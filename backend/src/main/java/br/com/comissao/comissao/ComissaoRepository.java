package br.com.comissao.comissao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComissaoRepository extends JpaRepository<Comissao, Long> {

    Optional<Comissao> findByParcelaId(Long parcelaId);

    List<Comissao> findByOsId(Long osId);

    @Query("""
            select c from Comissao c join fetch c.os o join fetch o.cliente join fetch c.vendedor join fetch c.parcela p
            where c.mesRef = :mes and c.status <> br.com.comissao.comissao.StatusComissao.CANCELADA
            order by c.vendedor.nome, o.dataEmissao, p.numero""")
    List<Comissao> doMes(@Param("mes") String mes);

    @Query("""
            select c from Comissao c join fetch c.os o join fetch o.cliente join fetch c.vendedor join fetch c.parcela p
            where c.status in :status and c.valor > 0
            order by c.status, p.dataRecebimento desc""")
    List<Comissao> porStatus(@Param("status") List<StatusComissao> status);

    @Query("select c.status, coalesce(sum(c.valor), 0) from Comissao c where c.vendedor.id = :vendedorId and (:mes is null or c.mesRef = :mes) group by c.status")
    List<Object[]> totaisPorStatus(@Param("vendedorId") Long vendedorId, @Param("mes") String mes);

    @Query("""
            select c from Comissao c join fetch c.os o join fetch o.cliente cl join fetch c.vendedor
            where c.status = br.com.comissao.comissao.StatusComissao.PAGA
              and (lower(cl.nome) like lower(concat('%', :q, '%')) or lower(coalesce(cl.razaoSocial, '')) like lower(concat('%', :q, '%')))
              and (:vendedorId is null or c.vendedor.id = :vendedorId)
            order by c.pagaEm desc""")
    List<Comissao> pagasDoCliente(@Param("q") String q, @Param("vendedorId") Long vendedorId);
}
