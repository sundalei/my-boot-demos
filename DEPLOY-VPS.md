# Deploy entra-gateway-demo + entra-id-backend to a VPS on 443 (app.sundalei.tech)

Goal: `https://app.sundalei.tech` → **gateway** (`entra-gateway-demo`) on **443** →
Entra login → **backend** (`entra-id-backend`) on **9091**.

> The backend's module default is 9090, but on Rocky **Cockpit** (the web console)
> already listens on 9090. `backend.service` therefore overrides it with
> `SERVER_PORT=9091`, and the gateway route in `application-prod.yml` points at 9091.
> If you'd rather free 9090 instead: `sudo systemctl disable --now cockpit.socket`.

The gateway binds 443 **directly** (no nginx/Caddy in front), running as a non-root
systemd service with `CAP_NET_BIND_SERVICE`. TLS cert from Let's Encrypt.

- Stack: Spring Boot 4.1 / Spring Cloud 2025.1 / **Java 21**, multi-module Maven.
- Everything is **https** — Entra rejects non-HTTPS redirect URIs for real hosts.
- Config files live in this repo's `deploy/` directory.

Assumes a **Rocky Linux 9** VPS with sudo (RHEL-family: `dnf`, `firewalld`, SELinux).

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
sudo dnf install -y java-21-openjdk-headless
ls -l /usr/bin/java                       # must exist
/usr/bin/java -version                    # expect 21.x
readlink -f /usr/bin/java                 # resolves into /usr/lib/jvm/...
```

> **Must be a system-wide JDK.** A per-user install (SDKMAN, `~/.sdkman/...`, a tarball
> in your home dir) will NOT work for the services: home directories are mode 700, so
> the `appsvc` service user can't execute anything under `/home/<you>`, and systemd
> fails with `status=203/EXEC`. Keep your SDKMAN JDK for `mvn package` if you like —
> the services need `/usr/bin/java` as well.

---

## 3. Build the two jars (multi-module)

Build on the VPS (needs JDK 21 + Maven) or locally, then copy the jars over.

```bash
sudo dnf install -y maven git       # if building on the VPS
# get the repo onto the box into ~/my-boot-demos, then from the repo ROOT:
cd ~/my-boot-demos
mvn -pl entra-gateway-demo,entra-id-backend -am -DskipTests package
```

`-pl` builds just these two modules, `-am` also builds the parent/anything they
depend on. You get:

- `entra-gateway-demo/target/entra-gateway-demo-1.0.0-SNAPSHOT.jar`
- `entra-id-backend/target/entra-id-backend-1.0.0-SNAPSHOT.jar`

**Verify both jars are executable fat jars before going further** — a module missing
`spring-boot-maven-plugin` silently produces a thin jar that dies instantly with
"no main manifest attribute":

```bash
for j in entra-gateway-demo entra-id-backend; do
  echo "== $j"
  unzip -p $j/target/$j-1.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF | grep -E "Main-Class|Start-Class"
done
```

Each must print `Main-Class: org.springframework.boot.loader.launch.JarLauncher` and
`Start-Class: com.example.Application`. If one prints nothing, its `pom.xml` is missing
the `spring-boot-maven-plugin` in `<build><plugins>`.

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

> Port **80 must be open in firewalld** for certbot's standalone challenge. If Step 9
> hasn't been run yet and certbot fails to validate, run the firewall commands from
> Step 9 now, then retry.

certbot lives in EPEL on Rocky, so enable EPEL first:

```bash
sudo dnf install -y epel-release
sudo dnf install -y certbot
sudo certbot certonly --standalone -d app.sundalei.tech --agree-tos -m you@example.com -n
```

Certs land in `/etc/letsencrypt/live/app.sundalei.tech/` → `fullchain.pem`, `privkey.pem`.

---

## 7. Convert the cert to a PKCS12 keystore for Spring

Just run these with `sudo` — values typed inline. **Replace `CHANGE_ME` with the
password you set as `GATEWAY_TLS_PASSWORD`, and make sure the output path matches
`GATEWAY_TLS_KEYSTORE`, both in `/etc/gateway-demo/gateway.env`.**

> ⚠️ **Don't paste the real password into this file** — it's committed to git. Keep it
> only in `/etc/gateway-demo/gateway.env` (mode 600) on the server.

```bash
sudo openssl pkcs12 -export \
  -in  /etc/letsencrypt/live/app.sundalei.tech/fullchain.pem \
  -inkey /etc/letsencrypt/live/app.sundalei.tech/privkey.pem \
  -name gateway \
  -out /opt/gateway-demo/app.p12 \
  -passout pass:CHANGE_ME

