echo "######################"
echo " List Topics"

docker exec -ti refarch-eda-item-inventory_kafka_1 bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"

echo "######################"
echo " create Topics"


docker  exec -ti refarch-eda-item-inventory_kafka_1 bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create  --replication-factor 1 --partitions 1 --topic items"

docker  exec -ti refarch-eda-item-inventory_kafka_1 bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create  --replication-factor 1 --partitions 1 --topic inventory"
