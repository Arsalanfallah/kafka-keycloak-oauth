package ir.isc.kafka;
/**
 * @author arsaln-fallah
 * Company:ISC
 * @date 2/3/26 3:00 PM
 */
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

public class OAuthKafkaProducer {

    private static final String BOOTSTRAP_SERVERS = "kafka-broker:9093";
    private static final String TOPIC = "test-oauth-topic";

    private static final String CLIENT_ID = "kafka-producer";
    private static final String CLIENT_SECRET = "qrhol4MD6wAoBWCfRPyeHcxw5vI425rV";
    private static final String TOKEN_URL = "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token";

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("KAFKA PRODUCER OAUTH AUTHENTICATION TEST");
        System.out.println("============================================================");

        // ⚠️ Kafka 4.x requires explicit allowed URLs for OAuth token endpoint
        System.setProperty(
                "org.apache.kafka.sasl.oauthbearer.allowed.urls",
                TOKEN_URL
        );
        System.setProperty("java.security.auth.login.config",
                "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/kafka-oauth-java/kafka_client_jaas.conf");

        Properties props = setProperties();

        // 1️⃣ Create topic if it doesn't exist
        createTopicIfNotExists(TOPIC,props);

        KafkaProducer<String, String> producer = new KafkaProducer<>(props,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.StringSerializer());

        System.out.println("✓ Producer created successfully");

        // 3️⃣ Send test messages
        ObjectMapper mapper = new ObjectMapper();
        int messageCount = 5;

        for (int i = 0; i < messageCount; i++) {
            Map<String, Object> message = new HashMap<>();
            message.put("message_id", i);
            message.put("timestamp", Instant.now().getEpochSecond());
            message.put("content", "Test message " + i + " with OAuth authentication");
            message.put("producer_client", CLIENT_ID);

            String jsonMessage = mapper.writeValueAsString(message);

            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "key-" + i, jsonMessage);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.out.println("✗ Message delivery failed: " + exception.getMessage());
                } else {
                    System.out.printf("✓ Message delivered to %s [%d] @ offset %d%n",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            // Poll to trigger callbacks
            producer.flush();
        }

        producer.close();
        System.out.println("✓ All messages sent successfully");
    }

    private static void createTopicIfNotExists(String topicName,Properties properties) throws ExecutionException, InterruptedException {

        try (AdminClient admin = AdminClient.create(properties)) {
            Set<String> topics = admin.listTopics().names().get();
            if (topics.contains(topicName)) {
                System.out.println("✓ Topic '" + topicName + "' already exists");
                return;
            }

            NewTopic newTopic = new NewTopic(topicName, 3, (short) 1);
            admin.createTopics(Collections.singleton(newTopic)).all().get();
            System.out.println("✓ Topic '" + topicName + "' created successfully");
        }
    }
    private static Properties setProperties(){
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);

        // SASL + OAuth
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "OAUTHBEARER");
        props.put("sasl.login.callback.handler.class",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler");

        // OAuth endpoint & credentials
        props.put("sasl.oauthbearer.token.endpoint.url",
                TOKEN_URL);
        props.put("sasl.oauthbearer.client.credentials.client.id","kafka-producer");
        props.put("sasl.oauthbearer.client.credentials.client.secret",CLIENT_SECRET);
        props.put("sasl.oauthbearer.client.id", "kafka-producer");
        props.put("sasl.oauthbearer.client.secret", CLIENT_SECRET);
        props.put("sasl.oauthbearer.scope", "profile email");


        // Disable SSL verification for testing
        props.put("ssl.endpoint.identification.algorithm", "");
        props.put("ssl.truststore.location", "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/kafka-security/broker/kafka.server.truststore.jks");
        props.put("ssl.truststore.password", "changeit");
        props.put("ssl.truststore.type", "JKS");
        props.put("ssl.trustmanager.algorithm", "PKIX");

        // Producer configs
        props.put("acks", "all");
        props.put("enable.idempotence", "true");

        return props;
    }
}
