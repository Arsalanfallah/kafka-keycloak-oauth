Step 1: Ensure broker and internal topics are ready
## ****************** Check that your broker is running ************************:

* docker exec -it kafka-broker sh
Export the JAAS config (THIS IS THE KEY STEP)

* 
cat << 'EOF' > /opt/kafka/config/admin-jaas.conf
  KafkaClient {
  org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required
  oauth.client.id="kafka-broker"
  oauth.client.secret="HlMg4MB460S6hwiirIjqqg0Fo47dhmpw"
  oauth.token.endpoint.uri="http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token"
  oauth.scope="openid";
};
EOF

*  
export KAFKA_OPTS="-Djava.security.auth.login.config=/opt/kafka/config/admin-jaas.conf \
  -Dorg.apache.kafka.sasl.oauthbearer.allowed.urls=http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token"


* 
cat <<'EOF' >  /opt/kafka/config/client.properties 
security.protocol=SASL_SSL
sasl.mechanism=OAUTHBEARER
sasl.login.callback.handler.class=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler
sasl.oauthbearer.token.endpoint.url=http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token
sasl.oauthbearer.client.credentials.client.id=kafka-consumer
sasl.oauthbearer.client.credentials.client.secret=Fns8Sx2PC405psr3OFJkdCiQNiRXbwtn
ssl.truststore.location=/etc/kafka/secrets/kafka.server.truststore.jks
ssl.truststore.password=changeit
ssl.endpoint.identification.algorithm=
EOF


* 
 /opt/kafka/bin/kafka-broker-api-versions.sh \
--bootstrap-server kafka-broker:9093 \
--command-config /opt/kafka/config/client.properties

You should see broker 1 responding.

If you see METADATA responses for topics, broker is ready.
## ******************Check internal topics:***********************

/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka-broker:9093 \
--list \
--command-config /opt/kafka/config/client.properties

## You must see 
   __consumer_offsets
   test-oauth-topic
🚨 No __consumer_offsets

That means:
 Kafka still cannot create internal topics

## ***********************Set token Timout in keycloak *************
In Keycloak:
Realm → Tokens → Access Token Lifespan
Set to: 15m or 30m
Kafka + OAuth is happiest ≥ 10 minutes.


## ************************* Produce data right now ******************
/opt/kafka/bin/kafka-console-producer.sh \
--bootstrap-server kafka-broker:9093 \
--topic test-oauth-topic \
--producer.config /opt/kafka/config/client.properties
Type:
hello
oauth
works

Then consume with a new group:
/opt/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server kafka-broker:9093 \
--topic test-oauth-topic \
--group fresh-group-1 \
--from-beginning \
--consumer.config /opt/kafka/config/client.properties

## ************************* List all consumer groups (sanity check) *******
/opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server kafka-broker:9093 \
--list \
--command-config /opt/kafka/config/client.properties


Describe a specific group (THIS is the key command)

/opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server kafka-broker:9093 \
--group bootstrap-group \
--describe \
--command-config /opt/kafka/config/client.properties


## ******************  Check if the topic actually has data ***************
/opt/kafka/bin/kafka-run-class.sh \
kafka.tools.GetOffsetShell \
--broker-list kafka-broker:9093 \
--topic test-oauth-topic \
--time -1 \
--command-config /opt/kafka/config/client.properties
Example
test-oauth-topic:0:5
👉 Topic has 5 messages
If you see :0, the topic is empty.

## **********************  Reset offsets (safe & explicit) **************
Dry run first

/opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server kafka-broker:9093 \
--group bootstrap-group \
--topic test-oauth-topic \
--reset-offsets \
--to-earliest \
--dry-run \
--command-config /opt/kafka/config/client.properties

Execute

/opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server kafka-broker:9093 \
--group bootstrap-group \
--topic test-oauth-topic \
--reset-offsets \
--to-earliest \
--execute \
--command-config /opt/kafka/config/client.properties

## ***************** Consume again (no timeout) **********************
/opt/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server kafka-broker:9093 \
--topic test-oauth-topic \
--group bootstrap-group \
--from-beginning \
--consumer.config /opt/kafka/config/client.properties
## *******************88 If offsets still don’t move (rare) *********
Run while consumer is active:
/opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server kafka-broker:9093 \
--group bootstrap-group \
--describe \
--command-config /opt/kafka/config/client.properties

You should see:
CONSUMER-ID
HOST
CLIENT-ID

If empty → consumer never joined the group.