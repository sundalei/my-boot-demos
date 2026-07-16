package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
        .then(chain.filter(exchange));
  }

  private ServerWebExchange mutateWithIdentity(ServerWebExchange exchange, OidcUser user) {
    LOG.info("user {}", user);
    String sub = user.getSubject();
    String oid = user.getClaimAsString("oid");
    String name = user.getFullName() != null ? user.getFullName() : user.getClaimAsString("name");
    LOG.info("user name {}", name);
    return exchange;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
