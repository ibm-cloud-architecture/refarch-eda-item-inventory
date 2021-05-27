# Developing the application

The requirements to address are:

- consume item sold events from the `items` topic. Item has SKU as unique key. Item event has store ID reference
- compute for each item its current stock cross stores
- compute the store's stock for each item
- generate inventory event for store - item - stock
- expose APIs to get stock for a store or for an item

## Setting up the Quarkus Application

We will bootstrap the Quarkus application with the following Maven command (See [Quarkus maven tooling guide](https://quarkus.io/guides/maven-tooling#project-creation) for more information), to create the Quarkus App, with JAXRS, OpenAPI and Mutiny for non-blocking processing:

```shell
mvn io.quarkus:quarkus-maven-plugin:1.13.4.Final:create \
    -DprojectGroupId=ibm.garage \
    -DprojectArtifactId=item-inventory \
    -Dextensions="resteasy-jsonb, quarkus-resteasy-mutiny,smallrye-health,quarkus-smallrye-openapi,openshift,kubernetes-config"
```

You can replace the `projectGroupId, projectArtifactId` fields as you would like.

*Recall that, if you want to add a Quarkus extension, do something like: `./mvnw quarkus:add-extension -Dextensions="kafka"`*

## Start the dev mode

In the project root folder, start Quarkus in development mode, and you should be able to continuously develop in your IDE while the application is running:

```shell
./mvnw quarkus:dev
```

Going to the URL [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui). The API is empty but health and open API are predefined due to Quarkus plugins. Health works too: [http://localhost:8080/q/health](http://localhost:8080/q/health) 
and finally the development user interface is accessible via [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/).

* Let add a simple JAXRS resource to manage the Inventory API `InventoryResource.java` under `src/main/java` folder, with the package name: `ibm.gse.eda.inventory.infra.api`
(We try to adopt a domain driven design approach of code layer, and the "onion architecture" with app, domain and infrastructure code structure).

The following code uses JAXRS annotations to expose the API and [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/) to use reactive programming:

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
@Path("/api/v1/items")
public class InventoryResource {
    
    @GET
    @Path("/{itemID}")
    @Produces(MediaType.APPLICATION_JSON)
    public  Uni<JsonObject> getStock(@PathParam("itemID") String itemID) {
            JsonObject stock = new JsonObject("{\"name\": \"hello you\", \"itemID\": \"" + itemID + "\"}");
            return Uni.createFrom().item( stock);
    }
}
```

A refresh on [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui) should bring you a working API, that you can "try it out" by giving an arbitrary item name.

As any Quarkus Application, a lot happen in the configuration, so let add a minimum of configuration to the `application.properties` file,
so we can control logging, and expose the swagger UI even in production and authorize to create a route manifest
when deployed on OpenShift.

```properties
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
quarkus.log.console.level=INFO
quarkus.log.console.enable=true
quarkus.http.port=8080
quarkus.swagger-ui.always-include=true
quarkus.openshift.expose=true
```

To move from imperative programming to a more reactive approach, we are using the `Uni class` from [Mutiny](https://smallrye.io/smallrye-mutiny/) 
so our API is now asynchronous ans non-blocking: Quarkus uses [Vert.x](https://vertx.io/) to support non-blocking IO programming model and Mutiny
 is another abstraction to manage mono or multi elements in a reactive way.

## Deploy to OpenShift using s2i

Before going too far in the development, let deploy this simple app to OpenShift using the source to image 
capability. We assume you are logged to the cluster via `oc login...`, and that you have created a 
project: `oc new-project kstreams-labs`.

The following command should package the application and create OpenShift manifests, build a docker 
images and push it to OpenShift Private registry.

```shell
./mvnw package -Dquarkus.kubernetes.deploy=true
```

It can take some seconds to build for the first time and deploy to OpenShift: `oc get pods -w` lets you 
see the build pods and the running app once the build is done. As we expose the application, an OpenShift 
route was created. The url is visible at the end of the build output, something like:

`...The deployed application can be accessed at: http://item-inventory...`

But if you want to retrieve the exposed route at any time, use the following command:

```shell
oc get routes item-inventory -o jsonpath='{.spec.host}'
```

Which you can combine to immediately test your end point

```shell
URL=$(oc get routes item-inventory -o jsonpath='{.spec.host}')
curl -X GET http://$URL/api/v1/items/Item_1
```

## Modify testing

Adopting a test driven development, let rename and modify the first test case created by Quarkus:

* Rename the GreetingResourceTest to InventoryResourceTest
* Modify the hamcrest assertion to get the json and test on the id

```java
public void testInventoryStoreEndpoint() {
        given()
          .when().get("/api/v1/items/Item_1")
          .then()
             .statusCode(200)
             .body("itemID", is("Item_1")).extract().response();
    }
```

* If run this test within your IDE it should be successful and a run like `./mvnw verify` should also 
succeed. 

We will add more tests soon.

## Add Github action

To support continuous integration, we have done a [workflow for git action](https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory/blob/master/.github/workflows/dockerbuild.yaml) to 
build the docker image and push it to a registry. We use [quay.io](https://quay.io) as of now.

To enable this git action we need to define five secrets in the github repository:

* DOCKER_IMAGE_NAME the image name to build. Here it is `item-inventory`
* DOCKER_USERNAME: user to access the registry (quay.io)
* DOCKER_PASSWORD: and its password.
* DOCKER_REPOSITORY for example the organization we use is `ibmcase`
* DOCKER_REGISTRY set to `quay.io`

To enable your project with git, recall to do something like:

```shell
git init
git remote add origin https://github.com/<your_git_account>/item-inventory.git
git add .
git commit -m "first commit"
git branch -M main
git push --set-upstream origin main
```

## Define the domain entities

Now we need to define our main business entities: item transaction and the item inventory.

Under the `src/main/java/../domain` folder, add the two classes representing the business entities we will be using:

```Java
package ibm.gse.eda.inventory.domain;
import java.time.LocalDateTime;

public class ItemTransaction {
    public static String RESTOCK = "RESTOCK";
    public static String SALE = "SALE";
    public String storeName;
    public String sku;
    public int quantity;
    public String type;
    public Double price;
    public String timestamp;

    public ItemTransaction(){}
}
```

This ItemTransaction will also being used for event structure on `items` topic. The `type` attribute is used to specify 
if this is a sale event or a restock event.

As this service is used to accumulate item quantity cross stores, the ItemInventory includes itemID and current stock.

```java
public class ItemInventory {
    public String itemID;
    public Long currentStock = 0L;
```

As part of the logic we want to add methods in the Inventory class to update the quantity given an item. We start by adding unit tests to design and validate those methods.

Under the `src/test/main` we add:

```java
package ut;
public class TestInventoryLogic {

    @Test
    public void shouldUpdateItemSoldQuantityTo10(){
        ItemTransaction i1 = new ItemTransaction();
        i1.sku = "Item_1";
        i1.type = Item.SALE;
        i1.quantity = 10;
        ItemInventory out = inventory.updateStockQuantityFromTransaction(i1.sku,i1);
        Assertions.assertEquals(10,out.currentStock);
    }
}
```

now let add the two following methods to make this test successful

```Java
public ItemInventory updateStockQuantityFromTransaction(String k, ItemTransaction tx) {
        this.itemID = itemID;
        if (tx.type != null && ItemTransaction.SALE.equals(tx.type)) {
            this.currentStock-=tx.quantity;
        } else {
            this.currentStock+=tx.quantity;
        }
        return this;
    }
```

We can add a restock test and more tests...

Modify the InventoryResource to return the ItemInventory instead of JsonObject (we will connect interactive query later in this lab).

```java
public  Uni<ItemInventory> getItemStock(@PathParam("itemID") String itemID){
        ItemInventory stock = new ItemInventory();
        stock.currentStock = 10;
        stock.itemID=itemID;
        return Uni.createFrom().item( stock);
}
```

Modify the test of `InventoryResourceTest.testInventoryStoreEndpoint` as:

```java
given()
      .when().get("/api/v1/items/item1")
      .then()
      .statusCode(200)
      .body("itemID", is("item1")).extract().response();
```

Now we are good with the REST end point. Lets add Kafka-streams logic.

## Add Kafka Streams and reactive messaging plugins

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
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
    <scope>test</scope>
</dependency>
```
The reactive messaging is not used with Kafka Streams, but used to generate messages to Kafka for integration tests.

Modify the properties to add kafka, kafka-streams and reactive messaging parameters as follow:

```properties
quarkus.kafka-streams.consumer.session.timeout.ms=7000
quarkus.kafka-streams.consumer.heartbeat.interval.ms=200
quarkus.kafka-streams.application-id=item-aggregator
quarkus.kafka-streams.topics=items,inventory

%test.mp.messaging.outgoing.items.connector=smallrye-kafka
%test.mp.messaging.outgoing.items.topic=items
%test.mp.messaging.outgoing.items.key.serializer=org.apache.kafka.common.serialization.StringSerializer
%test.mp.messaging.outgoing.items.value.serializer=io.quarkus.kafka.client.serialization.JsonbSerializer
```

`mvnw verify` should be successful, if you do not set those properties you may encounter errors like:

```shell
One or more configuration errors have prevented the application from starting. The errors are:
  - SRCFG00011: Could not expand value quarkus.application.name in property quarkus.kafka-streams.application-id
  - SRCFG00014: Property quarkus.kafka-streams.topics is required but the value was not found or is empty
```

## Define an item deserializer

The item needs to be deserialized to a Item bean, so we add a new class `ItemTransactionDeserializer` under the 
`ibm.gse.eda.inventory.infrastructure` folder. 

```java
package ibm.gse.eda.inventory.infrastructure;

import ibm.gse.eda.inventory.domain.Item;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ItemTransactionDeserializer extends JsonbDeserializer<ItemTransaction> {
    public ItemDeserializer(){
        // pass the class to the parent.
        super(ItemTransaction.class);
    }
}
```

## Kafka Streams approach

As introduced in [other Kafka Streams labs](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/) we have to define a topology to process the items and compute aggregates. Below are the construct we can use:

* in-topic: items: contains item transaction representing an item sold in store. 
The item object is defined in [domain/ItemTransaction.java](domain/ItemTransaction.java) class.
* out-topic: inventory: contains the item stock events.
* Ktable <itemID, ItemInventory> with store. To keep total stock, cross stores per item
* Interactive query to get data for a given Item ID and expose the result as reactive REST resource.


The approach is to get items stream, extract the item ID as new key and group by this new key. 
We add the `ItemProcessingAgent` class under the domain folder. 
We want to apply a domain driven design implementation approach using domain classes to represent 
the business logic and code expressed with ubiquituous language. 
The proposed implementation almost reach this language, adding only into the vocabulary 
the concept of streams and table in the form of KStream and KTable. We could have avoid 
that but it will not bring that much value for the implementation. 
Stream and tables are clear enough terms to be understood by business analysts.

Here is a squeleton of this class:

```java
package ibm.gse.eda.inventory.domain;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ItemProcessingAgent {
    
     public Topology processItemTransaction(){
       // get item as Kstreams
       // process items and aggregate at the item level 
       // update the current stock for this item 
       // produce inventory records
     }
}
```

We will as usual starts by the tests, so we create a unit test, call [TestItemStreamTopology.java](https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory/blob/master/src/test/java/ut/TestItemStreamTopology.java).

Some remarks on this test implementation:
* Do not use `@QuarkusTest` in the test class to avoid loading the application.properties. 
* The stream processing needs SerDes and configuration so let declare those elements:

```java
    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<ItemTransaction> itemSerde = new JsonbSerde<>(ItemTransaction.class);
  
    private JsonbSerde<ItemInventory> inventorySerde = new JsonbSerde<>(ItemInventory.class);
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

We should not have any compilation error but there is no topology defined yet. 
So let work back on the Agent class to define the topology.

The test class will now have the agent declared and the topology is the output of the 
processItemStream() method:

```java
  private ItemProcessingAgent agent = new ItemProcessingAgent(); 

  public void setup() {
      Topology topology = agent.processItemTransaction();
```

In the ItemProcessingAgent.java class, add the following method. We will explain the logic later:

```java
public Topology processItemStream(){
        KStream<String,ItemTransaction> items = inItemsAsStream.getItemStreams();     
        // process items and aggregate at the store level 
        KTable<String,ItemInventory> itemItemInventory = items
            // use store name as key, which is what the item event is also using
            .map((k,transaction) -> {
                ItemInventory newRecord = new ItemInventory();
                newRecord.updateStockQuantityFromTransaction(transaction.sku, transaction);
               return  new KeyValue<String,ItemInventory>(newRecord.itemID,newRecord);
            })
            .groupByKey( Grouped.with(Serdes.String(),ItemInventory.itemInventorySerde)).
            aggregate( () -> new ItemInventory(),
                (itemID,newValue,currentValue) -> currentValue.updateStockQuantity(itemID,newValue.currentStock),
                materializeAsStoreInventoryKafkaStore());

       
        return inItemsAsStream.run();
    }
```

To separate the Kafka plumbing from the business methods the code for Kafka streams will be the `infra` package. 
So let add the [ItemTransactionStream class]() to get the items as KStreams from the `items` Kafka topic.

This class uses the StreamBuilder from KafkaStreams to build a KStream from the `items` topic with item records. 
The key is the store name as a String. As we want to aggregate on the ItemIDm we do a mapping to change
both key anf value type. Then group records on the new key. Once we have such group we can compute aggregate.
So the code is using the ItemInventory to keep state of the item inventory and compute it via the `aggregate` method. 
The first argument is used when a new key (item) is added, so we need a new ItemInventory, 
the second argument is the method to update existing aggregate (inventory instance) with 
the new record received with the existing key. The last argument is to specify how to 
materialize this Ktable, (the aggregate generates a KTable). 

Let add the run method in the `ItemTransactionStream` to return the topology and run it.

```java
public Topology run() {
		return builder.build();
}
```

The topic name are injected from the application properties:

```
inventory.topic=iteminventory
items.topic=items
```


We should have enough to run our test without NPE, if we complement the test class by modifying 
the setup() method:

```java
@BeforeEach
    public void setup() {
        // as no CDI is used set the topic names
        agent.inItemsAsStream = new ItemTransactionStream();
        agent.inItemsAsStream.itemSoldInputStreamName="itemSold";
        // output will go to the inventory
        
        Topology topology = agent.processItemTransaction();
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic(agent.inItemsAsStream.itemSoldInputStreamName, 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        itemInventoryOutputTopic = testDriver.createOutputTopic(agent.itemInventoryOutputStreamName, 
                                stringSerde.deserializer(), 
                                ItemInventory.itemInventorySerde.deserializer());
```

If we run the test, the test driver get the topology, and insert two items to the input topic. 
The first assertion fails as we did not get the inventory to the output topic

In the processItemStream() method add before the return statement:

```java
 produceStoreInventoryToOutputStream(itemItemInventory);
 return itemStream.run();
```

Then we need to take the KTable inventory and push element to the output topic. 
Add the following method:

```java
  public void produceStoreInventoryToOutputStream(KTable<String, ItemInventory> itemInventory) {
        KStream<String, ItemInventory> inventories = itemInventory.toStream();
        inventories.print(Printed.toSysOut());
        inventories.to(itemInventoryOutputStreamName, Produced.with(Serdes.String(), ItemInventory.itemInventorySerde));
    }
```

This code transforms the KTable into a stream and produce the record to the `item.inventory` topic:

If we rerun our test we should be successful.

We covered a lot, normally this topology could run on top of real Kafka cluster. Let do some integration test.

## Integration tests

For integration tests we are using python scripts as it is a very powerful and flexible scripting language to develop tests. 
We have a Dockerfile to build an image with the Kafka library for Python: The image is already available on `quay.io`. 
If you want to tune this image, the Dockerfile is in the `e2e` folder: `docker build -t quay.io/ibmcase/pythonitg .`

* If not done already start the `Kafka broker` locally with `docker-compose up -d`
* Create the needed topic: `./scripts/createTopics`
* Start the item-inventory app: `./mvnw quarkus:dev`
* Produce some items from the python environment doing the following steps:

```shell
# Under e2e folder
# get the docker network nane
docker network list
# You may get something similar to:
> refarch-eda-item-inventory_default bridge local
# Start the python container attach to this network
./startPythonEnv.sh <your_docker_network_name>
# or using the default name of refarch-eda-item-inventory_default
./startPythonEnv.sh
# Within the container shell session start the producer: 
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

```sh
######################
 Verify items topic content
{"id": 0, "storeName": "Store-3", "sku": "Item-1", "type": "RESTOCK", "quantity": 5}
{"id": 1, "storeName": "Store-3", "sku": "Item-1", "type": "SALE", "quantity": 2, "price": 10.0}
{"id": 2, "storeName": "Store-4", "sku": "Item-2", "type": "RESTOCK", "quantity": 20}
{"id": 3, "storeName": "Store-4", "sku": "Item-3", "type": "RESTOCK", "quantity": 30}
{"id": 4, "storeName": "Store-4", "sku": "Item-3", "type": "SALE", "quantity": 5, "price": 6.0}
```

* Verify inventory aggregates are created: ` ./scripts/verifyInventory.sh`

## Interactive queries

Now as presented in [this note](../../../technology/kafka-streams/#interactive-queries), as soon as 
we use KTable materialized with state store we can use query to get the last state of the records saved. 

As items topic can be partitioned, a REST call may not reach the good end points, as the local store may not have the expected queried key. 
So the code is using interactive query to get access to the local state stores or return a URL of a remote item where the record for the given key is.

The API returns a query result on the inventory. We can define such bean as:

```Java
public class ItemCountQueryResult {
    private static ItemCountQueryResult NOT_FOUND = new ItemCountQueryResult(null, null, null);
    private final ItemInventory result;
    private final String host;
    private final Integer port;
}
```

Modify the Resource class to use this result DTO now:

```java
@GET
    @Path("/{itemID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ItemCountQueryResult> getItemStock(@PathParam("itemID") String itemID){
```

The queries is the new class to support interactive query. The principle is simple, we need to access 
the Kafka store that has the itemID key we search for. But there is a small problem, 
due to the fact that the input topic may be partitioned so the local store may not have 
the data for the given key. Therefore Kafka streams offers an API to get metadata of 
the store allocation between nodes for the Kafka Streams.

```Java
 @Inject
KafkaStreams streams;

    public List<PipelineMetadata> getItemCountStoreMetadata() {
        return streams.allMetadataForStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME)
                .stream()
                .map(m -> new PipelineMetadata(
                        m.hostInfo().host() + ":" + m.hostInfo().port(),
                        m.topicPartitions()
                                .stream()
                                .map(TopicPartition::toString)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toList());
    }
```

### API 

Now we want to complete our APIs by adding information on the store metadata from URL `/meta-data`. The method to add to the Resource class is:

```java
@GET
@Path("/meta-data")
@Produces(MediaType.APPLICATION_JSON)
public Multi<PipelineMetadata> getMetaData() {
    return Multi.createFrom().items(queries.getItemCountStoreMetadata().stream());
}
```

*It is possible, while testing the API, to get a 404 response. The execption may be linked to the state of the kafka stream processing: 
for example something like: `java.lang.IllegalStateException: KafkaStreams is not running. State is CREATED.`. 
This may be due to the test data we have, as once kafka stream for a specific group-id has consumed 
the records then the offsets are committed, and a new start will not process the old records. 
Changing the application-id properties can re-read all the records from offset 0.* 

