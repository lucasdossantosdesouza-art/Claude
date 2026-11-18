package br.com.comissao.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AplicacaoConfig {

    /** Relógio injetável: facilita testes das regras que dependem da data atual. */
    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zona() == null ? "America/Sao_Paulo" : props.zona()));
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("API Comissão & Contas a Receber").version("1.0.0")
                        .description("Ordens de Serviço, contas a receber, pós-venda e comissão de vendedores"))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
