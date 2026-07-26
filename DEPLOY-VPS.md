# Deploy entra-gateway-demo + entra-id-backend to a VPS on 443 (app.sundalei.tech)

Goal: `https://app.sundalei.tech` → **gateway** (`entra-gateway-demo`) on **443** →
Entra login → **backend** (`entra-id-backend`) on 9090.

The gateway binds 443 **directly** (no nginx/Caddy in front), running as a non-root
systemd service with `CAP_NET_BIND_SERVICE`. TLS cert from Let's Encrypt.

- Stack: Spring Boot 4.1 / Spring Cloud 2025.1 / **Java 21**, multi-module Maven.
- Everything is **https** — Entra rejects non-HTTPS redirect URIs for real hosts.
- Config files live in this repo's `deploy/` directory.

Assumes an Ubuntu/Debian VPS with sudo.

---

## 0. Prerequisites

- VPS with a public IP and root/sudo.
- Access to an Entra tenant where you can create an app registration.
- Ports **80** and **443** open in the VPS firewall / cloud security group
  (80 is only for obtaining/renewing the cert).
- **Caddy is currently serving other apps on 80/443** — you'll stop it in Step 5b.

---

## 1. Point DNS at the VPS

Create an **A record**: `app.sundalei.tech` → your VPS public IP.
```bash
dig +short app.sundalei.tech        # should print your VPS IP
```

---

## 2. Install Java 21 on the VPS

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version                       # expect 21.x
```

---

## 3. Build the two jars (multi-module)

Build on the VPS (needs JDK 21 + Maven) or locally, then copy the jars over.

```bash
sudo apt install -y maven git       # if building on the VPS
# get the repo onto the box into ~/my-boot-demos, then from the repo ROOT:
cd ~/my-boot-demos
mvn -pl entra-gateway-demo,entra-id-backend -am -DskipTests package
```

`-pl` builds just these two modules, `-am` also builds the parent/anything they
depend on. You get:
- `entra-gateway-demo/target/entra-gateway-demo-1.0.0-SNAPSHOT.jar`
- `entra-id-backend/target/entra-id-backend-1.0.0-SNAPSHOT.jar`

Stage them under stable names the services expect:
```bash
sudo mkdir -p /opt/gateway-demo
sudo cp entra-gateway-demo/target/entra-gateway-demo-1.0.0-SNAPSHOT.jar /opt/gateway-demo/gateway.jar
sudo cp entra-id-backend/target/entra-id-backend-1.0.0-SNAPSHOT.jar     /opt/gateway-demo/backend.jar
sudo cp deploy/application-prod.yml /opt/gateway-demo/application-prod.yml
```

> The gateway pom includes a macOS-only Netty resolver
> (`netty-resolver-dns-native-macos`). It's harmless on Linux (unused), so you can
> leave it; optionally remove that dependency for a cleaner prod build.

---

## 4. Create the Entra app registration

Azure portal → **Entra ID → App registrations → New registration**:

1. Name: `app-sundalei-gateway`.
2. **Redirect URI** → platform **Web** →
   ```text
   https://app.sundalei.tech/login/oauth2/code/entra
   ```
3. Register. Copy **Application (client) ID** and **Directory (tenant) ID** from Overview.
4. **Certificates & secrets → New client secret** → copy the secret **Value**.

---

## 5. Service user + env file

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin appsvc
sudo mkdir -p /etc/gateway-demo
sudo cp deploy/gateway.env.example /etc/gateway-demo/gateway.env
sudo nano /etc/gateway-demo/gateway.env      # fill tenant/client/secret + keystore password
sudo chmod 600 /etc/gateway-demo/gateway.env
sudo chown appsvc:appsvc /etc/gateway-demo/gateway.env
```

---

## 5b. Free ports 80 and 443 (stop Caddy)

Caddy owns 80/443 for your other apps. Only one process can hold a port at a time,
so stop Caddy before certbot uses 80 (Step 6) and before the gateway binds 443 (Step 8).

> While Caddy is stopped, its apps are **offline**. Use a window you're OK with;
> "Restoring Caddy" (below) brings them back.

```bash
sudo systemctl status caddy --no-pager
sudo ss -ltnp | grep -E ':(80|443)'      # should show caddy
sudo systemctl stop caddy
sudo systemctl disable caddy             # optional; re-enable on restore
sudo ss -ltnp | grep -E ':(80|443)'      # should now print nothing
```

---

## 6. Get the TLS certificate (Let's Encrypt)

```bash
sudo apt install -y certbot
sudo certbot certonly --standalone -d app.sundalei.tech --agree-tos -m you@example.com -n
```
Certs land in `/etc/letsencrypt/live/app.sundalei.tech/` → `fullchain.pem`, `privkey.pem`.

