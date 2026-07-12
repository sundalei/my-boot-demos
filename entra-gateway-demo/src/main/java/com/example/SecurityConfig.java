package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  /**
   * Every request must be authenticated. Unauthenticated users are redirected to Entra ID via the
   * autorization-code flow (oauth2Login). Once logged in, the request continues through the gateway
   * filters to the backend.
   */
  @Bean
  SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    http.authorizeExchange(exchange -> exchange.anyExchange().authenticated())
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
