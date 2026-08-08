# Outbound TLS to the backend — why the client cert broke the no-access user

## TL;DR

`spring.cloud.gateway.httpclient.ssl.key-store` makes the gateway present a **client certificate**
to the backend. The backend's Apache vhost on `:9443` has `SSLVerifyClient optional` and
`RequestHeader set SSL_CLIENT_S_DN`, so that certificate's DN is forwarded to the app — and the
app's `express-auth` middleware has an **`upstream-cert` login strategy** that authenticates the
request from it.

Effect: a user who is *not* provisioned in the app is no longer treated as unauthenticated. The
header lookup fails, `express-auth` falls through to `upstream-cert`, and the request is
authenticated as the **certificate's user** instead. The SPA then loads and fails later.

**Fix: do not send a client certificate on the outbound connection.** Keep only the trust material
needed to verify the backend's server certificate.

## The evidence

Same user (`t721303`, deliberately deleted from dev Mongo), same endpoint, same day:

```
# via Maverics — 09:22:07                     -> GET /screening 302  (clean redirect to /login)
auth - Using user query { externalId: 't721303' }
[INFO] auth - no user authenticated, redirecting to login app

# via our gateway — 09:38:34                  -> GET /screening 200, then /common/customer 400
auth - Using user query { externalId: 't721303' }
auth - Found user ' FA0CXHL ' using provider > upstream-cert
```

Identical query, identical "not found". The only difference is the `upstream-cert` fallback, which
only succeeds because our TLS connection presents a client certificate.

(Note: the periodic `FA43412 / upstream-cert` lines every 15s are the health-check probes — those
legitimately use cert auth and are unrelated.)

## key-store vs trust-store — the distinction that matters

This is the part that is easy to get backwards:

| Setting | What it holds | What it does | Needed here? |
|---|---|---|---|
| **key-store** | **our own** private key + certificate | Presents a **client certificate** to the server (mutual TLS). Proves *who we are*. | **NO — remove it.** This is what triggers `upstream-cert`. |
| **trust-store** | CA certificates we trust | Verifies the **server's** certificate. Proves *who they are*. | **YES** — the backend uses an internal CA. |

If the goal was "make HTTPS to `:9443` work", the trust-store is what was needed — the key-store was
not. `SSLVerifyClient optional` means the backend accepts a client certificate but does not require
one; Maverics does not send one, which is why it behaves correctly.

## Refined configuration

Replace the current block:

```properties
# BEFORE — presents a client certificate, causing upstream-cert auth in the backend
spring.cloud.gateway.httpclient.ssl.key-store=${application.user.keyStore}
spring.cloud.gateway.httpclient.ssl.key-store-password=${application.user.keyStorePassword}
spring.cloud.gateway.httpclient.ssl.key-store-type=JKS
```

with:

```properties
# TLS for outbound gateway HTTP client calls to the backend (https://<alias>:9443).
#
# IMPORTANT: do NOT configure a key-store here.
#
# A key-store makes the gateway present a CLIENT CERTIFICATE. The backend vhost on :9443 has
#   SSLVerifyClient optional
#   RequestHeader set SSL_CLIENT_S_DN "%{SSL_CLIENT_S_DN}s"
# so the certificate DN reaches the application, and express-auth's `upstream-cert` strategy
# authenticates the request as that certificate's user. An unprovisioned end user is then treated
# as logged in (as the cert user) instead of being redirected to the app's /login page.
# Maverics does not present a client certificate, which is why it produces the correct 302.
#
# We only need to TRUST the backend's server certificate (issued by the internal CA):
spring.cloud.gateway.httpclient.ssl.trusted-x509-certificates=${application.backend.caFile}
```

Notes on the trust setting:

- Point `application.backend.caFile` at the internal CA bundle the backend's cert chains to — the
  Apache config referenced `/etc/pki/tls/certs/ca-bundle.crt` for this app, so that (or the
  UBS-internal CA file) is the right value.
- If the internal CA is already in the JVM truststore (`cacerts`, or a `-Djavax.net.ssl.trustStore`
  set at startup), you can omit `trusted-x509-certificates` entirely — Reactor Netty will use the
  default trust manager.
- **Do not** use `spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true`. It disables
  server certificate verification. It will "work" and it is not acceptable for a bank.

## Verify after the change

1. Keep the test user deleted from dev Mongo.
2. Restart the gateway, clear cookies / use a clean incognito window, hit `/screening`.
3. Expected: **302** to `/login#/screening/` — same as Maverics.

Confirm in both logs:

```
# Apache — expect 302, and no /common/customer at all
grep -a "t721303" /data0/logs/apache/access_log.$(date +%Y.%m.%d) \
  | grep -aE '"GET /screening HTTP|/common/customer|"GET /login HTTP' | tail -10

# App — expect the redirect line, and NO "using provider > upstream-cert" for our request
grep -H -a "$(date +%Y-%m-%d)" $(ls /data0/logs/node/*.log | grep -v '__2026') 2>/dev/null \
  | grep -a "auth -" | tail -20
```

Success looks like:

```
auth - Using user query { externalId: 't721303' }
[INFO] auth - no user authenticated, redirecting to login app
```

4. Then **restore the user** in dev Mongo and confirm the provisioned path still works end to end
   (including creating an assessment). This should be unaffected: for a provisioned user the
   `externalId` lookup succeeds first, so `upstream-cert` never runs — which is exactly why the
   working user behaved correctly even with the certificate configured.

## Before you remove it — is the certificate needed for anything else?

Worth a moment's thought rather than assuming:

- **Backend requires mTLS?** No — `SSLVerifyClient optional`, and Maverics connects without one.
- **Used for audit / attribution?** The app logs `SSL_CLIENT_S_DN`-derived users (e.g. `FA43412` for
  health checks). If any downstream process expects the gateway's traffic to carry a specific cert
  identity, removing it changes that. Nothing observed suggests it does, but confirm with the team
  who issued `application.user.keyStore` what it was intended for.
- **Other backends?** `httpclient.ssl.*` is global to the gateway's HTTP client. If a future route
  talks to a service that genuinely requires mTLS, configure it per-route rather than globally.

If it turns out the certificate *is* required for some other reason, the alternative is to keep it
and have the backend not act on it — i.e. ask Ripjar to disable the `upstream-cert` strategy for
this application, or have Apache stop forwarding `SSL_CLIENT_S_DN` for gateway traffic. Removing the
client certificate from the gateway is the simpler and more faithful reproduction of Maverics.
