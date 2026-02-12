package ir.isc.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class QuickOAuthTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9093";
    private static final String TOPIC = "test-oauth-topic";

    private static final String CLIENT_ID = "kafka-producer";
    private static final String CLIENT_SECRET = <CLIENT_SECRET>;
    private static final String TOKEN_URL =
            "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token";

    public static void main(String[] args) throws Exception {

        System.out.println("========================================");
        System.out.println("KAFKA OAUTH PRODUCER (CLEAN)");
        System.out.println("========================================");

        // Allow HTTP token endpoint
        System.setProperty(
                "org.apache.kafka.sasl.oauthbearer.allowed.urls",
                TOKEN_URL
        );

        Properties props = new Properties();

        // --- Core ---
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // --- Serialization ---
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());

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
                "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/" +
                        "kafka-security/broker/kafka.server.truststore.jks");
        props.put("ssl.truststore.password", "*******");
        props.put("ssl.endpoint.identification.algorithm", "");

        KafkaProducer<String, String> producer =
                new KafkaProducer<>(props);

        System.out.println("✓ Producer created");

        AtomicInteger deliveredCount = new AtomicInteger(0);
        ObjectMapper mapper = new ObjectMapper();

        for (int i = 0; i < 3; i++) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("message", "OAuth message " + i);
            msg.put("timestamp", System.currentTimeMillis());

            String json = mapper.writeValueAsString(msg);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(TOPIC, json);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("✗ Send failed: " + exception.getMessage());
                } else {
                    int count = deliveredCount.incrementAndGet();
                    System.out.printf(
                            "✓ Message %d → %s [%d] @ offset %d%n",
                            count,
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset()
                    );
                }
            });
        }

        producer.flush();
        producer.close();

        System.out.println("========================================");
        System.out.printf("✓ Messages sent: %d%n", deliveredCount.get());
        System.out.println("========================================");
    }
}
