package br.com.comissao.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Utilitários de valores monetários e datas. */
public final class Valores {

    public static final Locale PT_BR = Locale.of("pt", "BR");
    public static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private Valores() {
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal moeda(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** valor × percentual / 100, arredondado em centavos. */
    public static BigDecimal aplicarPercentual(BigDecimal valor, BigDecimal percentual) {
        return moeda(nz(valor).multiply(nz(percentual)).divide(CEM, 6, RoundingMode.HALF_UP));
    }

    public static String formatarMoeda(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(moeda(v));
    }

    public static String formatarData(LocalDate d) {
        return d == null ? "" : d.format(DATA_BR);
    }

    public static String formatarPercentual(BigDecimal p) {
        return nz(p).stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    public static String mesRef(LocalDate d) {
        return YearMonth.from(d).toString();
    }
}
