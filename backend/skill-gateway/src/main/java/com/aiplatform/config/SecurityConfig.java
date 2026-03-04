package com.aiplatform.config;

import com.aiplatform.security.AkSkAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Security configuration for Gateway.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AkSkAuthFilter akSkAuthFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        // Health and metrics endpoints
                        .pathMatchers("/actuator/**").permitAll()
                        // WebSocket endpoint requires authentication
                        .pathMatchers("/ws/**").authenticated()
                        // API endpoints
                        .pathMatchers(HttpMethod.GET, "/v1/models").permitAll()
                        .pathMatchers("/v1/**").authenticated()
                        .anyExchange().permitAll()
                )
                .addFilterAt((exchange, chain) ->
                        akSkAuthFilter.authenticate(exchange)
                                .flatMap(auth -> chain.filter(exchange))
                                .switchIfEmpty(chain.filter(exchange)),
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .build();
    }
}