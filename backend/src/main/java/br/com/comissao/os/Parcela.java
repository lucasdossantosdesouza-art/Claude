package br.com.comissao.os;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cada parcela é um "recebimento pendente" da OS até o comprovante ser anexado. */
@Entity
@Table(name = "parcela")
@Getter
@Setter
@NoArgsConstructor
public class Parcela {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "os_id")
    private OrdemServico os;

    private int numero;
    private String forma;
    private BigDecimal valor;
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    private StatusParcela status = StatusParcela.PENDENTE;
    private LocalDate dataRecebimento;
    private String comprovante;

    public boolean isPendente() {
        return status == StatusParcela.PENDENTE;
    }

    public boolean isAtrasada(LocalDate hoje) {
        return isPendente() && vencimento.isBefore(hoje);
    }
}
