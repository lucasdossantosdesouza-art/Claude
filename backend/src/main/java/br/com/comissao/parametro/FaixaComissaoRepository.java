package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaixaComissaoRepository extends JpaRepository<FaixaComissao, Long> {

    /** Mês de início do conjunto de faixas vigente no mês consultado. */
    @Query("select max(f.vigenteDesde) from FaixaComissao f where f.vigenteDesde <= :mes")
    String vigenciaEm(@Param("mes") String mes);

    List<FaixaComissao> findByVigenteDesdeOrderByValorMinimo(String vigenteDesde);

    List<FaixaComissao> findAllByOrderByVigenteDesdeDescValorMinimo();

    @Modifying
    @Query("delete from FaixaComissao f where f.vigenteDesde = :vigenteDesde")
    void excluirVigencia(@Param("vigenteDesde") String vigenteDesde);
}
