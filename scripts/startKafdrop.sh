source .env
sed 's/KAFKA_USER/'$KAFKA_USER'/g' ./scripts/kafka.properties > ./scripts/output.properties
sed -i '' 's/KAFKA_PASSWORD/'$KAFKA_PASSWORD'/g' ./scripts/output.properties
sed -i '' 's/KAFKA_CERT_PWD/'$KAFKA_CERT_PWD'/g' ./scripts/output.properties
docker run --rm -p 9000:9000 \
    --name kafdrop \
    -v $(pwd)/certs:/home/certs \
    -e KAFKA_BROKERCONNECT=$KAFKA_BROKERS \
    -e KAFKA_PROPERTIES=$(cat ./scripts/output.properties | base64) \
    -e JVM_OPTS="-Xms32M -Xmx64M" \
    -e SERVER_SERVLET_CONTEXTPATH="/" \
    obsidiandynamics/kafdrop