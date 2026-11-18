package br.com.comissao.cliente;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findFirstByCnpj(String cnpj);

    Optional<Cliente> findFirstByNomeIgnoreCase(String nome);

    @Query("""
            select c from Cliente c
            where lower(c.nome) like lower(concat('%', :q, '%'))
               or lower(coalesce(c.razaoSocial, '')) like lower(concat('%', :q, '%'))
               or coalesce(c.cnpj, '') like concat('%', :q, '%')
            order by c.nome""")
    List<Cliente> buscar(@Param("q") String q, Pageable pageable);
}
