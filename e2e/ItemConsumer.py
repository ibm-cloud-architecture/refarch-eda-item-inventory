import logging
from kafka.KafkaConsumer import KafkaConsumer
import kafka.eventStreamsConfig as config

if __name__ == '__main__':
    print("Consume items to verify data")
    logging.basicConfig(level=logging.INFO)
    CONSUMERGROUP = "ItemConsumer-tracegroup-1"
    try:
        consumer = KafkaConsumer(
                kafka_brokers = config.KAFKA_BROKERS, 
                kafka_user = config.KAFKA_USER, 
                kafka_pwd = config.KAFKA_PASSWORD, 
                kafka_cacert = config.KAFKA_CERT_PATH, 
                kafka_sasl_mechanism=config.KAFKA_SASL_MECHANISM,
                topic_name = "items",
                autocommit = False,
                fromWhere = 'earliest')
        consumer.prepare(CONSUMERGROUP)
        consumer.pollEvents()
    except Exception as identifier:
        print(identifier)
        input('Press enter to continue')
        print("Thank you")
   