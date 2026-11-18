package br.com.comissao.comissao;

import br.com.comissao.os.OrdemServico;
import br.com.comissao.os.Parcela;
import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "comissao")
@Getter
@Setter
@NoArgsConstructor
public class Comissao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "os_id")
    private OrdemServico os;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcela_id", unique = true)
    private Parcela parcela;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    private String mesRef;
    private BigDecimal base;
    private BigDecimal percentual;
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private StatusComissao status;
    private LocalDateTime autorizadaEm;
    private LocalDateTime pagaEm;
}
