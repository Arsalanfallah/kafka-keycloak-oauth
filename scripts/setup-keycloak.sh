#!/bin/bash
set -euo pipefail

echo "======================================" >&2
echo " Keycloak Setup for Kafka OAuth (Strimzi)" >&2
echo "======================================" >&2

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

cd "$(dirname "$0")/.."

KEYCLOAK_URL="http://localhost:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM_NAME="kafka-realm"

KEYCLOAK_TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/token"
KEYCLOAK_JWKS_ENDPOINT="${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/certs"

#######################################
# Wait for Keycloak to be ready
#######################################
echo -e "${YELLOW}Waiting for Keycloak...${NC}" >&2
for i in {1..20}; do
  if curl -sf "${KEYCLOAK_URL}/health/ready" >/dev/null; then
    echo -e "${GREEN}✓ Keycloak is ready${NC}" >&2
    break
  fi
  sleep 2
done

#######################################
# Get admin token
#######################################
ADMIN_TOKEN=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [[ -z "$ADMIN_TOKEN" || "$ADMIN_TOKEN" == "null" ]]; then
  echo -e "${RED}Failed to obtain admin token${NC}" >&2
  exit 1
fi

#######################################
# Create realm if missing
#######################################
if ! curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" | grep -q 200; then
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{\"realm\":\"${REALM_NAME}\",\"enabled\":true}" >/dev/null
fi
echo -e "${GREEN}✓ Realm ready${NC}" >&2

#######################################
# Delete existing clients
#######################################
delete_client() {
  CLIENT_ID="$1"
  CLIENT_UUID=$(curl -s \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.[0].id')

  if [[ "$CLIENT_UUID" != "null" && -n "$CLIENT_UUID" ]]; then
    curl -s -X DELETE \
      "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${CLIENT_UUID}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" >/dev/null
    echo -e "${YELLOW}Deleted old client: ${CLIENT_ID}${NC}" >&2
  fi
}

for C in kafka-broker kafka-controller kafka-producer kafka-consumer; do
  delete_client "$C"
done

#######################################
# Create clients and get secrets
#######################################
create_client() {
  CLIENT_ID="$1"
  echo -e "${YELLOW}Creating client: ${CLIENT_ID}${NC}" >&2

  curl -s -X POST \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"clientId\": \"${CLIENT_ID}\",
      \"enabled\": true,
      \"publicClient\": false,
      \"serviceAccountsEnabled\": true,
      \"standardFlowEnabled\": false,
      \"directAccessGrantsEnabled\": false,
      \"protocol\": \"openid-connect\"
    }" >/dev/null || true

  CLIENT_UUID=$(curl -s \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.[0].id')

  SECRET=$(curl -s \
    "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${CLIENT_UUID}/client-secret" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.value')

  echo -e "${GREEN}✓ Client ${CLIENT_ID} ready${NC}" >&2
  echo "${SECRET}"
}

BROKER_SECRET=$(create_client kafka-broker)
CONTROLLER_SECRET=$(create_client kafka-controller)
PRODUCER_SECRET=$(create_client kafka-producer)
CONSUMER_SECRET=$(create_client kafka-consumer)

#######################################
# Generate .env
#######################################
cat > .env <<EOF
KAFKA_VERSION=4.1.0
CLUSTER_ID=kafka-cluster-01

KEYCLOAK_REALM=${REALM_NAME}
KEYCLOAK_ISSUER=${KEYCLOAK_URL}/realms/${REALM_NAME}
KEYCLOAK_TOKEN_ENDPOINT=${KEYCLOAK_TOKEN_ENDPOINT}
KEYCLOAK_JWKS_ENDPOINT=${KEYCLOAK_JWKS_ENDPOINT}

KAFKA_BROKER_CLIENT_SECRET=${BROKER_SECRET}
KAFKA_CONTROLLER_CLIENT_SECRET=${CONTROLLER_SECRET}
KAFKA_PRODUCER_CLIENT_SECRET=${PRODUCER_SECRET}
KAFKA_CONSUMER_CLIENT_SECRET=${CONSUMER_SECRET}
EOF

echo -e "${GREEN}✓ .env generated${NC}" >&2

#######################################
# Generate Kafka runtime configs
#######################################
mkdir -p kafka-config

KAFKA_COMMON="
security.protocol=SASL_SSL
sasl.mechanism=OAUTHBEARER
sasl.oauthbearer.token.endpoint.url=${KEYCLOAK_TOKEN_ENDPOINT}

ssl.truststore.location=/etc/kafka/secrets/kafka.server.truststore.jks
ssl.truststore.password=changeit
ssl.endpoint.identification.algorithm=
"

# Controller properties
cat > kafka-config/controller.properties <<EOF
${KAFKA_COMMON}
# Runtime OAuth config
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \\
  oauth.client.secret="${CONTROLLER_SECRET}" \\
  oauth.token.endpoint.uri="${KEYCLOAK_TOKEN_ENDPOINT}";
EOF

# Broker properties
cat > kafka-config/broker.properties <<EOF
${KAFKA_COMMON}
# Runtime OAuth config
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \\
  oauth.client.secret="${BROKER_SECRET}" \\
  oauth.token.endpoint.uri="${KEYCLOAK_TOKEN_ENDPOINT}";
EOF

# Producer / Consumer
declare -A CLIENT_SECRETS=(
  [producer]="$PRODUCER_SECRET"
  [consumer]="$CONSUMER_SECRET"
)

for ROLE in producer consumer; do
  SECRET="${CLIENT_SECRETS[$ROLE]}"
  cat > kafka-config/${ROLE}.properties <<EOF
${KAFKA_COMMON}
# Runtime OAuth config
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \\
  oauth.client.secret="${SECRET}" \\
  oauth.token.endpoint.uri="${KEYCLOAK_TOKEN_ENDPOINT}";
EOF
done

echo -e "${GREEN}✓ Kafka runtime configs generated${NC}" >&2

#######################################
# Test token issuance
#######################################
echo -e "${YELLOW}Testing token issuance...${NC}" >&2

declare -A TOKEN_SECRETS=(
  [kafka-broker]="$BROKER_SECRET"
  [kafka-controller]="$CONTROLLER_SECRET"
  [kafka-producer]="$PRODUCER_SECRET"
  [kafka-consumer]="$CONSUMER_SECRET"
)

for C in kafka-broker kafka-controller kafka-producer kafka-consumer; do
  SECRET="${TOKEN_SECRETS[$C]}"
  SUCCESS=false

  for i in {1..5}; do
    TOKEN=$(curl -s -X POST \
      "${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=client_credentials" \
      -d "client_id=${C}" \
      -d "client_secret=${SECRET}" | jq -r '.access_token')

    if [[ -n "$TOKEN" && "$TOKEN" != "null" ]]; then
      echo -e "${GREEN}✓ Token OK for ${C}${NC}" >&2
      SUCCESS=true
      break
    fi
    sleep 2
  done

  if [[ "$SUCCESS" = false ]]; then
    echo -e "${RED}✗ Token FAILED for ${C}${NC}" >&2
  fi
done

echo ""
echo -e "${GREEN}======================================"
echo " Keycloak Kafka OAuth setup complete"
echo "======================================${NC}" >&2
