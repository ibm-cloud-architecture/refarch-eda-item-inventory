package ut;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ibm.gse.eda.inventory.domain.StoreInventory;
import ibm.gse.eda.inventory.domain.ItemTransaction;
import ibm.gse.eda.inventory.domain.StoreInventoryAgent;
import ibm.gse.eda.inventory.infra.ItemInventoryStream;
import ibm.gse.eda.inventory.infra.ItemStream;
import io.quarkus.kafka.client.serialization.JsonbSerde;

/**
 * Use TestDriver to test the Kafka streams topology without kafka brokers
 */
public class TestInventoryTopology {
     
    private static TopologyTestDriver testDriver;

    private TestInputTopic<String, ItemTransaction> inputTopic;
    private TestOutputTopic<String, StoreInventory> inventoryOutputTopic;
 
    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<ItemTransaction> itemSerde = new JsonbSerde<>(ItemTransaction.class);
    private JsonbSerde<StoreInventory> inventorySerde = new JsonbSerde<>(StoreInventory.class);
  
    private StoreInventoryAgent agent = new StoreInventoryAgent();
    
    public  Properties getStreamsConfig() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stock-aggregator");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummmy:1234");
        return props;
    }


    /**
     * From items streams which includes sell or restock events from a store
     * aggregage per store and keep item, quamntity
     */
    @BeforeEach
    public void setup() {
        // as no CDI is used set the topic names
        agent.inItemsAsStream = new ItemStream();
        agent.inItemsAsStream.itemSoldInputStreamName="itemSold";
        // output will go to the inventory
        agent.itemInventoryAsStream = new ItemInventoryStream();
        agent.itemInventoryAsStream.inventoryStockOutputStreamName = "inventory";
        
        Topology topology = agent.processItemStream();
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic(agent.inItemsAsStream.itemSoldInputStreamName, 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        inventoryOutputTopic = testDriver.createOutputTopic(agent.itemInventoryAsStream.inventoryStockOutputStreamName, 
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

    @Test
    public void shouldGetInventoryUpdatedQuantity(){
        //given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // and then sold        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // verify an inventory aggregate events are created with good quantity
        Assertions.assertFalse(inventoryOutputTopic.isEmpty()); 
        Assertions.assertEquals(5, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(3, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
    }
    
    @Test
    public void shouldGetRestockQuantity(){
        // given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5);
        inputTopic.pipeInput(item.storeName, item);        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,2);
        inputTopic.pipeInput(item.storeName, item);

        Assertions.assertFalse(inventoryOutputTopic.isEmpty()); 
        // can validate at the <Key,Value> Store
        ReadOnlyKeyValueStore<String,StoreInventory> storage = testDriver.getKeyValueStore(StoreInventoryAgent.STORE_INVENTORY_KAFKA_STORE_NAME);
        StoreInventory i = (StoreInventory)storage.get("Store-1");
        // the store keeps the last inventory
        Assertions.assertEquals(7L,  i.stock.get("Item-1"));
        // the output streams gots all the events
        Assertions.assertEquals(5, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(7, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
     
     }

     
     @Test
     public void shouldGetTwoItemsSold(){
         ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,33.2);
         inputTopic.pipeInput(item.storeName, item);
         ReadOnlyKeyValueStore<String,Long> storage = testDriver.getKeyValueStore(StoreInventoryAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
         Assertions.assertEquals(2, storage.get("Item-1"));
     }
     
     @Test
     public void shouldGetFiveItemsSoldOverMultipleStores(){
         //given an item is sold in a store
         ItemTransaction itemStocked1 = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,6,33.2);
         ItemTransaction itemSold1 = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,33.2);
         ItemTransaction itemStocked2 = new ItemTransaction("Store-2","Item-1",ItemTransaction.RESTOCK,5,30.2);
         ItemTransaction itemSold2 = new ItemTransaction("Store-2","Item-1",ItemTransaction.SALE,3,30.2);
         inputTopic.pipeInput(itemStocked1.storeName, itemStocked1);
         inputTopic.pipeInput(itemStocked2.storeName, itemStocked2);
         inputTopic.pipeInput(itemSold1.storeName, itemSold1);
         inputTopic.pipeInput(itemSold2.storeName, itemSold2);
         ReadOnlyKeyValueStore<String,Long> storage = testDriver.getKeyValueStore(StoreInventoryAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
         Assertions.assertEquals(7, storage.get("Item-1"));
     }
     
 
}