sudo chown appsvc:appsvc /opt/gateway-demo/app.p12
sudo chmod 640 /opt/gateway-demo/app.p12

sudo ls -l /opt/gateway-demo/app.p12       # verify: appsvc:appsvc, mode 640
```

<details>
<summary>Why not <code>source gateway.env</code> first? (permission denied)</summary>

`gateway.env` is mode 600 owned by `appsvc`, so your login user can't read it — and
`source` can't be sudo'd (it's a shell builtin, so `sudo source` gives
"command not found"). Plain `sudo openssl ... -out "$GATEWAY_TLS_KEYSTORE"` also
fails quietly, because your shell expands the variable _before_ sudo runs, leaving it
empty. `sudo` elevates a single command; it doesn't carry shell state.

If you'd rather have the values read from the env file automatically (no risk of them
drifting out of sync), run one privileged shell instead:

```bash
sudo bash -c 'source /etc/gateway-demo/gateway.env && \
  openssl pkcs12 -export \
    -in /etc/letsencrypt/live/app.sundalei.tech/fullchain.pem \
    -inkey /etc/letsencrypt/live/app.sundalei.tech/privkey.pem \
    -name gateway -out "$GATEWAY_TLS_KEYSTORE" \
    -passout pass:"$GATEWAY_TLS_PASSWORD" && \
  chown appsvc:appsvc "$GATEWAY_TLS_KEYSTORE" && \
  chmod 640 "$GATEWAY_TLS_KEYSTORE"'
```

</details>

---

## 8. Install and start the systemd services

```bash
sudo cp deploy/backend.service  /etc/systemd/system/backend.service
sudo cp deploy/gateway.service  /etc/systemd/system/gateway.service
sudo chown -R appsvc:appsvc /opt/gateway-demo

# Pre-flight: the SERVICE USER must be able to run java and read the jars.
# If this java check fails you'll get status=203/EXEC — see Step 2.
sudo -u appsvc /usr/bin/java -version
sudo -u appsvc test -r /opt/gateway-demo/gateway.jar && echo "gateway.jar readable"
sudo -u appsvc test -r /opt/gateway-demo/backend.jar && echo "backend.jar readable"

sudo systemctl daemon-reload
sudo systemctl enable --now backend.service
sudo systemctl enable --now gateway.service

sudo systemctl status backend.service --no-pager
sudo systemctl status gateway.service --no-pager
sudo ss -ltnp | grep -E ':(443|9091)'   # gateway on 443, backend on 9091 (both appsvc)
```

---

## 8b. Alternative: run the jars directly, no systemd

Useful for a quick capability demo, or when the eventual packaging (e.g.
`rpm-maven-plugin`) will supply the unit file later.

**No JVM flag can grant a low port.** Binding <1024 is a kernel privilege check on the
process — Spring can ask for 443, the kernel decides. So you change the process
privilege, not the java command. Three ways:

**(a) Run as root — simplest.** `sudo` strips the environment, so source the env file
and exec java in one shell (and run from `/opt/gateway-demo` so `application-prod.yml`
is picked up):

```bash
# backend (separate terminal)
sudo bash -c 'cd /opt/gateway-demo && exec java -jar backend.jar \
  --server.address=127.0.0.1 --server.port=9091'

# gateway on 443
sudo bash -c 'set -a; source /etc/gateway-demo/gateway.env; set +a; \
  cd /opt/gateway-demo && exec java -jar gateway.jar --spring.profiles.active=prod'
