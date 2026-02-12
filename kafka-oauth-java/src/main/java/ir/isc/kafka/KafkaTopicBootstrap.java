package ir.isc.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * @author arsaln-fallah
 * Company:ISC
 * @date 2/12/26 11:50 AM
 */


public class KafkaTopicBootstrap {

    private static final String BOOTSTRAP_SERVERS = "kafka-broker:9093";

    private static final String CLIENT_ID = "kafka-broker";
    private static final String CLIENT_SECRET = <CLIENT_SECRET>;
    private static final String TOKEN_URL =
            "http://keycloak:8080/realms/kafka-realm/protocol/openid-connect/token";

    public static void main(String[] args) throws Exception {

        System.out.println("=== Kafka Topic Bootstrap ===");

        Properties props = new Properties();

        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
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

        props.put("ssl.truststore.location",
                "/home/arsaln-fallah/projects/Borna Connect/kafka-keycloak-oauth/" +
                        "kafka-security/broker/kafka.server.truststore.jks");
        props.put("ssl.truststore.password", "*****");
        props.put("ssl.endpoint.identification.algorithm", "");

        try (AdminClient admin = AdminClient.create(props)) {

            String topicName = "test-oauth-topic";
            int partitions = 3;
            short replicationFactor = 1;

            Set<String> existingTopics = admin.listTopics().names().get();

            if (existingTopics.contains(topicName)) {
                System.out.println("✓ Topic already exists: " + topicName);
            } else {
                NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
                admin.createTopics(Collections.singleton(newTopic)).all().get();
                System.out.println("✓ Topic created: " + topicName);
            }
        }

        System.out.println("=== Bootstrap finished ===");
    }
}

