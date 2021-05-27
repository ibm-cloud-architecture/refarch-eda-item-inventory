echo "######################"
echo " Verify items topic content"


docker  exec -ti kafka bash -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 --topic items --from-beginning"