```

`set -a` auto-exports everything in the env file so `${ENTRA_*}` / `${GATEWAY_TLS_*}`
resolve. Ctrl-C stops it.

**(b) Lower the privileged-port threshold — no root for the app:**

```bash
sudo sysctl -w net.ipv4.ip_unprivileged_port_start=443
sudo chown "$USER" /opt/gateway-demo/app.p12     # keystore is 640 appsvc; let your user read it
cd /opt/gateway-demo && java -jar gateway.jar --spring.profiles.active=prod   # no sudo
```

Revert: `sudo sysctl -w net.ipv4.ip_unprivileged_port_start=1024` (not persistent unless
added to `/etc/sysctl.d/`). Remember to restore the keystore ownership to `appsvc`
before going back to the systemd services.

**(c) `setcap` on the JVM binary** — broad and wiped on JDK updates, so least preferred:

```bash
sudo setcap cap_net_bind_service=+ep "$(readlink -f /usr/bin/java)"   # undo: setcap -r <path>
```

> Stop the systemd services first (`sudo systemctl stop gateway backend`) — only one
> process can hold 443. For anything long-lived, prefer the systemd units in Step 8:
> `AmbientCapabilities=CAP_NET_BIND_SERVICE` keeps the app non-root and changes nothing
> globally. Carry that same line into the RPM's unit file when you package it.

---

## 9. Firewall (firewalld)

```bash
sudo firewall-cmd --permanent --add-service=http     # port 80, cert issuance/renewal
sudo firewall-cmd --permanent --add-service=https    # port 443, the gateway
sudo firewall-cmd --reload
sudo firewall-cmd --list-services                    # verify http https present
```

---

## 10. Test end to end

0. Quick local check first: `curl -s localhost:9091/api/me/headers` → the backend
   responds with `"Not Provided"` values (proves it's up; no headers when bypassing
   the gateway).
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

> ⚠️ **If you ever restart Caddy on port 80, standalone renewal will fail** (Caddy
> holds the port). Either keep Caddy off, or switch renewal to the webroot/DNS plugin.
> Certs last 90 days; `sudo certbot certificates` shows the expiry.

---

## Restoring Caddy (rollback)

```bash
sudo systemctl stop gateway.service backend.service
sudo systemctl disable gateway.service backend.service   # no auto-start on reboot
sudo systemctl enable --now caddy
sudo ss -ltnp | grep -E ':(80|443)'      # caddy owns them again
```

Handoff: the gateway must release 443 before Caddy can rebind it.

Nothing is destroyed by this — jars, keystore, `gateway.env`, the units, the cert and
the Entra registration all stay in place. **To bring the gateway back later:**

```bash
sudo systemctl stop caddy
sudo systemctl enable --now backend.service gateway.service
sudo ss -ltnp | grep -E ':(443|9091)'
```

> ⚠️ **Cert expiry is the one thing that degrades while the gateway is off.** Renewal
> uses standalone mode and needs port 80, which Caddy will be holding — so
> `certbot renew` silently fails and the cert (90-day life) can lapse. Before
> restarting the gateway after a long pause: `sudo certbot certificates` to check the
> date, and if needed stop Caddy, `sudo certbot renew --force-renewal`, then rebuild
> the p12 (Step 7). For a permanent fix, switch renewal to the webroot or DNS plugin so
> it doesn't need exclusive use of port 80.
>
> Also note the renewal deploy hook ends with `systemctl restart gateway.service`, and
> `restart` _starts_ a stopped unit — so if renewal ever succeeds while Caddy is up, the
> hook would start the gateway and fight Caddy for 443.

---

## Final verification checklist

Run these to confirm a known-good state. All should pass on a working deployment.

```bash
# 1. Both services active (not "activating (auto-restart)")
systemctl is-active backend.service gateway.service          # active / active

# 2. Correct ports, correct owner (gateway 443, backend 9091, both java as appsvc)
sudo ss -ltnp | grep -E ':(443|9091)'

# 3. Gateway is NOT running as root
ps -o user= -p "$(systemctl show -p MainPID --value gateway.service)"   # appsvc

# 4. Backend is loopback-only (must NOT be 0.0.0.0/*)
sudo ss -ltnp | grep ':9091'                                  # expect 127.0.0.1:9091

# 5. Backend reachable locally, and header-less when bypassing the gateway
curl -s localhost:9091/api/me/headers                          # "Not Provided" values

