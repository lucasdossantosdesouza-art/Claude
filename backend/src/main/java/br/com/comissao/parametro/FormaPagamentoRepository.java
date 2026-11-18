package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormaPagamentoRepository extends JpaRepository<FormaPagamento, Long> {
    List<FormaPagamento> findAllByOrderByNome();

    List<FormaPagamento> findByAtivoTrueOrderByNome();
}
