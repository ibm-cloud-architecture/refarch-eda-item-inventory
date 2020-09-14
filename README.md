# Item aggregator component

This project illustrates combining Kafka streams with reactive programming, reactive messaging with Quarkus.
The use case is around getting item sold events and build a real time inventory in kafka streams exposed via REST on top of sequential queries. 

The project can be used as a lab to build the following features:

* create a Quarkus app using Microprofile reactive messaging to consume items sold in stores
* aggregate store id -> item id -> item sold count
* aggregate item id -> total sold so far
* generate events on inventory topic using storeID -> [items] in stock

or can be used as-is from the docker image: [ibmcase/item-aggregator](https://hub.docker.com/repository/docker/ibmcase/item-aggregator) to demonstrate Kafka streams or to be integrated in a bigger end to end scenario with Kafka connect (See [this scenario](https://ibm-cloud-architecture.github.io/refarch-eda/scenarios/realtime-inventory/)).  

## Kafka Streams approach

Even if you do not want to build it yourself the approach to support the above use cases is to use the following Kafka elements:

* in-topic: items: contains items sold in store. The item object is defined in [domain/Item.java](domain/Item.java) class.
* out-topic: inventory: contains the item stock events.
* Ktable <itemID, count> with store. To keep total stock, cross stores per item
* Ktable <storeID, <itemID, count> with store. To keep store inventory
* Interactive query to get data from store and expose the result as reactive REST resource.

## In a hurry, just run it

* Connect to IBM Event Streams via CLI: It is deployed on OpenShift under the `eventstreams` project:

  ```shell
  oc login
  cloudctl es init

  Select an instance:
  1. minimal-prod ( Namespace:eventstreams )
  2. sandbox-rp ( Namespace:eventstreams )
  Enter a number> 1
  ```

  Get the `Event Streams bootstrap external address` from the output of previous command and update the KAFKA_BROKERS variable in `.env` file in this repository.

* Be sure to have created the following topics on your Event Streams instance:
  * `items` topic with 3 partitions created
  * `inventory` topic with one partition

  ```shell
 cloudctl es topic-create --name items --partitions 3 --replication-factor 3
 cloudctl es topic-create --name inventory --partitions 1 --replication-factor 3
 cloudctl es topics
 ```

* Get the scram user credentials and TLS certificate from Event Streams on OpenShift.  See the [note here](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/overview/pre-requisites#getting-tls-authentication-from-event-streams-on-openshift)

  Modify KAFKA_CERT_PWD in the `.env` file.
* Update a .env file with the environment variables:

```
KAFKA_BROKERS=...-kafka-bootstrap-eventstreams.....containers.appdomain.cloud:443
KAFKA_USER=app-demo
KAFKA_CERT_PATH=${PWD}/certs/es-cert.p12
KAFKA_CERT_PWD=
# only if you use a TLS user
# USER_CERT_PATH=${PWD}/certs/user.p12
# USER_CERT_PWD=
```

Start the app in dev mode after doing the `source .env` command to set environment variables.

```shell
source .env
./mvnw quarkus:dev
```

The application should be connected to Kafka and get visibility to the items and inventory topics. Next step is to send some items using [end to end](#end-to-end-testing) testing.

## Streaming approach

The approach is to get items stream and get the store name as new key and group by this new key.

```
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

* under e2e folder get the Event Streams certificate in pem format:

```shell
cloudctl es certificates --format pem > e2e/es-cert.pem
```

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

* After these two records published we can validate the Event Streams console:
  * The consumer groups has 3 active members as there are three Kafka stream tasks running.
  * One of the task has processed the partition 1 where e2 records were sent.  
  * The inventory topic has 2 records published.
* Using the REST api we can see the current stock for the store `Store-1` and the item

```shell
curl http://localhost:8080/inventory/store/Store-1/Item-2

# should get a result like:
{
  "stock": {
    "Item-2": 7
  },
  "storeName": "Store-1"
}
```

The API is visible via the swagger-ui: `http://localhost:8080/swagger-ui/`

## Deploy on OpenShift cluster with Event Streams

* Select one of the Kafka users with TLS authentication defined or create a new one with the produce, consume messages and create topic and schemas authorizations, on all topics or topic with a specific prefix, on all consumer groups or again with a specific prefix, all transaction IDs.

 ```shell
 # if not logged yes to your openshift cluster where the docker private registry resides do:
oc login --token=... --server=https://c...
 oc get kafkausers -n eventstreams
 ```

We use a user with TLS authentication named: ` tls-user`

* Copy user's secret to the current project where the application will run

```shell
oc get secret  tls-user -n eventstreams --export -o yaml | oc apply -f -
```

* Define config map for Kafka broker URL and user name: update the file [src/main/kubernetes/configmap.yaml]()

```
oc apply -f src/main/kubernetes/configmap.yaml
```

* Build and push the image to public registry

```shell
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t ibmcase/item-aggregator:0.0.2 .
docker push ibmcase/item-aggregator:0.0.2
# build with s2i and push the image to private registry
./mvnw clean package -DQuarkus.kubernetes.deploy=true
```
