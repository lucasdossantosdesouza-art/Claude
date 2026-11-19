package br.com.comissao.parametro;

import java.math.BigDecimal;
import java.util.List;

/**
 * Faixas definidas no documento de requisitos (item 3.1), usadas para "restaurar valores do documento".
 * Os valores dos parâmetros simples ficam na coluna {@code parametro.valor_documento}.
 */
public final class ValoresDocumento {

    public record Faixa(BigDecimal valorMinimo, BigDecimal valor) {
        static Faixa de(String minimo, String valor) {
            return new Faixa(new BigDecimal(minimo), new BigDecimal(valor));
        }
    }

    /** Atingiu R$ 58.000 → 1%; R$ 70.000 → 2%; R$ 80.000 → 3%. */
    public static final List<Faixa> FAIXAS_COMISSAO = List.of(
            Faixa.de("58000", "1"), Faixa.de("70000", "2"), Faixa.de("80000", "3"));

    /** A partir de R$ 20.133 → R$ 100; R$ 23.810 → R$ 150; R$ 26.690 → R$ 200. */
    public static final List<Faixa> FAIXAS_META_SEMANAL = List.of(
            Faixa.de("20133", "100"), Faixa.de("23810", "150"), Faixa.de("26690", "200"));

    /** Vigência da carga inicial (vale para todo o passado). */
    public static final String VIGENCIA_INICIAL = "2000-01";

    private ValoresDocumento() {
    }
}
