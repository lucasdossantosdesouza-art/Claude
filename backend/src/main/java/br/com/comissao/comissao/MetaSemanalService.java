package br.com.comissao.comissao;

import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.FaixaMetaSemanal;
import br.com.comissao.parametro.ParametroService;
import br.com.comissao.shared.NegocioException;
import br.com.comissao.shared.NaoEncontradoException;
import br.com.comissao.usuario.Usuario;
import br.com.comissao.usuario.UsuarioRepository;
import br.com.comissao.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static br.com.comissao.shared.Valores.formatarMoeda;
import static br.com.comissao.shared.Valores.nz;

/** Meta da semana: bônus fixo por patamar de venda semanal, apurado por vendedor e pago à parte. */
@Service
@RequiredArgsConstructor
public class MetaSemanalService {

    private final OrdemServicoRepository ordens;
    private final ParametroService parametros;
    private final MetaSemanalPagaRepository pagas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public record MetaSemanal(LocalDate semanaInicio, LocalDate semanaFim, BigDecimal total, BigDecimal bonus,
                              BigDecimal proximoPatamar, BigDecimal proximoBonus, BigDecimal faltaProximo,
                              boolean paga, boolean encerrada) {
    }

    public LocalDate inicioDaSemana(LocalDate dia) {
        int n = parametros.inteiro(ChaveParametro.DIA_INICIO_SEMANA);
        DayOfWeek inicio = DayOfWeek.of(n < 1 || n > 7 ? 1 : n);
        return dia.with(TemporalAdjusters.previousOrSame(inicio));
    }

    @Transactional(readOnly = true)
    public MetaSemanal semana(Long vendedorId, LocalDate inicio) {
        LocalDate fim = inicio.plusDays(7);
        BigDecimal total = nz(ordens.totalVendido(vendedorId, inicio, fim));
        BigDecimal bonus = BigDecimal.ZERO;
        FaixaMetaSemanal proxima = null;
        for (FaixaMetaSemanal f : parametros.faixasMetaSemanal(YearMonth.from(inicio))) {
            if (total.compareTo(f.getValorMinimo()) >= 0) {
                bonus = f.getBonus();
            } else if (proxima == null) {
                proxima = f;
            }
        }
        return new MetaSemanal(inicio, fim.minusDays(1), total, bonus,
                proxima == null ? null : proxima.getValorMinimo(), proxima == null ? null : proxima.getBonus(),
                proxima == null ? BigDecimal.ZERO : proxima.getValorMinimo().subtract(total),
                pagas.existsByVendedorIdAndSemanaInicio(vendedorId, inicio),
                !fim.isAfter(LocalDate.now(clock)));
    }

    @Transactional(readOnly = true)
    public MetaSemanal semanaAtual(Long vendedorId) {
        return semana(vendedorId, inicioDaSemana(LocalDate.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<MetaSemanal> semanasDoMes(Long vendedorId, YearMonth mes) {
        List<MetaSemanal> lista = new ArrayList<>();
        LocalDate fimMes = mes.plusMonths(1).atDay(1);
        for (LocalDate d = inicioDaSemana(mes.atDay(1)); d.isBefore(fimMes); d = d.plusDays(7)) {
            lista.add(semana(vendedorId, d));
        }
        return lista;
    }

    @Transactional
    public MetaSemanal pagar(Long vendedorId, LocalDate semanaInicio, Usuario gestor) {
        Usuario vendedor = usuarios.findById(vendedorId).orElseThrow(() -> new NaoEncontradoException("Vendedor não encontrado"));
        MetaSemanal m = semana(vendedorId, semanaInicio);
        if (m.bonus().signum() <= 0) {
            throw new NegocioException("Meta não atingida nessa semana.");
        }
        if (!m.encerrada()) {
            throw new NegocioException("A semana ainda não terminou.");
        }
        if (!m.paga()) {
            MetaSemanalPaga p = new MetaSemanalPaga();
            p.setVendedor(vendedor);
            p.setSemanaInicio(semanaInicio);
            p.setTotal(m.total());
            p.setBonus(m.bonus());
            p.setPagaEm(LocalDateTime.now(clock));
            pagas.save(p);
            auditoria.registrar(null, gestor, "META_SEMANAL_PAGA",
                    vendedor.getNome() + " — semana " + semanaInicio + ": " + formatarMoeda(m.bonus()));
        }
        return semana(vendedorId, semanaInicio);
    }
}
