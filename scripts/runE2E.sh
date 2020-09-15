source .env
docker run -v $(pwd)/e2e:/home -e KAFKA_BROKERS=$KAFKA_BROKERS \
   -e KAFKA_USER=$KAFKA_USER -e KAFKA_PASSWORD=$KAFKA_PASSWORD \
   -e KAFKA_CERT_PATH=/home/es-cert.pem \
      -ti ibmcase/python37 bash -c "python ItemProducer.py" 
