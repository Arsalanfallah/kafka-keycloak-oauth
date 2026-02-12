package ir.isc.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OAuthKafkaConsumer {

    private static final String BOOTSTRAP_SERVERS = "localhost:9093";
    private static final String TOPIC = "test-oauth-topic";
    private static final String GROUP_ID = "test-consumer-group";

    private static final String CLIENT_ID = "kafka-consumer";
    private static final String CLIENT_SECRET = <CLIENT_SECRET>;
    private static final String TOKEN_URL =
            "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token";

    public static void main(String[] args) {

        // Allow HTTP token endpoint
        System.setProperty(
                "org.apache.kafka.sasl.oauthbearer.allowed.urls",
                TOKEN_URL
        );

        Properties props = new Properties();

        // --- Core ---
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        // --- Serialization ---
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        // --- Security ---
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "OAUTHBEARER");

        props.put("sasl.login.callback.handler.class",
                "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

        props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                        "oauth.client.id=\"%s\" " +
                        "oauth.client.secret=\"%s\" " +
                        "oauth.token.endpoint.uri=\"%s\" " +
                        "oauth.grant.type=\"client_credentials\";",
                CLIENT_ID, CLIENT_SECRET, TOKEN_URL
        ));

        // --- SSL ---
        props.put("ssl.truststore.location",
                "/kafka-keycloak-oauth/" +
                        "kafka-security/broker/kafka.server.truststore.jks");
        props.put("ssl.truststore.password", "changeit");
        props.put("ssl.endpoint.identification.algorithm", "");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

            consumer.subscribe(Collections.singletonList(TOPIC));
            System.out.println("Consumer started (manual commit)");

            while (true) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofSeconds(1));

                for (ConsumerRecord<String, String> record : records) {
                    // ---- BUSINESS LOGIC HERE ----
                    System.out.printf(
                            "Topic=%s Partition=%d Offset=%d Key=%s Value=%s%n",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.key(),
                            record.value()
                    );
                }

                // ✅ Commit ONLY after successful processing
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
