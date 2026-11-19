package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoOcorrenciaRepository extends JpaRepository<TipoOcorrencia, Long> {
    List<TipoOcorrencia> findAllByOrderByNome();
}
