package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoProducaoRepository extends JpaRepository<ProdutoProducao, Long> {
    List<ProdutoProducao> findAllByOrderByNome();

    List<ProdutoProducao> findByAtivoTrueOrderByNome();
}
