package br.com.comissao.os;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "item_os")
@Getter
@Setter
@NoArgsConstructor
public class ItemOs {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "os_id")
    private OrdemServico os;

    @Enumerated(EnumType.STRING)
    private TipoItem tipo = TipoItem.PRODUTO;
    private String codigo;
    private String descricao;
    private String unidade;
    private BigDecimal quantidade;
    private BigDecimal valorUnitario;
    private BigDecimal desconto;
    private BigDecimal valorTotal;
}
