package br.com.comissao.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Espelho local do usuário do Keycloak (sincronizado a cada acesso). */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @Column(nullable = false)
    private String login;

    @Column(nullable = false)
    private String nome;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    private boolean ativo = true;

    private LocalDateTime ultimoAcesso;

    public boolean isVendedor() {
        return perfil == Perfil.VENDEDOR;
    }

    public boolean isGestor() {
        return perfil == Perfil.GESTOR;
    }
}
