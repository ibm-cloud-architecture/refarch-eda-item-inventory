echo "######################"
echo " Verify inventory topic content"


docker  exec -ti kafka bash -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic item.inventory --from-beginning"