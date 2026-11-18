package br.com.comissao.financeiro;

import br.com.comissao.comissao.*;
import br.com.comissao.comissao.ComissaoConsultaService.ComissaoDto;
import br.com.comissao.os.*;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.producao.AvisoRepository;
import br.com.comissao.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Contas a receber, inadimplência e painéis (financeiro e vendedor). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceiroService {

    private final ParcelaRepository parcelas;
    private final OrdemServicoRepository ordens;
    private final ComissaoConsultaService comissoes;
    private final ComissaoService comissaoService;
    private final RegraComissaoService regras;
    private final MetaSemanalService metas;
    private final ParametroService parametros;
    private final AvisoRepository avisos;
    private final Clock clock;

    public record ParcelaReceber(Long parcelaId, Long osId, String osCodigo, String cliente, Long vendedorId,
                                 String vendedor, int numero, String forma, LocalDate vencimento, BigDecimal valor,
                                 boolean atrasada, long diasAtraso) {
    }

    public record TotalPeriodo(String periodo, long parcelas, BigDecimal total) {
    }

    public List<ParcelaReceber> contasAReceber(LocalDate inicio, LocalDate fim, String cliente, Long vendedorId) {
        LocalDate hoje = LocalDate.now(clock);
        return parcelas.pendentes(inicio, fim, vendedorId).stream()
                .filter(p -> cliente == null || cliente.isBlank()
                        || p.getOs().getCliente().getNome().toLowerCase(Locale.ROOT).contains(cliente.toLowerCase(Locale.ROOT)))
                .map(p -> paraReceber(p, hoje)).toList();
    }

    private static ParcelaReceber paraReceber(Parcela p, LocalDate hoje) {
        OrdemServico o = p.getOs();
        long atraso = p.getVencimento().isBefore(hoje) ? ChronoUnit.DAYS.between(p.getVencimento(), hoje) : 0;
        return new ParcelaReceber(p.getId(), o.getId(), o.getCodigo(), o.getCliente().getNome(), o.getVendedor().getId(),
                o.getVendedor().getNome(), p.getNumero(), p.getForma(), p.getVencimento(), p.getValor(), atraso > 0, atraso);
    }

    /** Alerta de inadimplência: parcela vencida há mais de N dias sem comprovante. */
    public List<ParcelaReceber> inadimplencia(Long vendedorId) {
        LocalDate limite = LocalDate.now(clock).minusDays(parametros.inteiro(ChaveParametro.DIAS_TOLERANCIA_INADIMPLENCIA));
        return contasAReceber(null, limite.minusDays(1), null, vendedorId);
    }

    public record PainelFinanceiro(LocalDate data, BigDecimal totalDia, BigDecimal totalMes, BigDecimal totalGeral,
                                   List<TotalPeriodo> porMes, List<TotalPeriodo> porDia, List<ComissaoDto> comissoesAPagar,
                                   BigDecimal totalAutorizado, BigDecimal totalProvisorio, List<OsDtos.OsResumo> osFechadas,
                                   List<ParcelaReceber> inadimplencia, List<String> mesesPendentesFechamento,
                                   BigDecimal faturamentoMes, BigDecimal tetoEmpresa) {
    }

    public PainelFinanceiro painel(LocalDate data, OrdemServicoService osService) {
        List<ParcelaReceber> todas = contasAReceber(null, null, null, null);
        YearMonth mes = YearMonth.from(data);
        BigDecimal totalGeral = soma(todas);
        BigDecimal totalDia = soma(todas.stream().filter(p -> p.vencimento().equals(data)).toList());
        BigDecimal totalMes = soma(todas.stream().filter(p -> YearMonth.from(p.vencimento()).equals(mes)).toList());
        List<TotalPeriodo> porMes = agrupar(todas, p -> YearMonth.from(p.vencimento()).toString());
        List<TotalPeriodo> porDia = agrupar(todas.stream().filter(p -> YearMonth.from(p.vencimento()).equals(mes)).toList(),
                p -> p.vencimento().toString());
        List<ComissaoDto> aPagar = comissoes.aPagar();
        return new PainelFinanceiro(data, totalDia, totalMes, totalGeral, porMes, porDia, aPagar,
                somaComissao(aPagar, StatusComissao.AUTORIZADA), somaComissao(aPagar, StatusComissao.PROVISORIA),
                ordens.findBySituacaoOrderByIdDesc(SituacaoOs.FECHADA, PageRequest.of(0, 30)).stream()
                        .map(osService::resumo).toList(),
                inadimplencia(null),
                comissaoService.mesesPendentesDeFechamento().stream().map(YearMonth::toString).toList(),
                regras.faturamentoEmpresa(YearMonth.now(clock)), parametros.valor(ChaveParametro.TETO_FATURAMENTO_EMPRESA));
    }

    public record AvisoDto(Long id, String tipo, String texto, boolean urgente, String produto, LocalDate data,
                           Long osId, String osCodigo) {
    }

    public record PainelVendedor(String mes, Long vendedorId, String vendedor, FaixaCalculada faixa,
                                 MetaSemanalService.MetaSemanal metaSemana, BigDecimal salarioFixo,
                                 BigDecimal comissaoAutorizada, BigDecimal comissaoProvisoria, BigDecimal comissaoPendente,
                                 BigDecimal comissaoPaga, List<ParcelaReceber> atrasadas, List<ParcelaReceber> aVencer,
                                 List<AvisoDto> avisos) {
    }

    public PainelVendedor painelVendedor(Usuario vendedor) {
        YearMonth mes = YearMonth.now(clock);
        LocalDate hoje = LocalDate.now(clock);
        Map<StatusComissao, BigDecimal> tot = comissoes.totaisPorStatus(vendedor.getId(), null);
        int dias = parametros.inteiro(ChaveParametro.DIAS_ALERTA_VENCIMENTO);
        return new PainelVendedor(mes.toString(), vendedor.getId(), vendedor.getNome(),
                regras.faixaDoMes(vendedor.getId(), mes), metas.semanaAtual(vendedor.getId()),
                parametros.valor(ChaveParametro.SALARIO_FIXO), tot.get(StatusComissao.AUTORIZADA),
                tot.get(StatusComissao.PROVISORIA), tot.get(StatusComissao.PENDENTE_RECEBIMENTO), tot.get(StatusComissao.PAGA),
                inadimplencia(vendedor.getId()), contasAReceber(hoje, hoje.plusDays(dias), null, vendedor.getId()),
                avisos.visiveisParaVendedor(vendedor.getId()).stream().limit(20)
                        .map(a -> new AvisoDto(a.getId(), a.getTipo().name(), a.getTexto(), a.isUrgente(),
                                a.getProduto() == null ? null : a.getProduto().getNome(), a.getData(),
                                a.getOs() == null ? null : a.getOs().getId(), a.getOs() == null ? null : a.getOs().getCodigo()))
                        .toList());
    }

    private static BigDecimal soma(List<ParcelaReceber> l) {
        return l.stream().map(ParcelaReceber::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal somaComissao(List<ComissaoDto> l, StatusComissao s) {
        return l.stream().filter(c -> c.status() == s).map(ComissaoDto::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<TotalPeriodo> agrupar(List<ParcelaReceber> l, java.util.function.Function<ParcelaReceber, String> chave) {
        Map<String, List<ParcelaReceber>> g = l.stream().collect(Collectors.groupingBy(chave, TreeMap::new, Collectors.toList()));
        return g.entrySet().stream().map(e -> new TotalPeriodo(e.getKey(), e.getValue().size(), soma(e.getValue()))).toList();
    }
}
