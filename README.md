# Item aggregator component

This project illustrates combining Kafka Streams with reactive programming, reactive messaging with Quarkus.
The goal of the kafka streams implementation is to build a real time inventory view from items sold in different stores. The aggregates are kept in state store and expose via interactive queries.

The project is used as a Kafka Streams lab [documented here](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/lab-3/) with instructions to build it from the beginning.

Here is a simple diagram to illustrate the components used:

 ![1](https://github.com/ibm-cloud-architecture/refarch-eda/blob/master/docs/src/pages/use-cases/kafka-streams/lab-3/images/item-aggregator-ctx.png)

The goal of this note is to present how to run the solution locally using Strimzi Kafka image and two instances of the project.

You can use our docker image: [ibmcase/item-aggregator](https://hub.docker.com/repository/docker/ibmcase/item-aggregator) to demonstrate Kafka streams or to be integrated in a bigger end to end scenario with Kafka connect (See [this scenario](https://ibm-cloud-architecture.github.io/refarch-eda/scenarios/realtime-inventory/)).  

## Kafka Streams approach

Even if you do not want to build it yourself the approach to support the above use cases is to use the following Kafka elements:

* in-topic: items: contains items sold in store. The item object is defined in [domain/Item.java](domain/Item.java) class.
* out-topic: inventory: contains the item stock events.
* Ktable <itemID, count> with store. To keep total stock, cross stores per item
* Ktable <storeID, <itemID, count> with store. To keep store inventory
* Interactive query to get data from store and expose the result as reactive REST resource.

## In a hurry, just run it

* Start local Kafka: `docker-compose up` to start one kafka broker, and 2 instances of the service. 
* Be sure to have created the following topics on your Event Streams instance:
  * `items` topic with 3 partitions created
  * `inventory` topic with one partition

 ```shell
 # list existing topics
 docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.6.0 bash -c "/opt/kafka/bin/kafka-topics.sh  --bootstrap-server kafka:9092 --list"
 # If needed add neede topics:
  docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.6.0 bash -c "/opt/kafka/bin/kafka-topics.sh  --bootstrap-server kafka:9092 --create --topic inventory --partitions 1 --replication-factor 1"
  docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.6.0 bash -c "/opt/kafka/bin/kafka-topics.sh  --bootstrap-server kafka:9092 --create --topic items --partitions 3 --replication-factor 1"
 ```

* Verify each components runs well with `docker ps`, you should have 2 item-aggregators and 1 item producer.

## Demonstration script

Once started go to one of the Item Aggregator API: [http://localhost:8081/swagger-ui/](http://localhost:8081/swagger-ui/) and the /inventory/store/{storeID} end point. Using the `Store_1` storeID you should get an empty response.

* Send some simulated records: `curl -X POST http://localhost:8082/start -d '20'`
* Verify the store inventory is updated: `curl -X GET "http://localhost:8081/inventory/store/Store_1" -H  "accept: application/json"`
* Verify messages are sent to inventory topic by:

 ```shell 
 docker run -ti --network kafkanet strimzi/kafka:latest-kafka-2.6.0 bash
 # then in the shell 
 ./bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic inventory --from-beginning`
 ```

## Streaming approach

The approach is to get items stream and get the store name as new key and group by this new key.

```java
builder.stream(itemSoldTopicName, 
                        Consumed.with(Serdes.String(), itemSerde))
            // use store name as key
            .map((k,v) ->  new KeyValue<>(v.storeName, v))
            .groupByKey(Grouped.with(Serdes.String(),itemSerde))
```

If we stop here we not get the good result as each new item will erase the store record. We want to accumulate the stock per item per store. 

So we need an inventory object to keep this information:

```Java
public class Inventory {
    public String storeName;
    public HashMap<String,Long> stock = new HashMap<String,Long>();
    public Inventory(){}
}
```

So the operation to take this <storeName, item> record to inventory, and update existing inventory entry is the `aggregate` function:

```Java
.aggregate(
      () ->  new Inventory(), // initializer
      (k , newItem, currentInventory) 
            -> currentInventory.updateStockQuantity(k,newItem), 
      Materialized.<String,Inventory,KeyValueStore<Bytes,byte[]>>as(StoreInventoryAgent.STOCKS_STORE_NAME)
            .withKeySerde(Serdes.String())
            .withValueSerde(inventorySerde));
```

First row is to initialize new key, record with an empty Inventory object. 
The second row is executed when a key is found (first key too), and update the currentInventory with the new quantity from the item. The outcome of this is a Ktable<storeName, Inventory> 
The content is materialized in a store.

The update operation on the inventory is one key of the solution:

```Java
public Inventory updateStockQuantity(String k, Item newValue) {
        this.storeName = k;
        if (newValue.type.equals("SALE")) 
            newValue.quantity=-newValue.quantity;
        return this.updateStock(newValue.sku,newValue.quantity);
    }

    public Inventory updateStock(String sku, long newV) {
        if (stock.get(sku) == null) {
            stock.put(sku, Long.valueOf(newV));
        } else {
            Long currentValue = stock.get(sku);
            stock.put(sku, Long.valueOf(newV) + currentValue );
        }
        return this;
    }
```

Finally the KTable is streamed out to the inventory topic:

```Java
inventory.toStream()
            .to(inventoryStockTopicName,
                Produced.with(Serdes.String(),inventorySerde));
      
```

The KTable is also materialized as a store that can be accessed via an API like `/inventory/store/{storeid}/{itemid}` using interactive query.

As items topic can be partitioned, a REST call may not reach the good end points, as the local store may not have the expected queried key. So the code is using interactive query to get access to the local state stores or return a URL of a remote store where the records for the given key are.

## Build it yourself

To build it yourself we have documented a [separate tutorial here](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/lab-3/).

## End to end testing

The integration tests use Python scripts. We have a custom python docker images (ibmcase/python37) with the necessary Kafka and pandas libraries to execute the tests.

* under e2e folder get the Event Streams certificate in `pem format` from Event Streams 'connect to this cluster` and save it under `e2e` folder.

* Start the python environment to send 2 items. Under `e2e` folder, execute following command to start the python environment connected to the docker network where Kafka is running:

```shell
# if not done set the env variables
source .env
docker run -v $(pwd)/e2e:/home -e KAFKA_BROKERS=$KAFKA_BROKERS \
   -e KAFKA_USER=$KAFKA_USER -e KAFKA_PASSWORD=$KAFKA_PASSWORD \
   -e KAFKA_CERT_PATH=/home/es-cert.pem \
      -ti ibmcase/python37 bash
```

In the shell run the item producer: `python ItemProducer.py`

```shell
root@docker-desktop:/home# python ItemProducer.py
Start Item Sold Event Producer
INFO:root:--- This is the configuration for the producer: ---
INFO:root:[KafkaProducer] - {'bootstrap.servers': 'Kafka:9092', 'group.id': 'ItemSoldProducer-1', 'delivery.timeout.ms': 15000, 'request.timeout.ms': 15000}
INFO:root:---------------------------------------------------
INFO:root:Send {"storeName": "Store-1", "itemCode": "Item-2", "type": "RESTOCK", "quantity": 5} with key itemCode to items
INFO.. - Message delivered to items [0]
INFO:root:Send {"storeName": "Store-1", "itemCode": "Item-2", "type": "SALE", "quantity": 2, "price": 10.0} with key itemCode to items
INFO.. - Message delivered to items [0]
```

* After these two records are published, we can validate the Event Streams console:
  * The consumer groups has 3 active members as there are three Kafka stream tasks running.
  * One of the task has processed the partition 1 where e2 records were sent.  
  * The inventory topic has 2 records published.
* Using the REST api we can see the current stock for the store `Store-1` and the item


```shell
curl http://localhost:8080/inventory/store/Store-1/Item-2

# should get a result like:
{
  "stock": {
    "Item-2": 3
  },
  "storeName": "Store-1"
}
```

The API is visible via the swagger-ui: `http://localhost:8080/swagger-ui/`

## Deploy on OpenShift cluster with Event Streams

* Select one of the existing Kafka users with TLS authentication or create a new one from the Event Streams console, with the produce, consume messages and create topic and schemas authorizations, on all topics or topic with a specific prefix, on all consumer groups or again with a specific prefix, all transaction IDs.

```shell
# if not logged yes to your openshift cluster where the docker private registry resides do:
oc login --token=... --server=https://c...
oc get kafkausers -n eventstreams
```

We use a user with TLS authentication named: `tls-user`

* Copy user's secret to the current project where the application will run

```shell
oc get secret  tls-user -n eventstreams --export -o yaml | oc apply -f -
```

* Copy the server side TLS certificate to your project:

```shell
oc get secret  sandbox-cluster-ca-cert -n eventstreams --export -o yaml | oc apply -f -
```


* Build and push the image to public registry

```shell
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t ibmcase/item-aggregator:0.0.2 .
docker push ibmcase/item-aggregator:0.0.2
# build with s2i and push the image to private registry
./mvnw clean package -DQuarkus.kubernetes.deploy=true
```


## Running distributed mode on your latop with docker-compose
```shell
# runs three insance of your applicaiton with load balancer using nginx. this will be taken care by routes, services on openshift
docker-compose up --scale item-intentory=3
```
* Using the REST api we can see the current stock for the store `Store-1` and the item


```shell
curl http://localhost:4000/inventory/store/Store-1

# should get a result like:
{
  "stock": {
    "Item-2": 3
  },
  "storeName": "Store-1"
}
```