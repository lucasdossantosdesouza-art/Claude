package br.com.comissao.comissao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MetaSemanalPagaRepository extends JpaRepository<MetaSemanalPaga, Long> {
    boolean existsByVendedorIdAndSemanaInicio(Long vendedorId, LocalDate semanaInicio);
}
