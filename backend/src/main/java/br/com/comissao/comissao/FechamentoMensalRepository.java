package br.com.comissao.comissao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FechamentoMensalRepository extends JpaRepository<FechamentoMensal, Long> {

    Optional<FechamentoMensal> findByMesRefAndVendedorId(String mesRef, Long vendedorId);

    boolean existsByMesRef(String mesRef);

    @Query("select f from FechamentoMensal f join fetch f.vendedor order by f.mesRef desc, f.vendedor.nome")
    List<FechamentoMensal> historico();
}
