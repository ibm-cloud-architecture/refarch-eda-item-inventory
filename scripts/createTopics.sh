docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.5.0 bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create  --replication-factor 1 --partitions 2 --topic items"

docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.5.0 bash -c "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create  --replication-factor 1 --partitions 1 --topic inventory"
