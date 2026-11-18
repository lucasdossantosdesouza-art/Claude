package br.com.comissao.comissao;

import java.math.BigDecimal;

/**
 * Resultado da apuração da faixa de comissão de um vendedor em um mês.
 *
 * @param travado true quando o mês já foi fechado (percentual definitivo)
 */
public record FaixaCalculada(
        String mes,
        BigDecimal totalVendido,
        BigDecimal faturamentoEmpresa,
        BigDecimal percentual,
        String regra,
        boolean travado,
        BigDecimal proximaFaixaValor,
        BigDecimal proximaFaixaPercentual,
        BigDecimal faltaProximaFaixa) {
}
