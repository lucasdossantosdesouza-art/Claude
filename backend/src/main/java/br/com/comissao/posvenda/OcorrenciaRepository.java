package br.com.comissao.posvenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    List<Ocorrencia> findByOsIdOrderByIdDesc(Long osId);

    @Query("""
            select oc from Ocorrencia oc join fetch oc.os o join fetch o.cliente join fetch o.vendedor
            where (:status is null or oc.status = :status) and (:vendedorId is null or o.vendedor.id = :vendedorId)
            order by oc.id desc""")
    List<Ocorrencia> listar(@Param("status") String status, @Param("vendedorId") Long vendedorId);

    @Query("""
            select oc from Ocorrencia oc join fetch oc.os o
            where o.cliente.id = :clienteId and (:vendedorId is null or o.vendedor.id = :vendedorId)
            order by oc.id desc""")
    List<Ocorrencia> doCliente(@Param("clienteId") Long clienteId, @Param("vendedorId") Long vendedorId);
}
