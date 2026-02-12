#!/usr/bin/env bash
set -e

BOOTSTRAP_SERVER="kafka-broker:9093"
CONFIG="/tmp/client.properties"
JAAS_CONFIG="/tmp/admin-jaas.conf"
TOKEN_URL="http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token"

TOPIC="test-oauth-topic"
CONSUMER_GROUP="test-consumer-group"

PRODUCER_PRINCIPAL="User:service-account-kafka-producer"
CONSUMER_PRINCIPAL="User:service-account-kafka-consumer"
BROKER_PRINCIPAL="User:service-account-kafka-broker"
echo "🚀 Bootstrapping Kafka ACLs..."

export KAFKA_OPTS="-Djava.security.auth.login.config="/tmp/admin-jaas.conf" \
  -Dorg.apache.kafka.sasl.oauthbearer.allowed.urls=http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token"

## ** Run this once (as admin):
/opt/kafka/bin/kafka-acls.sh \
--bootstrap-server "$BOOTSTRAP_SERVER" \
--command-config "$CONFIG" \
--add \
--allow-principal "$BROKER_PRINCIPAL" \
--operation READ \
--operation WRITE \
--operation DESCRIBE \
--topic __consumer_offsets

# ---- Topic: Producer ----
/opt/kafka/bin/kafka-acls.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --command-config "$CONFIG" \
  --add \
  --allow-principal "$PRODUCER_PRINCIPAL" \
  --operation WRITE \
  --operation CREATE \
  --topic "$TOPIC"

# ---- Topic: Consumer ----
/opt/kafka/bin/kafka-acls.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --command-config "$CONFIG" \
  --add \
  --allow-principal "$CONSUMER_PRINCIPAL" \
  --operation READ \
  --topic "$TOPIC"

# ---- Consumer Group  to join / fetch offsets----
/opt/kafka/bin/kafka-acls.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --command-config "$CONFIG" \
  --add \
  --allow-principal "$CONSUMER_PRINCIPAL" \
  --operation READ \
  --group "$CONSUMER_GROUP"
# ---- Consumer Group  to discover & coordinate the group
/opt/kafka/bin/kafka-acls.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --command-config "$CONFIG" \
  --add \
  --allow-principal "$CONSUMER_PRINCIPAL" \
  --operation DESCRIBE \
  --group "$CONSUMER_GROUP"



echo "✅ ACL bootstrap completed successfully"
