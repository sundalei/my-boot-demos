package com.example;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Injects the authenticated identity into request headers before the request is proxied to the
 * backend.
 *
 * <p>This reproduces the header contract previously emitted by the Strata Maverics orchestrator (the
 * {@code seISSOHeader_Agnostic_V1} service extension), so the downstream {@code torchservices}
 * backends need no change when the gateway is swapped from Maverics to Spring Cloud Gateway. The
 * identity source is the Entra ID id_token claims (same IdP Maverics already federated to).
 *
 * <p>Header names use the canonical casing that appeared on the wire in the Maverics debug logs.
 * Values are taken verbatim from the id_token claims, except UID/remote-user values which are
 * lower-cased (matching the extension's {@code attrValueValLC} handling).
 */
@Component
public class IdentityHeaderFilter implements GlobalFilter, Ordered {

  private static final Logger LOG = LoggerFactory.getLogger(IdentityHeaderFilter.class);

  // ---- Header names (canonical casing, as Maverics emitted them) ----
  public static final String REMOTE_USER_HEADER = "Remote-User";
  public static final String X_REMOTE_USER_HEADER = "X-Remote-User";
  public static final String SMUNIVERSALID_HEADER = "Smuniversalid";
  public static final String UIDS_HEADER = "X-Auth-Uids";
  public static final String AUTH_TIME_HEADER = "X-Auth-Time";
  public static final String AUTH_STRENGTH_HEADER = "X-Auth-Strength";
  public static final String LOCATION_HEADER = "X-Auth-Location";
  public static final String CHANNEL_HEADER = "X-Auth-Channel";
  public static final String MANDATOR_HEADER = "X-Auth-Mandator";

  // ---- Claim names ----
  private static final String CLAIM_TNUMBER = "ubs_auth_t_number";
  private static final String CLAIM_LOCATION = "ubs_auth_location";
  private static final String CLAIM_CHANNEL = "ubs_auth_channel";
  private static final String CLAIM_ACRS = "acrs";

  /**
   * Composite {@code X-Auth-Uids} members, in the fixed order Maverics used. Key = the label written
   * into the header (e.g. {@code GPN=...}); value = the id_token claim it reads. Only members whose
   * claim is present are appended; each pair is followed by a {@code ;}.
   */
  private static final Map<String, String> UID_MEMBERS = new LinkedHashMap<>();

  static {
    UID_MEMBERS.put("GPN", "ubs_auth_gpn");
    UID_MEMBERS.put("T_NUMBER", "ubs_auth_t_number");
    UID_MEMBERS.put("ABACUS_ID", "ubs_auth_abacus_id");
    UID_MEMBERS.put("WEBSSO_ID", "ubs_auth_websso_id");
    UID_MEMBERS.put("UUNAME", "ubs_auth_uuname");
  }

  /**
   * Static per-application "mandator" value. In Maverics this came from app metadata
   * ({@code HEADER.X-AUTH-MANDATOR}). For a multi-app gateway, make this per-route configuration
   * rather than a constant.
   */
  private static final String MANDATOR_VALUE = "898";

  /** Clock skew (seconds) added to {@code iat}, matching the extension's {@code AZCLOCKSKEW}. */
  private static final long AUTH_TIME_SKEW_SECONDS = 300;

  /** RFC1123 / HTTP-date in UTC, e.g. "Mon, 02 Jan 2006 15:04:05 GMT". */
  private static final DateTimeFormatter RFC1123_GMT =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private static final String[] MANAGED_HEADERS = {
    REMOTE_USER_HEADER,
    X_REMOTE_USER_HEADER,
    SMUNIVERSALID_HEADER,
    UIDS_HEADER,
    AUTH_TIME_HEADER,
    AUTH_STRENGTH_HEADER,
    LOCATION_HEADER,
    CHANNEL_HEADER,
    MANDATOR_HEADER
  };

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange
        .getPrincipal()
        .filter(OAuth2AuthenticationToken.class::isInstance)
        .cast(OAuth2AuthenticationToken.class)
        .map(token -> (OidcUser) token.getPrincipal())
        .flatMap(
            user -> {
              // Authorization gate: Maverics policy required ubsAuthStrength == "S".
              String strength = computeStrength(user);
              if (!"S".equals(strength)) {
                LOG.debug("Denying request: auth strength {} is not S", strength);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
              }
              return chain.filter(mutateWithIdentity(exchange, user, strength));
            })
        // Defensive: SecurityConfig already forces authentication, so this rarely fires.
        .switchIfEmpty(Mono.defer(() -> chain.filter(stripIdentity(exchange))));
  }

  private ServerWebExchange mutateWithIdentity(
      ServerWebExchange exchange, OidcUser user, String strength) {
    String tnumber = lower(user.getClaimAsString(CLAIM_TNUMBER));
    String uids = buildUids(user);
    String authTime = buildAuthTime(user);
    String location = user.getClaimAsString(CLAIM_LOCATION);
    String channel = user.getClaimAsString(CLAIM_CHANNEL);

    Consumer<HttpHeaders> headers =
        h -> {
          // Strip any client-supplied values first so identity can never be forged.
          removeManagedHeaders(h);

          setIfPresent(h, REMOTE_USER_HEADER, tnumber);
          setIfPresent(h, X_REMOTE_USER_HEADER, tnumber);
          setIfPresent(h, SMUNIVERSALID_HEADER, tnumber);
          setIfPresent(h, UIDS_HEADER, uids);
          setIfPresent(h, AUTH_TIME_HEADER, authTime);
          h.set(AUTH_STRENGTH_HEADER, strength);
          setIfPresent(h, LOCATION_HEADER, location);
          setIfPresent(h, CHANNEL_HEADER, channel);
          h.set(MANDATOR_HEADER, MANDATOR_VALUE);
        };

    ServerHttpRequest request = exchange.getRequest().mutate().headers(headers).build();
    return exchange.mutate().request(request).build();
  }

  /** Builds {@code GPN=..;T_NUMBER=..;...} from present claims, lower-cased, in fixed order. */
  private static String buildUids(OidcUser user) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> member : UID_MEMBERS.entrySet()) {
      String value = user.getClaimAsString(member.getValue());
      if (value != null && !value.isBlank()) {
        sb.append(member.getKey()).append('=').append(lower(value)).append(';');
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }

  /** RFC1123 GMT string of (iat + skew), matching Maverics' convertTime(). */
  private static String buildAuthTime(OidcUser user) {
    Instant iat = user.getIssuedAt();
    if (iat == null) {
      return null;
    }
    return RFC1123_GMT.format(iat.plusSeconds(AUTH_TIME_SKEW_SECONDS));
  }

  /** "S" if the acrs claim contains "c25", otherwise "W" (default weak). */
  private static String computeStrength(OidcUser user) {
    List<String> acrs = user.getClaimAsStringList(CLAIM_ACRS);
    if (acrs != null && acrs.contains("c25")) {
      return "S";
    }
    return "W";
  }

  private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(IdentityHeaderFilter::removeManagedHeaders)
            .build();
    return exchange.mutate().request(request).build();
  }

  private static void removeManagedHeaders(HttpHeaders h) {
    for (String name : MANAGED_HEADERS) {
      h.remove(name);
    }
  }

  private static void setIfPresent(HttpHeaders h, String name, String value) {
    if (value != null && !value.isBlank()) {
      h.set(name, value);
    }
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