# 6. TLS served correctly on 443 with a valid chain (no -k needed)
curl -sI https://app.sundalei.tech/ | head -1                  # 302 to Microsoft login
echo | openssl s_client -connect app.sundalei.tech:443 -servername app.sundalei.tech 2>/dev/null \
  | openssl x509 -noout -subject -dates                        # CN=app.sundalei.tech, not expired

# 7. Cert renewal path works end to end
sudo certbot renew --dry-run

# 8. Secrets locked down
sudo ls -l /etc/gateway-demo/gateway.env                       # -rw------- appsvc
sudo ls -l /opt/gateway-demo/app.p12                           # -rw-r----- appsvc

# 9. Survives reboot (both enabled)
systemctl is-enabled backend.service gateway.service           # enabled / enabled
```

Then the browser test: `https://app.sundalei.tech/api/me/headers` after login should
show your real `sub`, decoded `name`, `email`, and a Bearer token preview.

---

## Troubleshooting quick hits

- **`AADSTS50011` redirect mismatch** → the registered URI must be exactly
  `https://app.sundalei.tech/login/oauth2/code/entra`.
- **`status=203/EXEC` on either service** → systemd couldn't execute the binary in
  `ExecStart`, i.e. `/usr/bin/java` is missing or unusable by `appsvc`. Usually means
  Java was installed per-user (SDKMAN) rather than system-wide — see Step 2. Verify
  with `ls -l /usr/bin/java` and `sudo -u appsvc /usr/bin/java -version`. If your JDK
  lives elsewhere, point the units at the real path from `readlink -f "$(which java)"`
  and `sudo systemctl daemon-reload`.
- **Gateway won't bind 443 / "address already in use"** → Caddy still running
  (Step 5b). `sudo ss -ltnp | grep ':443'`.
- **Gateway won't bind 443 / permission denied** → check `AmbientCapabilities` in the
  unit and that you ran `daemon-reload`.
- **`/api/me/headers` shows "Not Provided"** → you hit the backend directly instead of
  through the gateway, or the gateway isn't injecting headers — check gateway logs.
- **Backend exits `status=1/FAILURE` with "port already in use"** → something else owns
  the port. On Rocky, Cockpit holds 9090 (`sudo ss -ltnp | grep ':9090'` shows
  `cockpit-tls`), which is why the unit uses `SERVER_PORT=9091`. Either keep 9091 or
  free 9090 with `sudo systemctl disable --now cockpit.socket`.
- **Gateway exits `status=1/FAILURE` in well under a second** (tiny memory/CPU) →
  the jar isn't an executable fat jar. Check:
  `unzip -p /opt/gateway-demo/gateway.jar META-INF/MANIFEST.MF | grep -i main-class`.
  If empty, the module was missing `spring-boot-maven-plugin` in its `<build>` — it's
  now in `entra-gateway-demo/pom.xml`; rebuild and re-copy the jar (Step 3).
- **502 / backend unreachable** → `systemctl status backend`; must listen on
  127.0.0.1:9091 (the unit sets `SERVER_ADDRESS` and `SERVER_PORT`).
- **Secrets** → never bake the client secret into the jar; it stays in
  `/etc/gateway-demo/gateway.env` (mode 600). The module's dev `application.yml` has
  empty client-id/secret/issuer on purpose — the `prod` profile supplies them.
- **SELinux (Rocky is enforcing by default)** → the `CAP_NET_BIND_SERVICE` capability
  handles the 443 bind, and a systemd service normally runs unconfined, so it usually
  works as-is. If the gateway fails to bind or read the keystore, check for denials:
  `sudo ausearch -m avc -ts recent` or `sudo journalctl -u gateway.service`. To confirm
  SELinux is the cause, test with `sudo setenforce 0` (permissive) — if it then works,
  add a proper rule (e.g. `sudo semanage port -a -t http_port_t -p tcp 443` is already
  labeled, so the likely fix is `audit2allow`) rather than leaving SELinux disabled.
  Check current mode with `getenforce`.

---

## How this maps to the Maverics cutover

Same shape as replacing Maverics: a non-root service binding 443 via
`CAP_NET_BIND_SERVICE`, serving a real cert, doing Entra OIDC, injecting the
`X-Auth-*` headers + relaying the token to the backend. Swap `app.sundalei.tech` for
`dalei.com`, reuse the real registration/cert, and the steps are identical.
