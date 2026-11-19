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
    /** Mês (AAAA-MM) a partir do qual este conjunto de faixas vale. */
    private String vigenteDesde;
    private String registradoPor;

    public FaixaComissao(BigDecimal valorMinimo, BigDecimal percentual, String vigenteDesde, String registradoPor) {
        this.valorMinimo = valorMinimo;
        this.percentual = percentual;
        this.vigenteDesde = vigenteDesde;
        this.registradoPor = registradoPor;
    }
}
