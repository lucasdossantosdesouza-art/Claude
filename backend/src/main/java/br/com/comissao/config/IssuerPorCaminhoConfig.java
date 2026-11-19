package br.com.comissao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;

/**
 * Para ambientes atrás de proxy em que o endereço público do Keycloak varia (ex.: preview, túneis):
 * aceita qualquer emissor cujo caminho seja o do realm configurado (ex.: {@code /realms/comissao}).
 * A assinatura continua sendo verificada pelas chaves (JWKS) do Keycloak; a validade (exp/nbf) também.
 * Desligado por padrão — em produção a API valida o emissor exato.
 */
@Configuration
@ConditionalOnProperty(name = "app.seguranca.issuer-por-caminho", havingValue = "true")
public class IssuerPorCaminhoConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        String caminho = URI.create(issuerUri).getPath();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>("iss", iss -> iss != null && URI.create(iss).getPath().equals(caminho))));
        return decoder;
    }
}
