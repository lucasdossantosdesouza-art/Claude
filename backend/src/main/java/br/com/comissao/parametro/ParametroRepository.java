package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParametroRepository extends JpaRepository<Parametro, ChaveParametro> {
    List<Parametro> findAllByOrderByOrdem();
}
