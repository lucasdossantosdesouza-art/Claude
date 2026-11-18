package br.com.comissao.parametro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Total vendido no mês a partir de {@code valorMinimo} → {@code percentual} de comissão. */
@Entity
@Table(name = "faixa_comissao")
@Getter
@Setter
@NoArgsConstructor
public class FaixaComissao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal valorMinimo;
    private BigDecimal percentual;

    public FaixaComissao(BigDecimal valorMinimo, BigDecimal percentual) {
        this.valorMinimo = valorMinimo;
        this.percentual = percentual;
    }
}
