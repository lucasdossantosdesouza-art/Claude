package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaixaMetaSemanalRepository extends JpaRepository<FaixaMetaSemanal, Long> {
    List<FaixaMetaSemanal> findAllByOrderByValorMinimo();
}
