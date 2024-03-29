# Item sold aggregator component

This project illustrates combining Kafka Streams with reactive programming, and reactive messaging with Quarkus.
The goal of the Kafka streams implementation is to build a real time inventory view from items sold in different stores. The aggregates are kept in state store and exposed via interactive queries.

The project is used as a Kafka Streams lab [documented here](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/lab-3/) with instructions to deploy and run it on OpenShift.

Here is a simple diagram to illustrate the components used:

 ![1](https://github.com/ibm-cloud-architecture/refarch-eda/blob/master/docs/src/pages/use-cases/kafka-streams/lab-3/images/inventory-components.png)

The goal of this note is to present how to run this item inventory aggregartor locally using Strimzi Kafka image and instructions to build it.

Updated 01/25/2021

* move to quarkus 2.6.3
> quarkus dev   does not work.

Updated 06/09/2021

## Pre-requisites

For development purpose the following pre-requisites need to be installed on your working computer:

**Java**
- For the purposes of this lab we suggest Java 11+
- Quarkus (on version 1.13.x)

**Git client**

**Maven**
- Maven will be needed for bootstrapping our application from the command-line and running
our application.

**Docker**

If you want to access the end solution clone the following git repository: `git clone https://github.com/ibm-cloud-architecture/refarch-eda-item-inventory`.

## In a hurry, just run it locally

* Start local Kafka: `docker-compose  up -d` to start one Kafka broker, and two item-inventory service instances. 
* Created the `items` and `item.inventory` topics on your Kafka instance
 
 ```shell
 ./scripts/createTopics.sh 
######################
 create Topics
Created topic items.
Created topic item.inventory.

./scripts/listTopics.sh 
######################
 List Topics
item.inventory
items
 ```

* Verify each components runs well with `docker ps`:

```sh
CONTAINER ID   IMAGE                                      PORTS                     NAMES
2c2959bbda15   obsidiandynamics/kafdrop                   0.0.0.0:9000->9000/tcp    kafdrop
3e569f205f6f   cp.icr.io/cp/ibm-eventstreams-kafka:10.5.0 0.0.0.0:29092->9092/tcp   kafka
0cf09684b675   cp.icr.io/cp/ibm-eventstreams-kafka:10.5.0 0.0.0.0:2181->2181/tcp    zookeeper
d4f74a23cf6c   quay.io/ibmcase/eda-store-simulator:0.0.10 0.0.0.0:8081->8080/tcp    simulator
```

* Start the app in dev mode: `quarkus dev`
* do a POST on the simulator to generate 9 records

```sh
curl -X POST   -H 'accept: application/json' -H 'Content-Type: application/json' http://localhost:8081/api/stores/v1/startControlled -d '{ "records": 1, "backend": "KAFKA"}'  
```

The trace inside the item inventory code should list:

```sh
[KTABLE-TOSTREAM-0000000006]: Item_2, { itemID: Item_2 -> 0
[KTABLE-TOSTREAM-0000000006]: Item_3, { itemID: Item_3 -> 10
[KTABLE-TOSTREAM-0000000006]: Item_1, { itemID: Item_1 -> 50
```

Then [see the demonstration](#demonstration-script) script section below to test the application.

## Demonstration script

Once started go to one of the Item Aggregator API: [swagger-ui/](http://localhost:8080/q/swagger-ui/) and 
the `/api/v1/items/{itemID}` end point. Using the `Item_1` as itemID you should get an empty response.

* Send some item sale simulated records with `curl` on the simulator APIs: `curl -X POST http://localhost:8082/start -d '20'` or using the user interface at []()

  ![](./docs/store_simulator.png)

* Use [Kafdrop UI](http://localhost:9000/) to see messages in `items` topic.

  ![](./docs/kafdrop_items.png)

* Verify the store inventory is updated: `curl -X GET "http://localhost:8080/api/v1/items/Item_2" -H  "accept: application/json"`
* Verify messages are sent to `item.inventory` topic by 

  ![](./docs/kafdrop_item_inventory.png)

**Remark: after the item aggregator consumes some items, you should see some new topics created, used to persist the 
the item store.**

## Developing the application from A to Z

To develop the same type of application see [this specific note](./docs/dev-app.md). It also includes some testing tools
for integration tests.


## Deploy on OpenShift cluster with Kafka Strimzi

The instructions to deploy the complete real time inventorty solution is desceribed in [this Kafka Stream lab](https://ibm-cloud-architecture.github.io/refarch-eda/use-cases/kafka-streams/lab-3/). It uses gitops and a unique script
to deploy Kafka, configure topic, users and deploy the components.

## Continuous integration with OpenShift Pipelines

* Create a `rt-inventory-gitops` OpenShift project to execute pipeline
* Be sure the OpenShift pipelines operator is deployed if not do:

  ```sh
  oc apply -f https://raw.githubusercontent.com/ibm-cloud-architecture/eda-lab-inventory/master/environments/openshift-pipelines/operator.yaml
  ```
  
* Install the needed tasks to build app (some are provided as clustertask, some need updates): 

  ```sh
  oc apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/maven/0.2/maven.yaml
  ```

* Defines resources and pipeline:

 ```sh
 oc apply -k build/resource.yaml
 ```

* Execute the pipeline:

  ```sh
  oc create -f build/pipelinerun.yaml
  ```

  you should get this result:
  
  ![](./docs/quarkus-pipeline.png)


