package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaixaMetaSemanalRepository extends JpaRepository<FaixaMetaSemanal, Long> {

    /** Mês de início do conjunto de faixas vigente no mês consultado. */
    @Query("select max(f.vigenteDesde) from FaixaMetaSemanal f where f.vigenteDesde <= :mes")
    String vigenciaEm(@Param("mes") String mes);

    List<FaixaMetaSemanal> findByVigenteDesdeOrderByValorMinimo(String vigenteDesde);

    List<FaixaMetaSemanal> findAllByOrderByVigenteDesdeDescValorMinimo();

    @Modifying
    @Query("delete from FaixaMetaSemanal f where f.vigenteDesde = :vigenteDesde")
    void excluirVigencia(@Param("vigenteDesde") String vigenteDesde);
}
