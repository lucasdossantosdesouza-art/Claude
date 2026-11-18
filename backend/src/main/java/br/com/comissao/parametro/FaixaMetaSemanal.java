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

    public FaixaMetaSemanal(BigDecimal valorMinimo, BigDecimal bonus) {
        this.valorMinimo = valorMinimo;
        this.bonus = bonus;
    }
}
