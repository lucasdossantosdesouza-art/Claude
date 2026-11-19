package br.com.comissao.parametro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParametroVigenciaRepository extends JpaRepository<ParametroVigencia, Long> {

    /** Versão vigente no mês: a de maior "vigente desde" que não passa do mês consultado. */
    Optional<ParametroVigencia> findFirstByChaveAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(ChaveParametro chave, String mes);

    Optional<ParametroVigencia> findByChaveAndVigenteDesde(ChaveParametro chave, String vigenteDesde);

    List<ParametroVigencia> findAllByOrderByVigenteDesdeDescChave();

    List<ParametroVigencia> findByVigenteDesde(String vigenteDesde);

    void deleteByVigenteDesde(String vigenteDesde);
}
