package br.com.comissao.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_alteracao")
@Getter
@Setter
@NoArgsConstructor
public class LogAlteracao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long osId;
    private Long usuarioId;
    private String acao;
    private String detalhe;
    private LocalDateTime criadoEm;
}
