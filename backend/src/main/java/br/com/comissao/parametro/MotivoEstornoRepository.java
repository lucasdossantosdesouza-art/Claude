package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MotivoEstornoRepository extends JpaRepository<MotivoEstorno, Long> {
    List<MotivoEstorno> findAllByOrderByNome();

    List<MotivoEstorno> findByAtivoTrueOrderByNome();
}
