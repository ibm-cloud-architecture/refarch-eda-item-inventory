import logging
from kafka.KafkaProducer import KafkaProducer
import kafka.eventStreamsConfig as config

'''
Product some item sold event
'''
if __name__ == '__main__':
    print("Start Item Sold Event Producer")
    logging.basicConfig(level=logging.INFO)
    producer = KafkaProducer(kafka_brokers = config.KAFKA_BROKERS, 
                kafka_user = config.KAFKA_USER, 
                kafka_pwd = config.KAFKA_PASSWORD, 
                kafka_cacert = config.KAFKA_CERT_PATH, 
                kafka_sasl_mechanism=config.KAFKA_SASL_MECHANISM,
                topic_name = "items")
    producer.prepare("ItemSoldProducer-1")
    item = {'id': 1,'storeName': "Store-1",'sku': 'Item-2', 'type': 'RESTOCK', 'quantity': 5}
    producer.publishEvent(item,"sku")
    item = {'id': 2,'storeName': "Store-1",'sku': 'Item-2', 'type': 'SALE', 'quantity': 2, 'price': 10.0}
    producer.publishEvent(item,"sku")

  