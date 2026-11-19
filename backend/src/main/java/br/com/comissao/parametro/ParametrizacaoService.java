package br.com.comissao.parametro;

import br.com.comissao.auditoria.AuditoriaService;
import br.com.comissao.comissao.ComissaoService;
import br.com.comissao.comissao.FechamentoMensalRepository;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.comissao.parametro.ValoresDocumento.VIGENCIA_INICIAL;

/**
 * Manutenção da parametrização com vigência.
 * <ul>
 *   <li>Toda alteração vale a partir de um mês (AAAA-MM) e não altera meses anteriores.</li>
 *   <li>Só é possível alterar a partir do mês seguinte ao último mês fechado (faixas já travadas).</li>
 *   <li>Alterações com vigência futura ficam agendadas e podem ser excluídas antes de algum fechamento.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ParametrizacaoService {

    private final ParametroRepository catalogo;
    private final ParametroVigenciaRepository vigencias;
    private final FaixaComissaoRepository faixasComissao;
    private final FaixaMetaSemanalRepository faixasMeta;
    private final ParametroService parametros;
    private final FechamentoMensalRepository fechamentos;
    private final ComissaoService comissaoService;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record Faixa(BigDecimal valorMinimo, BigDecimal valor) {
    }

    public enum TipoTabela { COMISSAO, META_SEMANAL }

    // ------------------------------------------------------------ regras de vigência

    public YearMonth primeiroMesEditavel() {
        String ultimo = fechamentos.ultimoMesFechado();
        return ultimo == null ? YearMonth.parse(VIGENCIA_INICIAL).plusMonths(1) : YearMonth.parse(ultimo).plusMonths(1);
    }

    private void validarVigencia(YearMonth vigenteDesde) {
        if (vigenteDesde == null) {
            throw new NegocioException("Informe o mês a partir do qual a alteração vale.");
        }
        YearMonth minimo = primeiroMesEditavel();
        if (vigenteDesde.isBefore(minimo)) {
            throw new NegocioException("O mês " + vigenteDesde + " já foi fechado (faixas travadas). Escolha "
                    + minimo + " ou um mês posterior.");
        }
    }

    // ------------------------------------------------------------ parâmetros gerais

    @Transactional
    public List<String> salvarGerais(YearMonth vigenteDesde, Map<ChaveParametro, BigDecimal> valores, Usuario usuario) {
        validarVigencia(vigenteDesde);
        List<String> alterados = new ArrayList<>();
        valores.forEach((chave, novo) -> {
            Parametro p = catalogo.findById(chave).orElseThrow(() -> new NaoEncontradoException("Parâmetro " + chave));
            validarValor(p, novo);
            BigDecimal atual = parametros.valor(chave, vigenteDesde);
            if (atual.compareTo(novo) == 0) {
                return;
            }
            ParametroVigencia v = vigencias.findByChaveAndVigenteDesde(chave, vigenteDesde.toString()).orElseGet(ParametroVigencia::new);
            v.setChave(chave);
            v.setVigenteDesde(vigenteDesde.toString());
            v.setValor(novo);
            v.setRegistradoEm(LocalDateTime.now(clock));
            v.setRegistradoPor(nome(usuario));
            vigencias.save(v);
            alterados.add(p.getDescricao() + ": " + texto(atual) + " → " + texto(novo));
        });
        if (!alterados.isEmpty()) {
            auditoria.registrar(null, usuario, "PARAMETROS", "A partir de " + vigenteDesde + ": " + String.join("; ", alterados));
            recalcular(vigenteDesde);
        }
        return alterados;
    }

    private static void validarValor(Parametro p, BigDecimal v) {
        if (v == null || v.signum() < 0) {
            throw new NegocioException("Valor inválido para \"" + p.getDescricao() + "\".");
        }
        switch (p.getTipo()) {
            case "PERCENTUAL" -> {
                if (v.compareTo(BigDecimal.valueOf(100)) > 0) throw new NegocioException("Percentual acima de 100%: " + p.getDescricao());
            }
            case "BOOLEANO" -> {
                if (v.compareTo(BigDecimal.ONE) > 0) throw new NegocioException("Use 0 (não) ou 1 (sim): " + p.getDescricao());
            }
            case "DIAS" -> {
                if (v.stripTrailingZeros().scale() > 0) throw new NegocioException("Informe um número inteiro de dias: " + p.getDescricao());
                if (p.getChave() == ChaveParametro.DIA_INICIO_SEMANA && (v.intValue() < 1 || v.intValue() > 7)) {
                    throw new NegocioException("Dia de início da semana deve ser de 1 (segunda) a 7 (domingo).");
                }
            }
            case "NUMERO" -> {
                if (p.getChave() == ChaveParametro.FATOR_MEIA_MARGEM && v.compareTo(BigDecimal.ONE) > 0) {
                    throw new NegocioException("O fator da Meia Margem deve estar entre 0 e 1 (0,5 = metade).");
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ faixas

    @Transactional
    public boolean salvarFaixas(TipoTabela tipo, YearMonth vigenteDesde, List<Faixa> faixas, Usuario usuario) {
        validarVigencia(vigenteDesde);
        validarFaixas(tipo, faixas);
        List<Faixa> ordenadas = faixas.stream().sorted(Comparator.comparing(Faixa::valorMinimo)).toList();
        if (mesmas(ordenadas, faixasVigentes(tipo, vigenteDesde))) {
            return false;
        }
        String mes = vigenteDesde.toString();
        if (tipo == TipoTabela.COMISSAO) {
            faixasComissao.excluirVigencia(mes);
            ordenadas.forEach(f -> faixasComissao.save(new FaixaComissao(f.valorMinimo(), f.valor(), mes, nome(usuario))));
        } else {
            faixasMeta.excluirVigencia(mes);
            ordenadas.forEach(f -> faixasMeta.save(new FaixaMetaSemanal(f.valorMinimo(), f.valor(), mes, nome(usuario))));
        }
        auditoria.registrar(null, usuario, "PARAMETROS", (tipo == TipoTabela.COMISSAO ? "Faixas de comissão" : "Patamares da meta semanal")
                + " a partir de " + mes + ": " + descrever(tipo, ordenadas));
        recalcular(vigenteDesde);
        return true;
    }

    public List<Faixa> faixasVigentes(TipoTabela tipo, YearMonth mes) {
        return tipo == TipoTabela.COMISSAO
                ? parametros.faixasComissao(mes).stream().map(f -> new Faixa(f.getValorMinimo(), f.getPercentual())).toList()
                : parametros.faixasMetaSemanal(mes).stream().map(f -> new Faixa(f.getValorMinimo(), f.getBonus())).toList();
    }

    private static void validarFaixas(TipoTabela tipo, List<Faixa> faixas) {
        if (faixas == null || faixas.isEmpty()) {
            throw new NegocioException("Informe ao menos uma faixa.");
        }
        for (Faixa f : faixas) {
            if (f.valorMinimo() == null || f.valorMinimo().signum() <= 0 || f.valor() == null || f.valor().signum() < 0) {
                throw new NegocioException("Faixa inválida: valores devem ser positivos.");
            }
            if (tipo == TipoTabela.COMISSAO && f.valor().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new NegocioException("Percentual de comissão acima de 100%.");
            }
        }
        if (faixas.stream().map(f -> f.valorMinimo().stripTrailingZeros()).distinct().count() != faixas.size()) {
            throw new NegocioException("Há faixas com o mesmo valor mínimo.");
        }
    }

    private static boolean mesmas(List<Faixa> a, List<Faixa> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).valorMinimo().compareTo(b.get(i).valorMinimo()) != 0 || a.get(i).valor().compareTo(b.get(i).valor()) != 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ valores do documento

    @Transactional
    public List<String> restaurarDocumento(YearMonth vigenteDesde, Usuario usuario) {
        Map<ChaveParametro, BigDecimal> valores = catalogo.findAll().stream().filter(p -> p.getValorDocumento() != null)
                .collect(Collectors.toMap(Parametro::getChave, Parametro::getValorDocumento));
        List<String> alterados = new ArrayList<>(salvarGerais(vigenteDesde, valores, usuario));
        if (salvarFaixas(TipoTabela.COMISSAO, vigenteDesde, doc(ValoresDocumento.FAIXAS_COMISSAO), usuario)) {
            alterados.add("Faixas de comissão restauradas");
        }
        if (salvarFaixas(TipoTabela.META_SEMANAL, vigenteDesde, doc(ValoresDocumento.FAIXAS_META_SEMANAL), usuario)) {
            alterados.add("Patamares da meta semanal restaurados");
        }
        return alterados;
    }

    private static List<Faixa> doc(List<ValoresDocumento.Faixa> l) {
        return l.stream().map(f -> new Faixa(f.valorMinimo(), f.valor())).toList();
    }

    // ------------------------------------------------------------ histórico / agenda

    public record AlteracaoVigencia(String mes, String situacao, List<String> alteracoes, String registradoPor,
                                    LocalDateTime registradoEm, boolean removivel) {
    }

    @Transactional(readOnly = true)
    public List<AlteracaoVigencia> linhaDoTempo() {
        YearMonth atual = YearMonth.now(clock);
        YearMonth minimo = primeiroMesEditavel();
        Map<ChaveParametro, Parametro> cat = catalogo.findAll().stream().collect(Collectors.toMap(Parametro::getChave, Function.identity()));
        Map<String, List<ParametroVigencia>> porMes = vigencias.findAllByOrderByVigenteDesdeDescChave().stream()
                .collect(Collectors.groupingBy(ParametroVigencia::getVigenteDesde));
        Map<String, List<FaixaComissao>> comPorMes = faixasComissao.findAllByOrderByVigenteDesdeDescValorMinimo().stream()
                .collect(Collectors.groupingBy(FaixaComissao::getVigenteDesde));
        Map<String, List<FaixaMetaSemanal>> metaPorMes = faixasMeta.findAllByOrderByVigenteDesdeDescValorMinimo().stream()
                .collect(Collectors.groupingBy(FaixaMetaSemanal::getVigenteDesde));
        TreeSet<String> meses = new TreeSet<>(Comparator.reverseOrder());
        meses.addAll(porMes.keySet());
        meses.addAll(comPorMes.keySet());
        meses.addAll(metaPorMes.keySet());

        // a versão vigente hoje é o maior mês <= atual
        String vigenteHoje = meses.stream().filter(m -> m.compareTo(atual.toString()) <= 0).findFirst().orElse(null);
        List<AlteracaoVigencia> lista = new ArrayList<>();
        for (String m : meses) {
            YearMonth ym = YearMonth.parse(m);
            List<String> itens = new ArrayList<>();
            String por = null;
            LocalDateTime em = null;
            for (ParametroVigencia v : porMes.getOrDefault(m, List.of())) {
                Parametro p = cat.get(v.getChave());
                String anterior = m.equals(VIGENCIA_INICIAL) ? null : texto(parametros.valor(v.getChave(), ym.minusMonths(1)));
                itens.add(p.getDescricao() + ": " + (anterior == null ? "" : anterior + " → ") + texto(v.getValor()));
                por = v.getRegistradoPor();
                if (v.getRegistradoEm() != null && (em == null || v.getRegistradoEm().isAfter(em))) {
                    em = v.getRegistradoEm();
                }
            }
            if (comPorMes.containsKey(m)) {
                itens.add("Faixas de comissão: " + descrever(TipoTabela.COMISSAO, comPorMes.get(m).stream()
                        .map(f -> new Faixa(f.getValorMinimo(), f.getPercentual())).toList()));
                por = por == null ? comPorMes.get(m).getFirst().getRegistradoPor() : por;
            }
            if (metaPorMes.containsKey(m)) {
                itens.add("Patamares da meta semanal: " + descrever(TipoTabela.META_SEMANAL, metaPorMes.get(m).stream()
                        .map(f -> new Faixa(f.getValorMinimo(), f.getBonus())).toList()));
                por = por == null ? metaPorMes.get(m).getFirst().getRegistradoPor() : por;
            }
            String situacao = m.compareTo(atual.toString()) > 0 ? "AGENDADA" : m.equals(vigenteHoje) ? "VIGENTE" : "HISTORICA";
            boolean removivel = !m.equals(VIGENCIA_INICIAL) && !ym.isBefore(minimo);
            lista.add(new AlteracaoVigencia(m, situacao, itens, por, em, removivel));
        }
        return lista;
    }

    @Transactional
    public void excluirVigencia(YearMonth mes, Usuario usuario) {
        if (mes.toString().equals(VIGENCIA_INICIAL)) {
            throw new NegocioException("A carga inicial não pode ser excluída.");
        }
        if (mes.isBefore(primeiroMesEditavel())) {
            throw new NegocioException("Essa vigência já foi usada em um mês fechado e não pode ser excluída.");
        }
        String m = mes.toString();
        vigencias.deleteByVigenteDesde(m);
        faixasComissao.excluirVigencia(m);
        faixasMeta.excluirVigencia(m);
        auditoria.registrar(null, usuario, "PARAMETROS", "Alterações com vigência a partir de " + m + " excluídas");
        recalcular(mes);
    }

    // ------------------------------------------------------------ utilitários

    /** Recalcula as comissões dos meses abertos afetados (da vigência até o mês atual). */
    private void recalcular(YearMonth desde) {
        YearMonth atual = YearMonth.now(clock);
        YearMonth inicio = desde.isBefore(primeiroMesEditavel()) ? primeiroMesEditavel() : desde;
        for (YearMonth m = inicio; !m.isAfter(atual); m = m.plusMonths(1)) {
            if (!fechamentos.existsByMesRef(m.toString())) {
                comissaoService.sincronizarMes(m);
            }
        }
    }

    private static String descrever(TipoTabela tipo, List<Faixa> faixas) {
        return faixas.stream().map(f -> "a partir de " + br.com.comissao.shared.Valores.formatarMoeda(f.valorMinimo()) + " → "
                        + (tipo == TipoTabela.COMISSAO ? br.com.comissao.shared.Valores.formatarPercentual(f.valor())
                        : br.com.comissao.shared.Valores.formatarMoeda(f.valor())))
                .collect(Collectors.joining("; "));
    }

    private static String texto(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String nome(Usuario u) {
        return u == null ? "sistema" : u.getNome();
    }
}
