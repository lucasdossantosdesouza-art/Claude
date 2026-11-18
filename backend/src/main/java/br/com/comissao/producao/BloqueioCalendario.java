package br.com.comissao.producao;

import br.com.comissao.parametro.ProdutoProducao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Produto/tipo de produção sem capacidade em um dia (exibido em vermelho). */
@Entity
@Table(name = "bloqueio_calendario")
@Getter
@Setter
@NoArgsConstructor
public class BloqueioCalendario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate data;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "produto_id")
    private ProdutoProducao produto;
    private String observacao;
}
