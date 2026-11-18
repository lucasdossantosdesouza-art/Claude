package br.com.comissao.producao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDate;
import java.util.List;

public interface BloqueioCalendarioRepository extends JpaRepository<BloqueioCalendario, Long> {

    List<BloqueioCalendario> findByData(LocalDate data);

    List<BloqueioCalendario> findByDataGreaterThanEqualAndDataLessThanOrderByData(LocalDate inicio, LocalDate fim);

    @Modifying
    void deleteByData(LocalDate data);
}
