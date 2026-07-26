#!/usr/bin/env bash
# Runs after certbot renews the cert: rebuilds the PKCS12 keystore the gateway
# serves and restarts the gateway.
#
# Install: sudo cp to /etc/letsencrypt/renewal-hooks/deploy/gateway.sh
#          sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/gateway.sh
set -euo pipefail

DOMAIN="app.sundalei.tech"
LIVE="/etc/letsencrypt/live/${DOMAIN}"

# shellcheck disable=SC1091
source /etc/gateway-demo/gateway.env

openssl pkcs12 -export \
  -in "${LIVE}/fullchain.pem" \
  -inkey "${LIVE}/privkey.pem" \
  -name gateway \
  -out "${GATEWAY_TLS_KEYSTORE}" \
  -passout pass:"${GATEWAY_TLS_PASSWORD}"

chown appsvc:appsvc "${GATEWAY_TLS_KEYSTORE}"
chmod 640 "${GATEWAY_TLS_KEYSTORE}"

systemctl restart gateway.service
echo "Gateway keystore refreshed and service restarted."
