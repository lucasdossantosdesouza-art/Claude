package br.com.comissao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Data fixa para os testes: 18/11/2026 (meses anteriores podem ser fechados). */
@TestConfiguration
public class RelogioTeste {
    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Bean
    @Primary
    public Clock relogioFixo() {
        return Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZONA);
    }
}
