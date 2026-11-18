package br.com.comissao.os;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long>, JpaSpecificationExecutor<OrdemServico> {

    @Query("""
            select coalesce(sum(o.total), 0) from OrdemServico o
            where o.situacao <> br.com.comissao.os.SituacaoOs.CANCELADA
              and o.dataEmissao >= :inicio and o.dataEmissao < :fim
              and (:vendedorId is null or o.vendedor.id = :vendedorId)""")
    BigDecimal totalVendido(@Param("vendedorId") Long vendedorId, @Param("inicio") LocalDate inicio,
                            @Param("fim") LocalDate fim);

    @Query("select o from OrdemServico o where o.dataEmissao >= :inicio and o.dataEmissao < :fim")
    List<OrdemServico> doPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Pares (mês, vendedor) com vendas e sem fechamento, anteriores ao mês informado. */
    @Query(value = """
            select distinct to_char(o.data_emissao, 'YYYY-MM') as mes from ordem_servico o
            where o.data_emissao < :inicioMesAtual
              and not exists (select 1 from fechamento_mensal f
                              where f.mes_ref = to_char(o.data_emissao, 'YYYY-MM') and f.vendedor_id = o.vendedor_id)
            order by mes""", nativeQuery = true)
    List<String> mesesSemFechamento(@Param("inicioMesAtual") LocalDate inicioMesAtual);

    List<OrdemServico> findByClienteIdOrderByDataEmissaoDesc(Long clienteId);

    List<OrdemServico> findByClienteIdAndVendedorIdOrderByDataEmissaoDesc(Long clienteId, Long vendedorId);

    List<OrdemServico> findBySituacaoNotAndEntregueFalseOrderByPrevisaoEntrega(SituacaoOs situacao);

    List<OrdemServico> findBySituacaoNotAndEntregueFalseAndVendedorIdOrderByPrevisaoEntrega(SituacaoOs situacao, Long vendedorId);

    Page<OrdemServico> findBySituacaoOrderByIdDesc(SituacaoOs situacao, Pageable pageable);

    @Query("""
            select o from OrdemServico o join fetch o.cliente
            where o.documentoOrigem is not null and (lower(o.cliente.nome) like lower(concat('%', :q, '%')) or coalesce(o.codigo, '') like concat('%', :q, '%'))
            order by o.id desc""")
    List<OrdemServico> comDocumentoOrigem(@Param("q") String q, Pageable pageable);

    @Query("""
            select o from OrdemServico o join fetch o.cliente
            where o.estornoComprovante is not null and (lower(o.cliente.nome) like lower(concat('%', :q, '%')) or coalesce(o.codigo, '') like concat('%', :q, '%'))
            order by o.cancelData desc""")
    List<OrdemServico> comComprovanteEstorno(@Param("q") String q);

    @Query("""
            select count(o) from OrdemServico o where o.entregue = true and o.previsaoEntrega is not null
              and (:vendedorId is null or o.vendedor.id = :vendedorId)""")
    long totalEntregues(@Param("vendedorId") Long vendedorId);

    @Query("""
            select count(o) from OrdemServico o where o.entregue = true and o.previsaoEntrega is not null
              and o.dataEntregaReal <= o.previsaoEntrega and (:vendedorId is null or o.vendedor.id = :vendedorId)""")
    long entreguesNoPrazo(@Param("vendedorId") Long vendedorId);

    @Query("""
            select o.previsaoEntrega, count(o) from OrdemServico o
            where o.situacao <> br.com.comissao.os.SituacaoOs.CANCELADA and o.previsaoEntrega >= :inicio and o.previsaoEntrega < :fim
            group by o.previsaoEntrega""")
    List<Object[]> entregasPorDia(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
