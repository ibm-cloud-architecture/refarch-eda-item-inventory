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

import ibm.gse.eda.inventory.domain.ItemInventory;
import ibm.gse.eda.inventory.domain.ItemProcessingAgent;
import ibm.gse.eda.inventory.domain.ItemTransaction;
import ibm.gse.eda.inventory.infra.ItemTransactionStream;
import io.quarkus.kafka.client.serialization.JsonbSerde;

/**
 * Use TestDriver to test the Kafka streams topology without kafka brokers
 */
public class TestItemStreamTopology {
     
    private static TopologyTestDriver testDriver;

    private TestInputTopic<String, ItemTransaction> inputTopic;
    private TestOutputTopic<String, ItemInventory> itemInventoryOutputTopic;
 
    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<ItemTransaction> itemSerde = new JsonbSerde<>(ItemTransaction.class);
   
    private ItemProcessingAgent agent = new ItemProcessingAgent();
   
    
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
    public void shouldGetAInventoryWithTwoItemsFromOneStore() {
        // given two items are stocked in the same store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        item = new ItemTransaction("Store-1","Item-2",ItemTransaction.RESTOCK,10,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // the inventory keeps the items' stock
        ReadOnlyKeyValueStore<String,ItemInventory> itemInventory = testDriver.getKeyValueStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
        ItemInventory aStoreStock = (ItemInventory)itemInventory.get("Item-1");
        Assertions.assertEquals(5L,  aStoreStock.currentStock);
        aStoreStock = (ItemInventory)itemInventory.get("Item-2");
        Assertions.assertEquals(10L,  aStoreStock.currentStock);
        
    }

    @Test
    public void shouldGetInventoryWithAggreatedItemStock() {
         // given the same item is stocked in two stores 
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        item = new ItemTransaction("Store-2","Item-1",ItemTransaction.RESTOCK,10,33.2);
        inputTopic.pipeInput(item.storeName, item);

        // then the total  count of item 1 is the sum of each store stock for item 1
        ReadOnlyKeyValueStore<String,ItemInventory> itemStore = testDriver.getKeyValueStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
        Assertions.assertEquals(15, itemStore.get("Item-1").currentStock);
    }

    @Test
    public void shouldGetEmptyStockForItemSold() {
         // given the same item is stocked in two stores 
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,5,33.2);
        inputTopic.pipeInput(item.storeName, item);

        // then the total  count of item 1 is the sum of each store stock for item 1
        ReadOnlyKeyValueStore<String,ItemInventory> itemStock = testDriver.getKeyValueStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
        Assertions.assertEquals(0, itemStock.get("Item-1").currentStock);
    }

    @Test
    public void shouldGetInventoryUpdatedQuantity(){
        //given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // and then sold        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,33.2);
        inputTopic.pipeInput(item.storeName, item);
        // verify an store inventory aggregate events are created with good quantity
        Assertions.assertFalse(itemInventoryOutputTopic.isEmpty()); 
        Assertions.assertEquals(5, itemInventoryOutputTopic.readKeyValue().value.currentStock);
        Assertions.assertEquals(3, itemInventoryOutputTopic.readKeyValue().value.currentStock);
    }
    
    @Test
    public void shouldGetRestockQuantity(){
        // given an item is stocked in a store
        ItemTransaction item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5);
        inputTopic.pipeInput(item.storeName, item);        
        item = new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,2);
        inputTopic.pipeInput(item.storeName, item);

        Assertions.assertFalse(itemInventoryOutputTopic.isEmpty()); 
        // the output streams gots all the events
        Assertions.assertEquals(5, itemInventoryOutputTopic.readKeyValue().value.currentStock);
        Assertions.assertEquals(7, itemInventoryOutputTopic.readKeyValue().value.currentStock);
     
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
         ReadOnlyKeyValueStore<String,ItemInventory> storage = testDriver.getKeyValueStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME);
         Assertions.assertEquals(6, storage.get("Item-1").currentStock);
         Assertions.assertFalse(itemInventoryOutputTopic.isEmpty()); 
         // the output streams gots all the events
         Assertions.assertEquals(6, itemInventoryOutputTopic.readKeyValue().value.currentStock);
         Assertions.assertEquals(11, itemInventoryOutputTopic.readKeyValue().value.currentStock);
         Assertions.assertEquals(9, itemInventoryOutputTopic.readKeyValue().value.currentStock);
         Assertions.assertEquals(6, itemInventoryOutputTopic.readKeyValue().value.currentStock);
     }
     
 
}