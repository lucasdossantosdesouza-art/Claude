#!/bin/sh
# Gera o /config.json em tempo de execução a partir das variáveis de ambiente do container
set -e
cat > /usr/share/nginx/html/config.json <<JSON
{
  "apiUrl": "${API_URL:-/api}",
  "keycloak": {
    "url": "${KEYCLOAK_URL:-http://localhost:8180}",
    "realm": "${KEYCLOAK_REALM:-comissao}",
    "clientId": "${KEYCLOAK_CLIENT_ID:-comissao-web}"
  }
}
JSON
