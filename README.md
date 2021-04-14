# Item sold aggregator component

This project illustrates combining Kafka Streams with reactive programming, and reactive messaging with Quarkus.
The goal of the Kafka streams implementation is to build a real time inventory view from items sold in different stores. The aggregates are kept in state store and expose via interactive queries.

The project is used as a Kafka Streams lab [documented here](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/lab-3/) with instructions to deploy and run it on OpenShift.

Here is a simple diagram to illustrate the components used:

 ![1](https://github.com/ibm-cloud-architecture/refarch-eda/blob/master/docs/src/pages/use-cases/kafka-streams/lab-3/images/item-aggregator-ctx.png)

The goal of this note is to present how to run the solution locally using Strimzi Kafka image and instructions to build it from the beginning.

## Pre-requisites

For development purpose the following pre-requisites need to be installed on your working computer:

**Java**
- For the purposes of this lab we suggest Java 11+
- Quarkus 1.8.1

**Git client**

**Maven**
- Maven will be needed for bootstrapping our application from the command-line and running
our application.

**An IDE of your choice**
- Ideally an IDE that supports Quarkus (such as Visual Studio Code)

**Docker**

If you want to access the end solution clone the following git repository: `git clone https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory`.

## In a hurry, just run it locally

* Start local Kafka: `docker-compose -f docker-compose up -d` to start one Kafka broker, and two item-inventory service instances. 
* Created the `items` and `inventory` topics on your Kafka instance
 
 ```shell
 ./scripts/createTopics.sh 
######################
 create Topics
Created topic items.
Created topic inventory.
jerome-mac:refarch-eda-item-inventory jeromeboyer$ ./scripts/listTopics.sh 
######################
 List Topics
inventory
items
 ```

* Verify each components runs well with `docker ps`, you should have 2 item-aggregators and 1 item producer.

Then [see the demonstration](#demonstration-script) script section below.

## Developing the application

The requirements to address are:

- consume item sold events from the `items` topic. Item has SKU as unique key. Item event has store ID reference
- compute for each item its current stock cross stores
- compute the store's stock for each item
- generate inventory event for store - item - stock
- expose APIs to get stock for a store or for an item

### Setting up the Quarkus Application

We will bootstrap the Quarkus application with the following Maven command (See [Quarkus maven tooling guide](https://quarkus.io/guides/maven-tooling#project-creation) for more information), to create the Quarkus App, with JAXRS, OpenAPI and Mutiny for non-blocking processing:

```shell
mvn io.quarkus:quarkus-maven-plugin:1.13.1.Final:create \
    -DprojectGroupId=ibm.garage \
    -DprojectArtifactId=item-inventory \
    -Dextensions="resteasy-jsonb, quarkus-resteasy-mutiny,smallrye-health,quarkus-smallrye-openapi,openshift,kubernetes-config"
```

You can replace the `projectGroupId, projectArtifactId` fields as you would like, but to avoid confusion in the next sections try to keep the name of artifactId.

*Recall that is if you want to add a Quarkus extension, do something like: `./mvnw quarkus:add-extension -Dextensions="kafka"`*

### Start the dev mode

In the `item-inventory`, start Quarkus in development mode, and you should be able to continuously develop in your IDE while the application is running:

```shell
./mvnw quarkus:dev
```

Going to the URL [http://localhost:8080/q/swagger](http://localhost:8080/q/swagger-ui). The API is empty but health and open API are predefined due to Quarkus plugins. Health works too: [http://localhost:8080/q/health](http://localhost:8080/q/health) and finally the development user interface is accessible via [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/).

* Let add a simple JAXRS resource to manage the Inventory API `InventoryResource.java` under `src/main/java` folder, with the package name: `ibm.gse.eda.inventory.api`. (We try to adopt a domain driven design approach of code layer with api, domain and infrastructure to organize the code)

The following code uses JAXRS annotations to expose the API and [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/) to use event driven reactive programming:

```java
package ibm.gse.eda.inventory.api;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
@Path("/inventory")
public class InventoryResource {
    
    @GET
    @Path("/store/{storeID}")
    @Produces(MediaType.APPLICATION_JSON)
    public  Uni<JsonObject> getStock(@PathParam("storeID") String storeID) {
            JsonObject stock = new JsonObject("{\"name\": \"hello you\", \"id\": \"" + storeID + "\"}");
            return Uni.createFrom().item( stock);
    }
}
```

A refresh on [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui) should bring you a working API, that you can "try it out" by giving an arbitrary store name.

As any Quarkus Application, a lot happen in the configuration, so let add a minimum of configuration to the `application.properties` file

```properties
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
quarkus.log.console.level=INFO
quarkus.log.console.enable=true
quarkus.http.port=8080
quarkus.swagger-ui.always-include=true
quarkus.openshift.expose=true
```

To move from imperative programming to a more reactive approach, we are using Uni class from [Mutiny](https://smallrye.io/smallrye-mutiny/) to get our API being asynchronous non-blocking: Quarkus uses [Vert.x](https://vertx.io/) to support non-blocking IO programming model and Mutiny is another abstraction to manage mono or multi elements in a reactive way.

### Deploy to OpenShift using s2i

Before going too far in the development, let deploy this simple app to OpenShift using the source to image capability. We assume you are logged to the cluster via `oc login...`, and that you have created a project: `oc new-project kstreams-labs`.

The following command should package the application and create OpenShift manifests, build a docker images and push it to OpenShift Private registry.

```shell
./mvnw package -Dquarkus.kubernetes.deploy=true
```

It can take some seconds to build for the first time and deploy to OpenShift: `oc get pods -w` lets you see the build pods and the running app once the build is done. As we expose the application an OpenShift route was created. The url is visible at the end of the build output, something like:

`...The deployed application can be accessed at: http://item-inventory...`

But if you want to retrieve the exposed route at any time use the following command:

```shell
oc get routes item-inventory -o jsonpath='{.spec.host}'
```

Which you can combine to immediately test your end point

```shell
URL=$(oc get routes item-inventory -o jsonpath='{.spec.host}')
curl -X GET http://$URL/inventory/store/store_1
```

### Modify testing

Adopting a test driven development let modify the rename and modify the first test case created by Quarkus:

* Rename the GreetingResourceTest to InventoryResourceTest
* Modify the hamcrest assertion to get the json and test on the id

```java
public void testInventoryStoreEndpoint() {
        given()
          .when().get("/inventory/store/store_1")
          .then()
             .statusCode(200)
             .body("id", is("store_1")).extract().response();
    }
```

* If run this test within your IDE it should be successful and a run like `./mvnw verify` should also succeed

We will add more test soon.

### Add Github action

To support continuous integration, we have done a [workflow for git action](https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory/blob/master/.github/workflows/dockerbuild.yaml) to build the docker image and push it to a registry. We use [quay.io](https://quay.io) now.

To enable this git action we need to define five secrets in the github repository:

* DOCKER_IMAGE_NAME the image name to build. Here it is `item-inventory`
* DOCKER_USERNAME: user to access docker hub
* DOCKER_PASSWORD: and its password.
* DOCKER_REPOSITORY for example the organization we use is `ibmcase`
* DOCKER_REGISTRY set to `docker.io` or `quay.io`

To enable your project with git, recall to do something like:

```shell
git init
git remote add origin https://github.com/<your_git_account>/item-inventory.git
git add .
git commit -m "first commit"
git branch -M main
git push --set-upstream origin main
```

### Define the domain entities

Now we need to define our main business entities: item and inventory.

Under the `src/main/java/../domain` folder add the two classes representing the business entities we will be using:

```Java
package ibm.gse.eda.inventory.domain;
import java.time.LocalDateTime;

public class Item {
    public static String RESTOCK = "RESTOCK";
    public static String SALE = "SALE";
    public String storeName;
    public String sku;
    public int quantity;
    public String type;
    public Double price;
    public String timestamp;

    public Item(){}
}
```

This item will also being used for event structure on `items` topic. The type attribute is to specify if this is a sale event or a restock event.

The inventory per store includes a map of `item.sku` and `quantity`.

```Java
package ibm.gse.eda.inventory.domain;

import java.util.HashMap;
import java.util.Map;

public class Inventory {
    public String storeName;
    public HashMap<String,Long> stock = new HashMap<String,Long>();
    public Inventory(){}
}
```

As part of the logic we want to add methods in the Inventory class to update the quantity given an item. We start by adding unit tests to design and validate those methods.

Under the `src/test/main` we add:

```java
package ut;
public class TestInventoryLogic {

    @Test
    public void shouldUpdateItemSoldQuantityTo10(){
        Item i1 = new Item();
        i1.sku = " item_1";
        i1.type = Item.SALE;
        i1.quantity = 10;
        Inventory inventory = new Inventory();
        Inventory out = inventory.updateStockQuantity("Store_1",i1);
        Assertions.assertEquals(10,out.stock.get(i1.sku));
    }
}
```

now let add the two following methods to make this test successful

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

We can add a restock test

```java
 public void shouldUpdateItemRestockQuantityTo10(){
        Item i1 = new Item();
        i1.sku = " item_1";
        i1.type = Item.RESTOCK;
        i1.quantity = 10;
        Inventory inventory = new Inventory();
        Inventory out = inventory.updateStockQuantity("Store_1",i1);
        Assertions.assertEquals(10,out.stock.get(i1.sku));
    }
```

Modify the InventoryResource to return the inventory instead of JsonObject (we will connect interactive query later in this lab).

```java
public  Uni<Inventory> getStock(@PathParam("storeID") String storeID) {
        Inventory stock = new Inventory();
        stock.storeName = storeID;
        Item newItem = new Item();
        newItem.quantity = 10;
        newItem.sku="item-01";
        newItem.type = Item.RESTOCK;
        stock.updateStockQuantity(storeID, newItem);
        return Uni.createFrom().item( stock);
}
```

Modify the test of `InventoryResourceTest.testInventoryStoreEndpoint` as:

```java
given()
      .when().get("/inventory/store/store_1")
      .then()
      .statusCode(200)
      .body("storeName", is("store_1")).extract().response();
```

You should get a json document like the following:

```json
{"stock": {
    "item-01": 10
  },
  "storeName": "Store-1"
}
```

Now we are good with the REST end point. Lets add Kafka-streams logic.

### Add Kafka Streams and reactive messaging plugins

We need Kafka and Kafka streams plugins use the following commands to add extension:

```shell
./mvnw quarkus:add-extension -Dextensions="kafka-streams"
```

Since we will be using the Kafka Streams testing functionality we will need to edit the `pom.xml` to add
the dependency to our project. Open `pom.xml` and add the following.

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-streams-test-utils</artifactId>
    <version>2.7.0</version>
    <scope>test</scope>
</dependency>
```

Modify the properties to add kafka, kafka-streams and reactive messaging parameters as follow:

```properties
quarkus.kafka-streams.consumer.session.timeout.ms=7000
quarkus.kafka-streams.consumer.heartbeat.interval.ms=200
quarkus.kafka-streams.application-id=item-aggregator
quarkus.kafka-streams.topics=items,inventory

mp.messaging.incoming.item-channel.connector=smallrye-kafka
mp.messaging.incoming.item-channel.topic=items
mp.messaging.incoming.item-channel.group.id=item-aggregator
```

`mvnw verify` should be successful, if you do not set those properties you may encounter errors like:

```shell
One or more configuration errors have prevented the application from starting. The errors are:
  - SRCFG00011: Could not expand value quarkus.application.name in property quarkus.kafka-streams.application-id
  - SRCFG00014: Property quarkus.kafka-streams.topics is required but the value was not found or is empty
```

### Define an item deserializer

The item needs to be deserialized to a Item bean, so we add a new class `ItemDeserializer` under the 
`ibm.gse.eda.inventory.infrastructure` folder. 

```java
package ibm.gse.eda.inventory.infrastructure;

import ibm.gse.eda.inventory.domain.Item;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ItemDeserializer extends JsonbDeserializer<Item> {
    public ItemDeserializer(){
        // pass the class to the parent.
        super(Item.class);
    }
}
```

and add the declaration in the properties file (change the class name if needed) to specify how reactive messaging need to handle the key and the value deserialization:

```properties
mp.messaging.incoming.item-channel.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.item-channel.value.deserializer=ibm.gse.eda.inventory.infrastructure.ItemDeserializer
```

## Kafka Streams approach

As introduced in [other Kafka Streams labs](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/) we have to define a topology to process the items and compute aggregates. Below are the construct we can use:

* in-topic: items: contains items sold in store. The item object is defined in [domain/Item.java](domain/Item.java) class.
* out-topic: inventory: contains the item stock events.
* Ktable <itemID, count> with store. To keep total stock, cross stores per item
* Ktable <storeID, <itemID, count> with store. To keep store inventory
* Interactive query to get data from store and expose the result as reactive REST resource.


The approach is to get item stream, extract the store name as new key and group by this new key. We add the `StoreInventoryAgent` class under the domain folder. We want to apply a domain driven design implementation approach using domain classes to represent the business logic and code expressed with ubiquituous language. The proposed implementation almost reach this language, adding only into the vocabulary the concept of streams and table in the form of KStream and KTable. We could have avoid that but it will not bring that much value for the implementation. Stream and tables are clear enough terms to be understood by business analysts.

The `StoreInventoryAgent` class processes items to generate inventory view per store. Here is a squeleton

```java
package ibm.gse.eda.inventory.domain;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StoreInventoryAgent {
    
     public Topology processItemStream(){
       // get item as Kstreams
       // process items and aggregate at the store level 
       // use store name as key
       // update the current stock for this store - item pair
       // change the value type
       // produce inventory records
     }
}
```

We will as usual starts by the tests, so we create a unit test, call TestInventoryTopology.java.

```java
public class TestInventoryTopology {
    private static TopologyTestDriver testDriver;
    private TestInputTopic<String, Item> inputTopic;
    private TestOutputTopic<String, Inventory> inventoryOutputTopic;

    @Test
    public void shouldGetInventoryUpdatedQuantity(){
        //given an item is sold in a store
        Item item = new Item("Store-1","Item-1",Item.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);        
        item = new Item("Store-1","Item-1",Item.SALE,2,33.2);
        inputTopic.pipeInput(item.storeName, item);

        Assertions.assertFalse(inventoryOutputTopic.isEmpty()); 
        Assertions.assertEquals(5, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(3, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
    }
}
```


Do not use `@QuarkusTest` in the test class to avoid loading the application.properties. 

We need to add this constructor to the Item class:

```java
public Item(String store, String sku, String type, int quantity, double price) {
                this.storeName = store;
                this.sku = sku;
                this.type = type;
                this.quantity = quantity;
                this.price = price;
                this.timestamp = LocalDateTime.now().toString();
        }
```

This test will fail with NPE. We need to add test driver, with input and output topic and topology. So let add a setup

```java
@BeforeEach
    public void setup() { 
        Topology topology = null;
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic("itemSoldInputStreamName", 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        inventoryOutputTopic = testDriver.createOutputTopic("inventoryStockOutputStreamName", 
                                stringSerde.deserializer(), 
                                inventorySerde.deserializer());
    }

    @AfterEach
    public void tearDown() {
        try {
            testDriver.close();
        } catch (final Exception e) {
             System.out.println("Ignoring exception, test failing due this exception:" + e.getLocalizedMessage());
        } 
    }
```

The stream processing needs SerDes and configuration so let declare those elements:

```java
    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<Item> itemSerde = new JsonbSerde<>(Item.class);
    private JsonbSerde<Inventory> inventorySerde = new JsonbSerde<>(Inventory.class);
```

Add the Stream configuration within a method:

```java
public  Properties getStreamsConfig() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stock-aggregator");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummmy:1234");
        return props;
    }
```

We should not have any compilation error but there is no topology define. So let work back on the Agent class to define the topology.

The test class will now have the agent declare and the the topology is the output of the processItemStream() method:

```java
 private StoreInventoryAgent agent = new StoreInventoryAgent();  

  public void setup() {
      Topology topology = agent.processItemStream();
```

In the StoreInventoryAgent.java class let add the following method. We will explain the logic later:

```java
public Topology processItemStream(){
        KStream<String,Item> items = itemStream.getItemStreams();     
        KTable<String,Inventory> inventory = items
            .groupByKey(ItemStream.buildGroupDefinition())
            .aggregate(
                () ->  new Inventory(), // initializer
                (k , newItem, existingInventory) 
                    -> existingInventory.updateStockQuantity(k,newItem), 
                    InventoryAggregate.materializeAsInventoryStore());       
       
        return itemStream.run();
    }
```

To separate the Kafka plumbing from the business methods the code for Kafka streams will be the `infrastructure` package. So let add the `ItemStream` class to get the items as KStreams from the `items` Kafka topic:

```java
package ibm.gse.eda.inventory.infrastructure;
@ApplicationScoped
public class ItemStream {
    @Inject
    @ConfigProperty(name="items.topic")
    public String itemSoldInputStreamName;
    
    private static JsonbSerde<Item> itemSerde = new JsonbSerde<>(Item.class);
    public StreamsBuilder builder;
      
    public ItemStream(){
        builder = new StreamsBuilder();
    }

    public KStream<String,Item> getItemStreams(){
        return builder.stream(itemSoldInputStreamName, 
                        Consumed.with(Serdes.String(), itemSerde));
    }

    public static Grouped<String, Item> buildGroupDefinition() {
		  return Grouped.with(Serdes.String(),itemSerde);
    }
}
```

This class uses the StreamBuilder from KafkaStreams to build a KStream from the `items` topic with item records. The key is the store name as a String. To build aggregate we first need to group records on a key, this is the goal of Grouped class and groupedByKey() method. Once we have such group we can compute aggregate. So the code is using the Inventory to keep state of the store inventory and compute it via the `aggregate` method. The first argument is when a new key (store) is added, so we need a new Inventory, the second argument is the method to update existing aggregate (inventory instance) with the new record received with the existing key. The last argument is to specify how to materialize this Ktable, (the aggregate generates a KTable). 

Let add the run method in the ItemStream to return the topology and run it.

```java
public Topology run() {
		return builder.build();
}
```

The topic name are injected from the application properties:

```
inventory.topic=inventory
items.topic=items
```

Add the Beans injection in the `StoreInventoryAgent` class:

```java
public class StoreInventoryAgent {
    @Inject
    public ItemStream itemStream;

    @Inject
    public InventoryAggregate inventoryAggregate;
```

So we need our last class to keep InventoryAggregate

```java
@ApplicationScoped
public class InventoryAggregate {

    @Inject
    @ConfigProperty(name = "inventory.topic")
    public String inventoryStockOutputStreamName;

    public static String INVENTORY_STORE_NAME = "StoreInventoryStock";

    private static JsonbSerde<Inventory> inventorySerde = new JsonbSerde<>(Inventory.class);
    
    /**
     * Create a key value store named INVENTORY_STORE_NAME to persist store inventory
     * @return
     */
    public static Materialized<String, Inventory, KeyValueStore<Bytes, byte[]>> materializeAsInventoryStore() {
        return Materialized.<String, Inventory, KeyValueStore<Bytes, byte[]>>as(INVENTORY_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(inventorySerde);
    }
}
```

We should have enough to run our test without NPE, if we complement the test class by modifying the setup() method:

```java
@BeforeEach
    public void setup() {
        // as no CDI is used set the topic names
        agent.itemStream = new ItemStream();
        agent.itemStream.itemSoldInputStreamName="itemSold";
        agent.inventoryAggregate = new InventoryAggregate();
        agent.inventoryAggregate.inventoryStockOutputStreamName = "inventory";
        
        Topology topology = agent.processItemStream();
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic(agent.itemStream.itemSoldInputStreamName, 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        inventoryOutputTopic = testDriver.createOutputTopic(agent.inventoryAggregate.inventoryStockOutputStreamName, 
                                stringSerde.deserializer(), 
                                inventorySerde.deserializer());
```

If we run the test, the test driver get the topology, and insert two items to the input topic. The first assertion fails as we did not get the inventory to the output topic

```
org.opentest4j.AssertionFailedError: expected: <false> but was: <true>
```

So let add the generation of the inventory aggregate. In the processItemStream() method add before the return statement:

```java
 inventoryAggregate.produceInventoryStockStream(inventory);
 return itemStream.run();
```

Then we need to take the KTable inventory and push element to the output topic. Add the following method into InventoryAggregate class:

```java
  public void produceInventoryStockStream(KTable<String, Inventory> inventory) {
        KStream<String, Inventory> inventories = inventory.toStream();
        inventories.print(Printed.toSysOut());

        inventories.to(inventoryStockOutputStreamName, Produced.with(Serdes.String(), inventorySerde));
    }
```

This code transforms the KTable into a stream and produce the record to the `inventory` topic:

If we rerun our test we should be successful and get a trace like

```
[KTABLE-TOSTREAM-0000000002]: Store-1, ibm.gse.eda.inventory.domain.Inventory@b5191102
[KTABLE-TOSTREAM-0000000002]: Store-1, ibm.gse.eda.inventory.domain.Inventory@a0e80272
```

We covered a lot, normally this topology could run on top of real Kafka cluster. Let do some integration test.

## Integration test

For integration tests we are using python scripts as it is a very powerful and flexible scripting language. We have a Dockerfile to build an image with the Kafka library: The image is already available on `quay.io`. If you want to tune this image, the Dockerfile is in the e2e folder: `docker build -t quay.io/ibmcase/pythonitg .`

* Start Kafka locally with `docker-compose -f dev-docker-compose up -d`
* Create the needed topic: `./scripts/createTopics`
* Start the item-inventory app: `./mvnw quarkus:dev`
* Produce some items from the python environment:

```shell
# Under e2e folder
# get the docker network nane
docker network list
./startPythonEnv.sh <your_docker_network_name>
# In the new shell
/home# python ItemProducer.py 
Start Item Sold Event Producer
INFO:root:--- This is the configuration for the producer: ---
INFO:root:[KafkaProducer] - {'bootstrap.servers': 'kafka:9092', 'group.id': 'ItemSoldProducer-1', 'delivery.timeout.ms': 15000, 'request.timeout.ms': 15000}
INFO:root:---------------------------------------------------
%4|1618380394.094|CONFWARN|rdkafka#producer-1| [thrd:app]: Configuration property group.id is a consumer property and will be ignored by this producer instance
INFO:root:Send {"id": 0, "storeName": "Store-3", "sku": "Item-1", "type": "RESTOCK", "quantity": 5} with key storeName to items
INFO:root:2021-04-14 06:06:34.358374 - Message delivered to items [0]
INFO:root:Send {"id": 1, "storeName": "Store-3", "sku": "Item-1", "type": "SALE", "quantity": 2, "price": 10.0} with key storeName to items
INFO:root:2021-04-14 06:06:34.368892 - Message delivered to items [0]
INFO:root:Send {"id": 2, "storeName": "Store-4", "sku": "Item-2", "type": "RESTOCK", "quantity": 20} with key storeName to items
INFO:root:2021-04-14 06:06:34.377940 - Message delivered to items [0]
INFO:root:Send {"id": 3, "storeName": "Store-4", "sku": "Item-3", "type": "RESTOCK", "quantity": 30} with key storeName to items
INFO:root:2021-04-14 06:06:34.388526 - Message delivered to items [0]
INFO:root:Send {"id": 4, "storeName": "Store-4", "sku": "Item-3", "type": "SALE", "quantity": 5, "price": 6.0} with key storeName to items
INFO:root:2021-04-14 06:06:34.396861 - Message delivered to items [0]
```

* Verify the items are in the Kafka topic: `./scripts/verifyItems.sh`

```
######################
 Verify items topic content
{"id": 0, "storeName": "Store-3", "sku": "Item-1", "type": "RESTOCK", "quantity": 5}
{"id": 1, "storeName": "Store-3", "sku": "Item-1", "type": "SALE", "quantity": 2, "price": 10.0}
{"id": 2, "storeName": "Store-4", "sku": "Item-2", "type": "RESTOCK", "quantity": 20}
{"id": 3, "storeName": "Store-4", "sku": "Item-3", "type": "RESTOCK", "quantity": 30}
{"id": 4, "storeName": "Store-4", "sku": "Item-3", "type": "SALE", "quantity": 5, "price": 6.0}
```

* Verify inventory aggregates are created: ` ./scripts/verifyInventory.sh `

# -----------------------------


And define the out going channel:

```properties
mp.messaging.outgoing.inventory-channel.connector=smallrye-kafka
mp.messaging.outgoing.inventory-channel.topic=inventory
mp.messaging.outgoing.inventory-channel.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.inventory-channel.value.serializer=io.quarkus.kafka.client.serialization.JsonbSerializer
```


## Interactive queries

The KTable is also materialized as a store that can be accessed via an API like `/inventory/store/{storeid}/{itemid}` using interactive query.

As items topic can be partitioned, a REST call may not reach the good end points, as the local store may not have the expected queried key. So the code is using interactive query to get access to the local state stores or return a URL of a remote store where the records for the given key are.


Now restart in dev mode: `mvn quarkus:dev`.... it should compile, and starts running... but could not connect... to Kafka. You may see this message:

```logs
WARN  [or.ap.ka.cl.NetworkClient] (kafka-admin-client-thread | adminclient-1) [AdminClient clientId=adminclient-1] Connection to node -1 (localhost/127.0.0.1:9092) could not be established. Broker may not be available.
```

We have multiple choices to access Kafka. We will use environment variables and Quarkus properties to control this access. Let use a docker compose until we have good enough message structure we can share in a centralized cluster. Use the docker-compose from the [source repository](https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory)

We could use a docker-compose and strimzi kafka image to run a local cluster, but we will use directly Event Streams deployed on OpenShift for as our development kafka cluster.



## Interactive queries

Now as presented in [this note](../../../technology/kafka-streams/#interactive-queries), as soon as we use KTable materialized with state store we can use query to get the last state of the records saved. 
The API returns a query result on the inventory. We can define such bean as:

```Java
public class InventoryQueryResult {
    private static InventoryQueryResult NOT_FOUND = new InventoryQueryResult(null, null, null);
    private final Inventory result;
    private final String host;
    private final Integer port;

    public static InventoryQueryResult notFound() {
        return NOT_FOUND;
    }

    public Optional<Inventory> getResult() {
        return Optional.ofNullable(result);
    }
}
```

So the Resource class is not

```java
@GET
@Path("/store/{storeID}")
@Produces(MediaType.APPLICATION_JSON)
public Uni<InventoryQueryResult> getStock(@PathParam("storeID") String storeID) {
    InventoryQueryResult result = queries.getStoreStock(storeID);
    if (result.getResult().isPresent()) {
        return Uni.createFrom().item(result);
    } else {
        return  Uni.createFrom().item(InventoryQueryResult.notFound());
    }
}
```

The queries is the new class to support interactive query. The principle is simple, we need to access the store that has the storeID key we search for. But there is a small problem, due to the fact that the input topic may be partitioned so the local store may not have the data for the given key. Therefore Kafka streams offers an API to get metadata of the store allocation between nodes for the Kafka Streams.

```Java
 @Inject
KafkaStreams streams;

metadata = streams.queryMetadataForKey(
    StoreInventoryAgent.STOCKS_STORE_NAME,
        storeID,
        Serdes.String().serializer());
    ...
    if (metadata.getActiveHost().host().equals(host)) {
        Inventory result = getStockStore().get(storeID);
        return InventoryQueryResult.found(result);
    } else {
        // call remote or propagate to ask the client to call the other host
        return InventoryQueryResult.foundRemotely(metadata.getActiveHost());
    }
```

### API 

Now we want to complete our APIs by adding information on the store metadata from URL `/meta-data`. The method to add to the Resource class is:

```java
@GET
@Path("/meta-data")
@Produces(MediaType.APPLICATION_JSON)
public Multi<PipelineMetadata> getMetaData() {
    return Multi.createFrom().items(queries.getStockStoreMetaData().stream());
}
```

*It is possible, while testing the API, to get a 404 response. The execption may be linked to the state of the kafka stream processing: for example something like: `java.lang.IllegalStateException: KafkaStreams is not running. State is CREATED.`. This may be due to the test data we have, as once kafka stream for a specific group-id has consumed the records then the offsets are committed, and a new start will not process the old records. Changing the application-id properties can re-read all the records from offset 0.* 



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


## Running distributed mode on your laptop with docker-compose

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

### See if needed

quarkus.kafka-streams.auto.offset.reset=latest
quarkus.kafka-streams.health.enabled=true
