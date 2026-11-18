package br.com.comissao.posvenda;

import br.com.comissao.os.OrdemServico;
import br.com.comissao.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Problema/reclamação de pós-venda vinculado à OS de origem. */
@Entity
@Table(name = "ocorrencia")
@Getter
@Setter
@NoArgsConstructor
public class Ocorrencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "os_id")
    private OrdemServico os;
    private String tipo;
    private String descricao;
    private String status = "ABERTA";
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    private LocalDateTime criadoEm;
    private LocalDateTime resolvidoEm;
}
