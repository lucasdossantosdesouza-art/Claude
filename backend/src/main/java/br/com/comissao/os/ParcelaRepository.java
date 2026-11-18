package br.com.comissao.os;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ParcelaRepository extends JpaRepository<Parcela, Long> {

    /** Parcelas pendentes de OS não canceladas (contas a receber), com filtro opcional de período e vendedor. */
    @Query("""
            select p from Parcela p join fetch p.os o join fetch o.cliente join fetch o.vendedor v
            where p.status = br.com.comissao.os.StatusParcela.PENDENTE
              and o.situacao <> br.com.comissao.os.SituacaoOs.CANCELADA
              and p.vencimento between :inicio and :fim
              and (:vendedorId is null or v.id = :vendedorId)
            order by p.vencimento, p.id""")
    List<Parcela> pendentesNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                     @Param("vendedorId") Long vendedorId);

    LocalDate INICIO_ABERTO = LocalDate.of(1900, 1, 1);
    LocalDate FIM_ABERTO = LocalDate.of(9999, 12, 31);

    /**
     * Período opcional: datas nulas viram limites abertos (o PostgreSQL não infere o tipo de
     * parâmetro de data nulo em "? is null").
     */
    default List<Parcela> pendentes(LocalDate inicio, LocalDate fim, Long vendedorId) {
        return pendentesNoPeriodo(inicio == null ? INICIO_ABERTO : inicio, fim == null ? FIM_ABERTO : fim, vendedorId);
    }

    @Query("""
            select p from Parcela p join fetch p.os o join fetch o.cliente join fetch o.vendedor v
            where p.comprovante is not null
              and (lower(o.cliente.nome) like lower(concat('%', :q, '%')) or coalesce(o.codigo, '') like concat('%', :q, '%'))
            order by p.dataRecebimento desc""")
    List<Parcela> comComprovante(@Param("q") String q);

    @Query("""
            select p from Parcela p join fetch p.os o join fetch o.cliente c
            where (lower(c.nome) like lower(concat('%', :q, '%')) or lower(coalesce(c.razaoSocial, '')) like lower(concat('%', :q, '%')))
              and (:vendedorId is null or o.vendedor.id = :vendedorId)
            order by p.vencimento desc""")
    List<Parcela> doCliente(@Param("q") String q, @Param("vendedorId") Long vendedorId);

    @Query("""
            select p from Parcela p join fetch p.os o
            where o.cliente.id = :clienteId and (:vendedorId is null or o.vendedor.id = :vendedorId)
            order by p.vencimento desc""")
    List<Parcela> doClienteId(@Param("clienteId") Long clienteId, @Param("vendedorId") Long vendedorId);
}