---

## 7. Convert the cert to a PKCS12 keystore for Spring

Use the same password you put in `gateway.env` as `GATEWAY_TLS_PASSWORD`:
```bash
source /etc/gateway-demo/gateway.env
sudo openssl pkcs12 -export \
  -in  /etc/letsencrypt/live/app.sundalei.tech/fullchain.pem \
  -inkey /etc/letsencrypt/live/app.sundalei.tech/privkey.pem \
  -name gateway \
  -out "$GATEWAY_TLS_KEYSTORE" \
  -passout pass:"$GATEWAY_TLS_PASSWORD"
sudo chown appsvc:appsvc "$GATEWAY_TLS_KEYSTORE"
sudo chmod 640 "$GATEWAY_TLS_KEYSTORE"
```

---

## 8. Install and start the systemd services

```bash
sudo cp deploy/backend.service  /etc/systemd/system/backend.service
sudo cp deploy/gateway.service  /etc/systemd/system/gateway.service
sudo chown -R appsvc:appsvc /opt/gateway-demo

sudo systemctl daemon-reload
sudo systemctl enable --now backend.service
sudo systemctl enable --now gateway.service

sudo systemctl status backend.service --no-pager
sudo systemctl status gateway.service --no-pager
sudo ss -ltnp | grep ':443'          # java owned by appsvc
```

---

## 9. Firewall

```bash
sudo ufw allow 80/tcp     # cert issuance/renewal
sudo ufw allow 443/tcp    # the gateway
```

---

## 10. Test end to end

1. `https://app.sundalei.tech/me` → after Entra login, the **gateway's** own
   `/me` endpoint returns your OIDC claims.
2. `https://app.sundalei.tech/api/me/headers` → proxied to the **backend**; shows
   `X-Auth-Sub / X-Auth-Name / X-Auth-Email` injected by the gateway, plus a preview
   of the relayed Bearer token (TokenRelay).
3. `https://app.sundalei.tech/api/me/token/decoded` → backend decodes the relayed
   access token payload.

Logs:
```bash
sudo journalctl -u gateway.service -f
sudo journalctl -u backend.service -f
```

> Routing note: the gateway's own controllers (`/me`, the `/login/**` and
> `/oauth2/**` OIDC endpoints) are handled locally; everything else matches
> `Path=/**` and is proxied to the backend. So `/me` = gateway, `/api/me/**` = backend.

---

## 11. Automate cert renewal

```bash
sudo cp deploy/letsencrypt-deploy-hook.sh /etc/letsencrypt/renewal-hooks/deploy/gateway.sh
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/gateway.sh
sudo certbot renew --dry-run
```
Renewal uses standalone mode (needs port 80 free momentarily). Since the gateway is
on 443 and Caddy stays stopped, port 80 is free — renewal works, then the hook
rebuilds the keystore and restarts the gateway.

---

## Restoring Caddy (rollback)

```bash
sudo systemctl stop gateway.service backend.service
sudo systemctl disable gateway.service backend.service   # optional
sudo systemctl enable --now caddy
sudo ss -ltnp | grep -E ':(80|443)'      # caddy owns them again
```
Handoff: the gateway must release 443 before Caddy can rebind it.

---

## Troubleshooting quick hits

- **`AADSTS50011` redirect mismatch** → the registered URI must be exactly
  `https://app.sundalei.tech/login/oauth2/code/entra`.
- **Gateway won't bind 443 / "address already in use"** → Caddy still running
  (Step 5b). `sudo ss -ltnp | grep ':443'`.
- **Gateway won't bind 443 / permission denied** → check `AmbientCapabilities` in the
  unit and that you ran `daemon-reload`.
- **`/api/me/headers` shows "Not Provided"** → you hit the backend directly instead of
  through the gateway, or the gateway isn't injecting headers — check gateway logs.
- **502 / backend unreachable** → `systemctl status backend`; must listen on
  127.0.0.1:9090 (the unit sets `SERVER_ADDRESS=127.0.0.1`).
- **Secrets** → never bake the client secret into the jar; it stays in
  `/etc/gateway-demo/gateway.env` (mode 600). The module's dev `application.yml` has
  empty client-id/secret/issuer on purpose — the `prod` profile supplies them.

---

## How this maps to the Maverics cutover

Same shape as replacing Maverics: a non-root service binding 443 via
`CAP_NET_BIND_SERVICE`, serving a real cert, doing Entra OIDC, injecting the
`X-Auth-*` headers + relaying the token to the backend. Swap `app.sundalei.tech` for
`dalei.com`, reuse the real registration/cert, and the steps are identical.
