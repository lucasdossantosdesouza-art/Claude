package br.com.comissao.comissao;

import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.usuario.Perfil;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Visões de leitura das comissões (tela, painel e relatórios). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComissaoConsultaService {

    private final ComissaoRepository comissoes;
    private final FechamentoMensalRepository fechamentos;
    private final UsuarioRepository usuarios;
    private final RegraComissaoService regras;
    private final MetaSemanalService metas;
    private final ParametroService parametros;

    public record ResumoVendedor(Long vendedorId, String vendedor, BigDecimal totalVendido, BigDecimal percentual,
                                 boolean travado, String regra, BigDecimal salarioFixo, BigDecimal autorizada,
                                 BigDecimal provisoria, BigDecimal paga, BigDecimal pendente, BigDecimal bonusSemanal) {
    }

    public record ComissaoDto(Long id, Long osId, String osCodigo, String cliente, Long vendedorId, String vendedor,
                              int parcela, String forma, LocalDate vencimento, LocalDate dataRecebimento,
                              String comprovante, boolean meiaMargem, BigDecimal base, BigDecimal percentual,
                              BigDecimal valor, StatusComissao status, LocalDateTime pagaEm) {
        static ComissaoDto de(Comissao c) {
            return new ComissaoDto(c.getId(), c.getOs().getId(), c.getOs().getCodigo(), c.getOs().getCliente().getNome(),
                    c.getVendedor().getId(), c.getVendedor().getNome(), c.getParcela().getNumero(), c.getParcela().getForma(),
                    c.getParcela().getVencimento(), c.getParcela().getDataRecebimento(), c.getParcela().getComprovante(),
                    c.getOs().isMeiaMargem(), c.getBase(), c.getPercentual(), c.getValor(), c.getStatus(), c.getPagaEm());
        }
    }

    public record ComissoesDoMes(String mes, boolean fechado, List<ResumoVendedor> resumo, List<ComissaoDto> detalhe) {
    }

    public ComissoesDoMes doMes(YearMonth mes, Usuario usuario, StatusComissao status, Long vendedorId) {
        Long filtroVendedor = usuario.isVendedor() ? usuario.getId() : vendedorId;
        List<ResumoVendedor> resumo = usuarios.findByPerfilOrderByNome(Perfil.VENDEDOR).stream()
                .filter(v -> filtroVendedor == null || v.getId().equals(filtroVendedor))
                .map(v -> resumoVendedor(v, mes)).toList();
        List<ComissaoDto> detalhe = comissoes.doMes(mes.toString()).stream()
                .filter(c -> filtroVendedor == null || c.getVendedor().getId().equals(filtroVendedor))
                .filter(c -> status == null || c.getStatus() == status)
                .map(ComissaoDto::de).toList();
        return new ComissoesDoMes(mes.toString(), fechamentos.existsByMesRef(mes.toString()), resumo, detalhe);
    }

    public ResumoVendedor resumoVendedor(Usuario v, YearMonth mes) {
        FaixaCalculada f = regras.faixaDoMes(v.getId(), mes);
        Map<StatusComissao, BigDecimal> tot = totaisPorStatus(v.getId(), mes);
        BigDecimal bonus = metas.semanasDoMes(v.getId(), mes).stream().map(MetaSemanalService.MetaSemanal::bonus)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumoVendedor(v.getId(), v.getNome(), f.totalVendido(), f.percentual(), f.travado(), f.regra(),
                parametros.valor(ChaveParametro.SALARIO_FIXO, mes), tot.get(StatusComissao.AUTORIZADA),
                tot.get(StatusComissao.PROVISORIA), tot.get(StatusComissao.PAGA),
                tot.get(StatusComissao.PENDENTE_RECEBIMENTO), bonus);
    }

    /** Totais por status; {@code mes} nulo = todos os meses. */
    public Map<StatusComissao, BigDecimal> totaisPorStatus(Long vendedorId, YearMonth mes) {
        Map<StatusComissao, BigDecimal> m = new EnumMap<>(StatusComissao.class);
        for (StatusComissao s : StatusComissao.values()) {
            m.put(s, BigDecimal.ZERO);
        }
        for (Object[] r : comissoes.totaisPorStatus(vendedorId, mes == null ? null : mes.toString())) {
            m.put((StatusComissao) r[0], (BigDecimal) r[1]);
        }
        return m;
    }

    public List<ComissaoDto> aPagar() {
        return comissoes.porStatus(List.of(StatusComissao.AUTORIZADA, StatusComissao.PROVISORIA)).stream()
                .map(ComissaoDto::de).toList();
    }
}
