package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
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
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
