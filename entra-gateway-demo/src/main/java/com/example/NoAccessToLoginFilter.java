package com.example;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Turns the backend's "no app access" signal into a 401 so the SPA redirects to its own login page.
 *
 * <p>Context: the gateway authenticates every valid Entra user (authN), but app access lives in the
 * app's own store (authZ) — the gateway deliberately does NOT check it. A user who authenticates
 * with Entra but has no app access reaches the app, which returns {@code 400} on
 * {@code GET /common/customer}. The Ripjar SPA does not handle that 400 and crashes into its error
 * boundary ("Something went wrong").
 *
 * <p>Authorized users get {@code 200} on the same endpoint. So the status alone is the signal:
 * rewrite {@code 400 -> 401} for this path, and the SPA's existing 401 handler redirects to
 * {@code /login#<original-path>} — reproducing what Maverics did, with no user lookup in the gateway.
 */
@Component
public class NoAccessToLoginFilter implements GlobalFilter, Ordered {

  private static final Logger LOG = LoggerFactory.getLogger(NoAccessToLoginFilter.class);

  /** Toggle without a rebuild: {@code idd.gateway.no-access.enabled=false} to disable. */
  @Value("${idd.gateway.no-access.enabled:true}")
  private boolean enabled;

  /** The endpoint whose 400 means "authenticated but not authorized for this app". */
  @Value("${idd.gateway.no-access.path:/common/customer}")
  private String targetPath;

  /** Status the backend returns for an unprovisioned user. */
  @Value("${idd.gateway.no-access.from-status:400}")
  private int fromStatus;

  /** Status to present instead, so the SPA runs its own "not authenticated" handling. */
  @Value("${idd.gateway.no-access.to-status:401}")
  private int toStatus;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled || !targetPath.equals(exchange.getRequest().getPath().value())) {
      return chain.filter(exchange);
    }

    ServerHttpResponse original = exchange.getResponse();
    ServerHttpResponseDecorator decorated =
        new ServerHttpResponseDecorator(original) {
          @Override
          public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            if (getStatusCode() != null && getStatusCode().value() == fromStatus) {
              // INFO on purpose: this is the signal you are watching for during the test.
              LOG.info("Rewriting {} {} -> {} so the SPA can redirect to login",
                  targetPath, fromStatus, toStatus);
              setStatusCode(HttpStatus.valueOf(toStatus));
            }
            return super.writeWith(body);
          }
        };

    return chain.filter(exchange.mutate().response(decorated).build());
  }

  /**
   * Must run BEFORE {@code NettyWriteResponseFilter} (order {@code -1}) so this response decorator is
   * installed before the response is written back to the client. Lower order = runs earlier, so
   * {@code -2} sits just ahead of the write filter. (Contrast with {@link IdentityHeaderFilter},
   * which acts on the REQUEST and therefore runs as LATE as possible — {@code LOWEST_PRECEDENCE - 1}
   * — to be the last thing to touch the request before it is proxied upstream.)
   */
  @Override
  public int getOrder() {
    return -2;
  }
}
