package br.com.comissao.producao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {

    List<Aviso> findTop200ByOrderByAtivoDescIdDesc();

    List<Aviso> findByAtivoTrueAndUrgenteTrueOrderByCriadoEmDesc();

    List<Aviso> findByAtivoTrueAndOsId(Long osId);

    List<Aviso> findByAtivoTrueAndData(LocalDate data);

    List<Aviso> findByAtivoTrueAndDataGreaterThanEqualAndDataLessThan(LocalDate inicio, LocalDate fim);

    /** Avisos visíveis ao vendedor: gerais, por data/produto e os vinculados às OS dele. */
    @Query("""
            select a from Aviso a left join a.os o
            where a.ativo = true and (o is null or o.vendedor.id = :vendedorId)
            order by a.urgente desc, a.criadoEm desc""")
    List<Aviso> visiveisParaVendedor(@Param("vendedorId") Long vendedorId);
}
