package br.com.comissao.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioAtualService usuarioAtual;
    private final UsuarioRepository repository;

    public record UsuarioDto(Long id, String login, String nome, String email, Perfil perfil) {
        static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getLogin(), u.getNome(), u.getEmail(), u.getPerfil());
        }
    }

    @GetMapping("/me")
    public UsuarioDto me() {
        return UsuarioDto.de(usuarioAtual.atual());
    }

    /** Vendedores conhecidos (sincronizados do Keycloak no primeiro acesso). */
    @GetMapping("/vendedores")
    @PreAuthorize("hasAnyRole('GESTOR','PRODUCAO')")
    public List<UsuarioDto> vendedores() {
        return repository.findByPerfilAndAtivoTrueOrderByNome(Perfil.VENDEDOR).stream().map(UsuarioDto::de).toList();
    }
}
