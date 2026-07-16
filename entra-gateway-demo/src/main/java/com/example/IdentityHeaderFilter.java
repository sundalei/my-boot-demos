package com.example;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Injects the authenticated identity into request headers before the request is proxied to the
 * backend.
 */
@Component
public class IdentityHeaderFilter implements GlobalFilter, Ordered {

  private static final Logger LOG = LoggerFactory.getLogger(IdentityHeaderFilter.class);

  public static final String SUB_HEADER = "X-Auth-Sub";
  public static final String OID_HEADER = "X-Auth-Oid";
  public static final String NAME_HEADER = "X-Auth-Name";
  public static final String EMAIL_HEADER = "X-Auth-Email";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    LOG.info("IdentityHeaderFilter invocation start");

    return exchange
        .getPrincipal()
        .doOnNext(
            principal ->
                LOG.info(
                    "1) principal: class={}, name={}",
                    principal.getClass().getName(),
                    principal.getName()))
        .filter(OAuth2AuthenticationToken.class::isInstance)
        .doOnNext(principal -> LOG.info("2) passed OAuth2AuthenticationToken filter"))
        .cast(OAuth2AuthenticationToken.class)
        .doOnNext(
            token ->
                LOG.info(
                    "3) clientRegistrationId={}, authorities={}, principal={}, principal class={}",
                    token.getAuthorizedClientRegistrationId(),
                    token.getAuthorities(),
                    token.getPrincipal(),
                    token.getPrincipal().getClass().getName()))
        .map(token -> (OidcUser) token.getPrincipal())
        .doOnNext(
            user ->
                LOG.info(
                    "4) oidc: sub={}, oid={}, email={}, preferredUsername={}",
                    user.getSubject(),
                    user.getClaim("oid"),
                    user.getEmail(),
                    user.getPreferredUsername()))
        .map(oidcUser -> mutateWithIdentity(exchange, oidcUser))
        .defaultIfEmpty(stripIdentity(exchange))
        .flatMap(chain::filter);
  }

  /** If somehow unauthenticated, never forged headers through */
  private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
    Consumer<HttpHeaders> headers =
        h -> {
          h.remove(SUB_HEADER);
          h.remove(OID_HEADER);
          h.remove(NAME_HEADER);
          h.remove(EMAIL_HEADER);
        };
    ServerHttpRequest request = exchange.getRequest().mutate().headers(headers).build();
    return exchange.mutate().request(request).build();
  }

  private ServerWebExchange mutateWithIdentity(ServerWebExchange exchange, OidcUser user) {
    LOG.info("user {}", user);
    String sub = user.getSubject();
    String oid = user.getClaimAsString("oid");
    String name = user.getFullName() != null ? user.getFullName() : user.getClaimAsString("name");
    LOG.info("user name:  {}", name);
    String email =
        firstNonNull(
            user.getEmail(),
            user.getClaimAsString("preferred_username"),
            user.getClaimAsString("email"));
    LOG.info("user email: {}", email);

    Consumer<HttpHeaders> headers =
        h -> {
          // strip any client-supplied values first
          h.remove(SUB_HEADER);
          h.remove(OID_HEADER);
          h.remove(NAME_HEADER);
          h.remove(EMAIL_HEADER);

          if (sub != null) {
            h.set(SUB_HEADER, sub);
          }

          if (oid != null) {
            h.set(OID_HEADER, oid);
          }

          if (name != null) {
            // encode name to avoid URL encoding issues
            h.set(NAME_HEADER, encode(name));
          }

          if (email != null) {
            h.set(EMAIL_HEADER, email);
          }
        };

    ServerHttpRequest request = exchange.getRequest().mutate().headers(headers).build();
    return exchange.mutate().request(request).build();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String firstNonNull(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
