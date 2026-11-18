package br.com.comissao.comissao;

import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Trava da faixa (%) do vendedor no mês da venda. */
@Entity
@Table(name = "fechamento_mensal")
@Getter
@Setter
@NoArgsConstructor
public class FechamentoMensal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String mesRef;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;
    private BigDecimal totalVendido;
    private BigDecimal faturamentoEmpresa;
    private BigDecimal percentual;
    private String regra;
    private BigDecimal salarioFixo;
    private LocalDateTime fechadoEm;
}
