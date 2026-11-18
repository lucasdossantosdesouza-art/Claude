package br.com.comissao.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolve o usuário autenticado a partir do JWT do Keycloak e o sincroniza na base local
 * (nome, e-mail e perfil sempre refletem o Keycloak, que é a fonte da verdade).
 */
@Service
@RequiredArgsConstructor
public class UsuarioAtualService {

    private final UsuarioRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Usuario atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Requisição sem token JWT");
        }
        Jwt jwt = token.getToken();
        Set<String> papeis = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        Perfil perfil = papeis.contains("ROLE_GESTOR") ? Perfil.GESTOR
                : papeis.contains("ROLE_PRODUCAO") ? Perfil.PRODUCAO : Perfil.VENDEDOR;

        Usuario u = repository.findByKeycloakId(jwt.getSubject()).orElseGet(Usuario::new);
        String login = jwt.getClaimAsString("preferred_username");
        String nome = jwt.getClaimAsString("name");
        u.setKeycloakId(jwt.getSubject());
        u.setLogin(login != null ? login : jwt.getSubject());
        u.setNome(nome != null && !nome.isBlank() ? nome : u.getLogin());
        u.setEmail(jwt.getClaimAsString("email"));
        u.setPerfil(perfil);
        u.setAtivo(true);
        LocalDateTime agora = LocalDateTime.now(clock);
        if (u.getUltimoAcesso() == null || u.getUltimoAcesso().isBefore(agora.minusMinutes(5)) || u.getId() == null) {
            u.setUltimoAcesso(agora);
        }
        return repository.save(u);
    }
}
