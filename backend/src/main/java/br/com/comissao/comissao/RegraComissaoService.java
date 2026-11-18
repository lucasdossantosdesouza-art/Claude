package br.com.comissao.comissao;

import br.com.comissao.os.OrdemServico;
import br.com.comissao.os.OrdemServicoRepository;
import br.com.comissao.parametro.ChaveParametro;
import br.com.comissao.parametro.FaixaComissao;
import br.com.comissao.parametro.ParametroService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import static br.com.comissao.shared.Valores.formatarMoeda;
import static br.com.comissao.shared.Valores.formatarPercentual;
import static br.com.comissao.shared.Valores.nz;

/**
 * Regras de apuração da faixa (%) de comissão mensal.
 * <ul>
 *   <li>Mês de referência = mês da venda (data de emissão da OS).</li>
 *   <li>Faixa = maior faixa cujo valor mínimo foi atingido pelo total vendido no mês.</li>
 *   <li>Sem faixa atingida: se o faturamento da EMPRESA no mês passou do teto, paga {@code PERCENTUAL_POS_TETO};
 *       caso contrário, {@code PERCENTUAL_ABAIXO_META}.</li>
 *   <li>Após o fechamento do mês a faixa fica travada.</li>
 *   <li>OS "Meia Margem": percentual da faixa × {@code FATOR_MEIA_MARGEM}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegraComissaoService {

    private final OrdemServicoRepository ordens;
    private final FechamentoMensalRepository fechamentos;
    private final ParametroService parametros;

    public BigDecimal totalVendido(Long vendedorId, YearMonth mes) {
        return nz(ordens.totalVendido(vendedorId, mes.atDay(1), mes.plusMonths(1).atDay(1)));
    }

    public BigDecimal faturamentoEmpresa(YearMonth mes) {
        return nz(ordens.totalVendido(null, mes.atDay(1), mes.plusMonths(1).atDay(1)));
    }

    /** Faixa calculada com os dados atuais, ignorando trava. */
    public FaixaCalculada calcularFaixaAberta(Long vendedorId, YearMonth mes) {
        BigDecimal total = totalVendido(vendedorId, mes);
        BigDecimal faturamento = faturamentoEmpresa(mes);
        List<FaixaComissao> faixas = parametros.faixasComissao();

        FaixaComissao atingida = null;
        FaixaComissao proxima = null;
        for (FaixaComissao f : faixas) {
            if (total.compareTo(f.getValorMinimo()) >= 0) {
                atingida = f;
            } else if (proxima == null) {
                proxima = f;
            }
        }
        BigDecimal teto = parametros.valor(ChaveParametro.TETO_FATURAMENTO_EMPRESA);
        BigDecimal percentual;
        String regra;
        if (atingida != null) {
            percentual = atingida.getPercentual();
            regra = "Faixa atingida: a partir de " + formatarMoeda(atingida.getValorMinimo()) + " → " + formatarPercentual(percentual);
        } else if (faturamento.compareTo(teto) > 0) {
            percentual = parametros.valor(ChaveParametro.PERCENTUAL_POS_TETO);
            regra = "Meta mínima não atingida e faturamento da empresa (" + formatarMoeda(faturamento)
                    + ") acima do teto (" + formatarMoeda(teto) + ") → " + formatarPercentual(percentual);
        } else {
            percentual = parametros.valor(ChaveParametro.PERCENTUAL_ABAIXO_META);
            regra = "Meta mínima não atingida → " + formatarPercentual(percentual);
        }
        return new FaixaCalculada(mes.toString(), total, faturamento, percentual, regra, false,
                proxima == null ? null : proxima.getValorMinimo(),
                proxima == null ? null : proxima.getPercentual(),
                proxima == null ? BigDecimal.ZERO : proxima.getValorMinimo().subtract(total));
    }

    /** Faixa travada (mês fechado) ou provisória. */
    public FaixaCalculada faixaDoMes(Long vendedorId, YearMonth mes) {
        return fechamentos.findByMesRefAndVendedorId(mes.toString(), vendedorId)
                .map(f -> new FaixaCalculada(f.getMesRef(), f.getTotalVendido(), f.getFaturamentoEmpresa(),
                        f.getPercentual(), f.getRegra(), true, null, null, BigDecimal.ZERO))
                .orElseGet(() -> calcularFaixaAberta(vendedorId, mes));
    }

    public record PercentualOs(BigDecimal percentual, boolean travado, FaixaCalculada faixa) {
    }

    public PercentualOs percentualDaOs(OrdemServico os) {
        FaixaCalculada faixa = faixaDoMes(os.getVendedor().getId(), YearMonth.from(os.getDataEmissao()));
        BigDecimal pct = faixa.percentual();
        if (os.isMeiaMargem()) {
            pct = pct.multiply(parametros.valor(ChaveParametro.FATOR_MEIA_MARGEM));
        }
        return new PercentualOs(pct.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros(), faixa.travado(), faixa);
    }
}
