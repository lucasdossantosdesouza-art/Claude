package br.com.comissao.parametro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Total vendido na semana a partir de {@code valorMinimo} → bônus fixo. */
@Entity
@Table(name = "faixa_meta_semanal")
@Getter
@Setter
@NoArgsConstructor
public class FaixaMetaSemanal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal valorMinimo;
    private BigDecimal bonus;
    /** Mês (AAAA-MM) a partir do qual este conjunto de faixas vale. */
    private String vigenteDesde;
    private String registradoPor;

    public FaixaMetaSemanal(BigDecimal valorMinimo, BigDecimal bonus, String vigenteDesde, String registradoPor) {
        this.valorMinimo = valorMinimo;
        this.bonus = bonus;
        this.vigenteDesde = vigenteDesde;
        this.registradoPor = registradoPor;
    }
}
