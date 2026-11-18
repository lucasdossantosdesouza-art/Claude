package br.com.comissao.auditoria;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogAlteracaoRepository extends JpaRepository<LogAlteracao, Long> {

    List<LogAlteracao> findByOsIdOrderByIdDesc(Long osId);

    @Query("""
            select l from LogAlteracao l
            where lower(coalesce(l.detalhe, '')) like lower(concat('%', :q, '%'))
               or lower(l.acao) like lower(concat('%', :q, '%'))
            order by l.id desc""")
    List<LogAlteracao> buscar(@Param("q") String q, Pageable pageable);
}
