package ir.isc.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OAuthKafkaConsumer {

    private static final String BOOTSTRAP_SERVERS = "localhost:9093";
    private static final String TOPIC = "test-oauth-topic";
    private static final String GROUP_ID = "test-consumer-group";

    // These must match your Keycloak client exactly
    private static final String CLIENT_ID = "kafka-consumer";
    private static final String CLIENT_SECRET = "zwPeaen9hnAtAblg3mCbaBH7nFSM52kJ";
    private static final String TOKEN_URL = "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token";

    public static void main(String[] args) {

        System.out.println("=================================================");
        System.out.println("KAFKA CONSUMER (FIXED OAUTH)");
        System.out.println("=================================================");

        // 1. Allow HTTP for Token Endpoint (Kafka 4.x requirement)
        System.setProperty(
                "org.apache.kafka.sasl.oauthbearer.allowed.urls",
                TOKEN_URL
        );

        Properties props = new Properties();
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
        // --- Core ---
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // --- Serialization ---
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // --- Security ---
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "OAUTHBEARER");

        // --- Strimzi OAuth Handler ---
        props.put("sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        // --- JAAS Config (The Fix) ---
        // We explicitly set 'oauth.username.claim' to "preferred_username" to match the broker.
        // We also set 'oauth.username' to ensure the token has a subject.
        props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                        "oauth.client.id=\"%s\" " +
                        "oauth.client.secret=\"%s\" " +
                        "oauth.token.endpoint.uri=\"%s\" " +
                        "oauth.scope=\"profile email\" " +
                        "oauth.username.claim=\"preferred_username\" " + // Matches broker config
                        "oauth.username=\"%s\" " +                      // Forces the subject value
                        "oauth.grant.type=\"client_credentials\";",     // Explicit grant type
                CLIENT_ID, CLIENT_SECRET, TOKEN_URL, CLIENT_ID
        ));

        // --- SSL ---
        props.put("ssl.truststore.location",
                "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/kafka-security/broker/kafka.server.truststore.jks");
        props.put("ssl.truststore.password", "changeit");
        props.put("ssl.endpoint.identification.algorithm", "");

        // Debug
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO"); // Changed to info to reduce noise, switch to debug if needed

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

            System.out.println("Subscribing to topic: " + TOPIC);
            consumer.subscribe(Collections.singletonList(TOPIC));

            // Wait for assignment
            System.out.println("Waiting for partition assignment...");
            while (consumer.assignment().isEmpty()) {
                consumer.poll(Duration.ofMillis(200));
            }

            consumer.seekToBeginning(consumer.assignment());
            System.out.println("✓ Assigned partitions: " + consumer.assignment());
            System.out.println("✓ Consuming messages...\n");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("✓ Message received:");
                    System.out.println("  Topic: " + record.topic());
                    System.out.println("  Partition: " + record.partition());
                    System.out.println("  Offset: " + record.offset());
                    System.out.println("  Key: " + record.key());
                    System.out.println("  Value: " + record.value());
                    System.out.println("--------------------------------------");
                }

                consumer.commitSync();
            }

        } catch (Exception e) {
            System.err.println("Consumer error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}