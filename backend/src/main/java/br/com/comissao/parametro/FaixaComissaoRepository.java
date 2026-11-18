package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaixaComissaoRepository extends JpaRepository<FaixaComissao, Long> {
    List<FaixaComissao> findAllByOrderByValorMinimo();
}
