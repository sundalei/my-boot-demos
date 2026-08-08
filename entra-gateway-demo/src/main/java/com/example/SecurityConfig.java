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
   * Paths that must stay reachable WITHOUT forcing an Entra login.
   *
   * <p>The backend SPA redirects unprovisioned users to its own {@code /login#<path>} page. If that
   * path required authentication here, the gateway would bounce the browser straight back into the
   * Entra authorization-code flow and the user would never see the login page (or would loop).
   */
  private static final String[] PUBLIC_PATHS = {
    "/login", "/login/**", "/assets/**", "/favicon.ico"
  };

  /**
   * Every request must be authenticated, except {@link #PUBLIC_PATHS}. Unauthenticated users are
   * redirected to Entra ID via the authorization-code flow (oauth2Login). Once logged in, the request
   * continues through the gateway filters to the backend.
   */
  @Bean
  SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    http.authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2Login(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
