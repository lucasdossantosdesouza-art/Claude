package br.com.comissao.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByKeycloakId(String keycloakId);

    List<Usuario> findByPerfilAndAtivoTrueOrderByNome(Perfil perfil);

    List<Usuario> findByPerfilOrderByNome(Perfil perfil);
}
