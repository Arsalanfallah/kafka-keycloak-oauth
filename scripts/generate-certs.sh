#!/bin/bash
set -e

echo "======================================"
echo "Kafka SSL Certificate Generation"
echo "======================================"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

cd "$(dirname "$0")/.."

mkdir -p kafka-security/{ca,broker,client}

# =====================
# Certificate Authority
# =====================
echo -e "${YELLOW}Generating Certificate Authority...${NC}"

openssl req -new -x509 \
  -keyout kafka-security/ca/ca-key.pem \
  -out kafka-security/ca/ca-cert.pem \
  -days 3650 \
  -passout pass:changeit \
  -subj "/C=US/ST=State/L=City/O=Organization/OU=IT/CN=KafkaCA"

echo -e "${GREEN}✓ CA certificate generated${NC}"

# =====================
# Broker Certificate
# =====================
echo -e "${YELLOW}Generating broker certificates...${NC}"

BROKER_ALIAS=kafka-broker

keytool -genkeypair \
  -alias ${BROKER_ALIAS} \
  -keyalg RSA \
  -keysize 2048 \
  -keystore kafka-security/broker/kafka.server.keystore.jks \
  -validity 3650 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=kafka-broker,OU=IT,O=Organization,L=City,ST=State,C=US" \
  -ext SAN=dns:kafka-broker,dns:localhost,ip:127.0.0.1

# CSR
keytool -certreq \
  -alias ${BROKER_ALIAS} \
  -keystore kafka-security/broker/kafka.server.keystore.jks \
  -file kafka-security/broker/kafka-broker.csr \
  -storepass changeit

# v3 extensions
cat > kafka-security/broker/v3.ext <<EOF
[v3_req]
subjectAltName = DNS:kafka-broker,DNS:localhost,IP:127.0.0.1
EOF

# Sign CSR
openssl x509 -req \
  -in kafka-security/broker/kafka-broker.csr \
  -CA kafka-security/ca/ca-cert.pem \
  -CAkey kafka-security/ca/ca-key.pem \
  -CAcreateserial \
  -out kafka-security/broker/kafka-broker-signed.pem \
  -days 3650 \
  -sha256 \
  -passin pass:changeit \
  -extensions v3_req \
  -extfile kafka-security/broker/v3.ext

# Import CA first
keytool -importcert \
  -alias CARoot \
  -file kafka-security/ca/ca-cert.pem \
  -keystore kafka-security/broker/kafka.server.keystore.jks \
  -storepass changeit \
  -noprompt

# Import signed cert
keytool -importcert \
  -alias ${BROKER_ALIAS} \
  -file kafka-security/broker/kafka-broker-signed.pem \
  -keystore kafka-security/broker/kafka.server.keystore.jks \
  -storepass changeit \
  -noprompt

# Broker truststore
keytool -importcert \
  -alias CARoot \
  -file kafka-security/ca/ca-cert.pem \
  -keystore kafka-security/broker/kafka.server.truststore.jks \
  -storepass changeit \
  -noprompt

echo -e "${GREEN}✓ Broker certificates generated${NC}"

# =====================
# Client Certificate (for mTLS)
# =====================
echo -e "${YELLOW}Generating client certificates...${NC}"

CLIENT_ALIAS=kafka-client

# Client keystore
keytool -genkeypair \
  -alias ${CLIENT_ALIAS} \
  -keyalg RSA \
  -keysize 2048 \
  -keystore kafka-security/client/kafka.client.keystore.jks \
  -validity 3650 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=kafka-client,OU=IT,O=Organization,L=City,ST=State,C=US"

# CSR
keytool -certreq \
  -alias ${CLIENT_ALIAS} \
  -keystore kafka-security/client/kafka.client.keystore.jks \
  -file kafka-security/client/kafka-client.csr \
  -storepass changeit

# v3 extensions
cat > kafka-security/client/v3.ext <<EOF
[v3_req]
subjectAltName = DNS:kafka-client,DNS:localhost,IP:127.0.0.1
EOF

# Sign client certificate
openssl x509 -req \
  -in kafka-security/client/kafka-client.csr \
  -CA kafka-security/ca/ca-cert.pem \
  -CAkey kafka-security/ca/ca-key.pem \
  -CAcreateserial \
  -out kafka-security/client/kafka-client-signed.pem \
  -days 3650 \
  -sha256 \
  -passin pass:changeit \
  -extensions v3_req \
  -extfile kafka-security/client/v3.ext

# Import CA first
keytool -importcert \
  -alias CARoot \
  -file kafka-security/ca/ca-cert.pem \
  -keystore kafka-security/client/kafka.client.keystore.jks \
  -storepass changeit \
  -noprompt

# Import signed client cert
keytool -importcert \
  -alias ${CLIENT_ALIAS} \
  -file kafka-security/client/kafka-client-signed.pem \
  -keystore kafka-security/client/kafka.client.keystore.jks \
  -storepass changeit \
  -noprompt

# Client truststore (trust broker CA)
keytool -importcert \
  -alias CARoot \
  -file kafka-security/ca/ca-cert.pem \
  -keystore kafka-security/client/kafka.client.truststore.jks \
  -storepass changeit \
  -noprompt

echo -e "${GREEN}✓ Client certificates generated${NC}"

# =====================
# Verification
# =====================
echo -e "${YELLOW}Verifying certificates...${NC}"

echo "Broker keystore:"
keytool -list -v -keystore kafka-security/broker/kafka.server.keystore.jks -storepass changeit | grep -E "Alias name|chain length"

echo "Client keystore:"
keytool -list -v -keystore kafka-security/client/kafka.client.keystore.jks -storepass changeit | grep -E "Alias name|chain length"

echo -e "${GREEN}✓ Certificate verification complete${NC}"
echo -e "${GREEN}======================================"
echo "SSL certificates generated successfully!"
echo "======================================${NC}"
