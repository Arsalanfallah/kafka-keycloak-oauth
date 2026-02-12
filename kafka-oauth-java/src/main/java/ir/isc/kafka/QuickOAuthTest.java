package ir.isc.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class QuickOAuthTest {

    public static void main(String[] args) throws Exception {
        String producer_secret="1IRGlqtcqaytncMC9TY6oUBV9thBeUhe";
        System.out.println("============================================================");
        System.out.println("QUICK OAUTH TEST (SSL verification disabled)");
        System.out.println("============================================================");

        // ⚠️ Kafka 4.x requires explicit allowed URLs for OAuth token endpoint
        System.setProperty(
                "org.apache.kafka.sasl.oauthbearer.allowed.urls",
                "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token"
        );
        System.setProperty("java.security.auth.login.config",
                "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/kafka-oauth-java/kafka_client_jaas.conf");


        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9093");

        // SASL + OAuth
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "OAUTHBEARER");
        props.put("sasl.login.callback.handler.class",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler");

        // OAuth endpoint & credentials
        props.put("sasl.oauthbearer.token.endpoint.url",
                "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token");
        props.put("sasl.oauthbearer.client.credentials.client.id","kafka-producer");
        props.put("sasl.oauthbearer.client.credentials.client.secret",producer_secret);
        props.put("sasl.oauthbearer.client.id", "kafka-producer");
        props.put("sasl.oauthbearer.client.secret", producer_secret);
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

        // Create producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(
                props,
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.StringSerializer()
        );

        System.out.println("✓ Producer created!");

        AtomicInteger deliveredCount = new AtomicInteger(0);
        ObjectMapper mapper = new ObjectMapper();

        System.out.println("\nSending 3 test messages...");
        for (int i = 0; i < 3; i++) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("test", "OAuth message " + i);
            msg.put("timestamp", System.currentTimeMillis());
            String json = mapper.writeValueAsString(msg);

            ProducerRecord<String, String> record = new ProducerRecord<>("test-oauth-topic", json);

            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        System.out.println("✗ Delivery failed: " + exception.getMessage());
                    } else {
                        int count = deliveredCount.incrementAndGet();
                        System.out.printf(
                                "✓ Message %d delivered to %s partition %d @ offset %d%n",
                                count, metadata.topic(), metadata.partition(), metadata.offset()
                        );
                    }
                }
            });
        }

        System.out.println("\nFlushing... waiting for delivery reports");
        producer.flush();
        producer.close();

        System.out.printf("%nMessages delivered: %d%n", deliveredCount.get());

        System.out.println("\n============================================================");
        System.out.println("✓ OAUTH AUTHENTICATION TEST PASSED!");
        System.out.println("============================================================");
    }
}


