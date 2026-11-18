package br.com.comissao.producao;

import br.com.comissao.os.OrdemServico;
import br.com.comissao.parametro.ProdutoProducao;
import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "aviso")
@Getter
@Setter
@NoArgsConstructor
public class Aviso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TipoAviso tipo;
    private String texto;
    private boolean urgente;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_id")
    private OrdemServico os;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id")
    private ProdutoProducao produto;
    private LocalDate data;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Usuario autor;
    private boolean ativo = true;
    private LocalDateTime criadoEm;
}
