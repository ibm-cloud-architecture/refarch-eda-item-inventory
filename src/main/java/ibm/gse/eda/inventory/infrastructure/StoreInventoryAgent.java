package ibm.gse.eda.inventory.infrastructure;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import ibm.gse.eda.inventory.domain.Inventory;
import ibm.gse.eda.inventory.domain.Item;
import io.quarkus.kafka.client.serialization.JsonbSerde;
/**
 * Defines the topology to process items, to accumulate sale quantity per item 
 * and stock amount per store per item
 */
@ApplicationScoped
public class StoreInventoryAgent {
    
    @Inject
    @ConfigProperty(name="mp.messaging.incoming.item-channel.topic")
    public String itemSoldTopicName;

    @Inject
    @ConfigProperty(name="mp.messaging.outgoing.inventory-channel.topic")
    public String inventoryStockTopicName;

    // store to keep item solds per item-id
    public static String ITEMS_STORE_NAME = "ItemSoldStore";

    // store to keep stock per store-id
    public static String STOCKS_STORE_NAME = "StoreStock";

    private JsonbSerde<Item> itemSerde = new JsonbSerde<>(Item.class);
    private JsonbSerde<Inventory> inventorySerde = new JsonbSerde<>(Inventory.class);
    KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore(STOCKS_STORE_NAME);


    /**
     * The topology process the items stream into two different paths: one
     * to compute the sum of items sold per item-id, the other to compute
     * the inventory per store. An app can have one topology.
     **/  
    @Produces
    public Topology buildTopology(){
        StreamsBuilder builder = new StreamsBuilder();
        // process items and aggregate at the store level 
        KTable<String,Inventory> inventory = builder.stream(itemSoldTopicName, 
                        Consumed.with(Serdes.String(), itemSerde))
            // use store name as key
            .groupByKey(Grouped.with(Serdes.String(),itemSerde))
            // update the current stock for this store - item pair
            // change the value type
            .aggregate(
                () ->  new Inventory(), // initializer
                (k , newItem, aggregate) 
                    -> aggregate.updateStockQuantity(k,newItem), 
                Materialized.<String,Inventory,KeyValueStore<Bytes,byte[]>>as(StoreInventoryAgent.STOCKS_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(inventorySerde));
            // generate to inventory topic
            KStream<String, Inventory> inventories = inventory.toStream();
            inventories.print(Printed.toSysOut());

            inventories.to(inventoryStockTopicName,
                Produced.with(Serdes.String(),inventorySerde));
      
        return builder.build();
    }


  
    
}