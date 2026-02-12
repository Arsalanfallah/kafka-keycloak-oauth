1-
docker compose up keycloak
2-
./scripts/setup-keycloak.sh
./scripts/fix-audience.sh
3- copy secret to ./kafka-config/kraft-config.properties
4-
./scripts/init-kafka.sh
5-
docker-compose up kafka-broker

6-
copy broker secret to ./kafka-config/client.properties
copy broker secret to ./kafka-config/admin-jaas.conf
7-
docker exec -it kafka-broker sh
8-
/tmp/bootstrap-acls.sh
9- Run java class KafkaTopicBootstrap into path
./kafka-oauth-java/src/main/java/ir/isc/kafka/KafkaTopicBootstrap.java

