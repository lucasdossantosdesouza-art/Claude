package br.com.comissao.comissao;

import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meta_semanal_paga")
@Getter
@Setter
@NoArgsConstructor
public class MetaSemanalPaga {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;
    private LocalDate semanaInicio;
    private BigDecimal total;
    private BigDecimal bonus;
    private LocalDateTime pagaEm;
}
