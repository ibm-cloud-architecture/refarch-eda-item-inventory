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

import ibm.gse.eda.inventory.domain.Inventory;
import ibm.gse.eda.inventory.domain.Item;
import ibm.gse.eda.inventory.infrastructure.StoreInventoryAgent;
import io.quarkus.kafka.client.serialization.JsonbSerde;

public class TestInventory {
     
    private static TopologyTestDriver testDriver;

    private TestInputTopic<String, Item> inputTopic;
    private TestOutputTopic<String, Inventory> inventoryOutputTopic;
   
    private StoreInventoryAgent agent = new StoreInventoryAgent();

    private Serde<String> stringSerde = new Serdes.StringSerde();
    private JsonbSerde<Item> itemSerde = new JsonbSerde<>(Item.class);
    
    private JsonbSerde<Inventory> inventorySerde = new JsonbSerde<>(Inventory.class);
  
    public  Properties getStreamsConfig() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stock-aggregator");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummmy:1234");
        return props;
    }


    /**
     * process item sale events, and aggregate per key
     */
    @BeforeEach
    public void setup() {
        // as no CDI is used set the topic names
        agent.itemSoldTopicName="itemSold";
        agent.inventoryStockTopicName = "inventory";
        Topology topology = agent.buildTopology();
        testDriver = new TopologyTestDriver(topology, getStreamsConfig());
        inputTopic = testDriver.createInputTopic(agent.itemSoldTopicName, 
                                stringSerde.serializer(),
                                itemSerde.serializer());
        inventoryOutputTopic = testDriver.createOutputTopic(agent.inventoryStockTopicName, 
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
        //given an item is sold in a store
        Item item = new Item("Store-1","Item-1",Item.RESTOCK,5,33.2);
        inputTopic.pipeInput(item.sku, item);        
        item = new Item("Store-1","Item-1",Item.SALE,2,33.2);
        inputTopic.pipeInput(item.sku, item);

        Assertions.assertFalse(inventoryOutputTopic.isEmpty()); 
        Assertions.assertEquals(5, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
        Assertions.assertEquals(3, inventoryOutputTopic.readKeyValue().value.stock.get("Item-1"));
    }
    
    @Test
    public void shouldGetRestockQuantity(){
        //given an item is sold in a store
        Item item = new Item("Store-1","Item-1",Item.RESTOCK,5);
        inputTopic.pipeInput(item.sku, item);        
        item = new Item("Store-1","Item-1",Item.RESTOCK,2);
        inputTopic.pipeInput(item.sku, item);

        Assertions.assertFalse(inventoryOutputTopic.isEmpty()); 
        ReadOnlyKeyValueStore<String,Inventory> storage = testDriver.getKeyValueStore(agent.STOCKS_STORE_NAME);
        Inventory i = (Inventory)storage.get("Store-1");
       
        Assertions.assertEquals(7L,  i.stock.get("Item-1"));
     }

     @Test
     public void shouldGetTwoItemsSold(){
         //given an item is sold in a store
         Item item = new Item("Store-1","Item-1",Item.SALE,2,33.2);
         inputTopic.pipeInput(item.sku, item);
         ReadOnlyKeyValueStore<String,Long> storage = testDriver.getKeyValueStore(agent.ITEMS_STORE_NAME);
         Assertions.assertEquals(2, storage.get("Item-1"));
     }
     
     @Test
     public void shouldGetFiveItemsSoldOverMultipleStores(){
         //given an item is sold in a store
         Item itemSold1 = new Item("Store-1","Item-1",Item.SALE,2,33.2);
         Item itemSold2 = new Item("Store-2","Item-1",Item.SALE,3,30.2);
         inputTopic.pipeInput(itemSold1.sku, itemSold1);
         inputTopic.pipeInput(itemSold2.sku, itemSold2);
         ReadOnlyKeyValueStore<String,Long> storage = testDriver.getKeyValueStore(agent.ITEMS_STORE_NAME);
         Assertions.assertEquals(5, storage.get("Item-1"));
     }
 
}