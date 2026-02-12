#!/bin/bash
# Fix audience mapper for Keycloak OAuth clients (Kafka / Strimzi)

set -euo pipefail

KEYCLOAK_URL="http://localhost:8080"
REALM_NAME="kafka-realm"
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo "======================================"
echo " Fixing Kafka OAuth audience (aud)"
echo "======================================"

############################
# Admin token
############################
echo "Getting admin token..."
ADMIN_TOKEN=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" | jq -r '.access_token')

if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == "null" ]]; then
  echo "❌ Failed to obtain admin token"
  exit 1
fi

echo "✓ Admin token acquired"

############################
# Add audience mapper
############################
add_audience_mapper() {
  local CLIENT_ID="$1"
  local AUDIENCE="kafka-broker"
  local MAPPER_NAME="aud-${AUDIENCE}"

  echo
  echo "Processing client: ${CLIENT_ID}"

  CLIENT_UUID=$(curl -s \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.[0].id')

  if [[ -z "$CLIENT_UUID" || "$CLIENT_UUID" == "null" ]]; then
    echo "❌ Client not found: ${CLIENT_ID}"
    return
  fi

  # Check if mapper already exists
  EXISTS=$(curl -s \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${CLIENT_UUID}/protocol-mappers/models" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | \
    jq -r ".[] | select(.name==\"${MAPPER_NAME}\") | .id")

  if [[ -n "$EXISTS" ]]; then
    echo "✓ Audience mapper already exists for ${CLIENT_ID}"
    return
  fi

  echo "Adding audience mapper..."

  curl -s -X POST \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${CLIENT_UUID}/protocol-mappers/models" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${MAPPER_NAME}\",
      \"protocol\": \"openid-connect\",
      \"protocolMapper\": \"oidc-audience-mapper\",
      \"consentRequired\": false,
      \"config\": {
        \"included.client.audience\": \"${AUDIENCE}\",
        \"access.token.claim\": \"true\",
        \"id.token.claim\": \"false\"
      }
    }" >/dev/null

  echo "✓ Audience mapper added for ${CLIENT_ID}"
}

############################
# Apply ONLY to token issuers
############################
add_audience_mapper "kafka-producer"
add_audience_mapper "kafka-consumer"
add_audience_mapper "kafka-controller"
add_audience_mapper "kafka-broker"

echo
echo "======================================"
echo " Kafka OAuth audience fix complete"
echo "======================================"

echo "Done! Test with:"
echo "curl -X POST http://localhost:8080/realms/kafka-realm/protocol/openid-connect/token \\"
echo "  -d 'grant_type=client_credentials' \\"
echo "  -d 'client_id=kafka-producer' \\"
echo "  -d 'client_secret=OK0MpmBK27AeSYkM3ipy2YCVwvUryPjQ'"