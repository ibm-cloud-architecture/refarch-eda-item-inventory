package ibm.gse.eda.inventory.domain;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

import ibm.gse.eda.inventory.infra.ItemInventoryStream;
import ibm.gse.eda.inventory.infra.ItemStream;

/**
 * The agent processes item from the items stream and build an inventory aggregate
 * per store. It accumulates sale quantity per item 
 * and stock amount per store per item
 */
@ApplicationScoped
public class StoreInventoryAgent {
    // store to keep stock per store-id
    public static String STORE_INVENTORY_KAFKA_STORE_NAME = "StoreInventoryStock";
    // store to keep item solds per item-id
    public static String ITEMS_STOCK_KAFKA_STORE_NAME = "ItemStock";

    KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore(STORE_INVENTORY_KAFKA_STORE_NAME);

    @Inject
    public ItemStream inItemsAsStream;

    @Inject
    public ItemInventoryStream itemInventoryAsStream;

    /**
     * The topology processes the items stream into two different paths: one
     * to compute the sum of items sold per item-id, the other to compute
     * the inventory per store. An app can have one topology.
     **/  
    @Produces
    public Topology processItemStream(){
        KStream<String,ItemTransaction> items = inItemsAsStream.getItemStreams();     
        // process items and aggregate at the store level 
        KTable<String,StoreInventory> storeItemInventory = items
            // use store name as key, which is what the item event is also using
            .groupByKey(ItemStream.buildGroupDefinitionType())
            // update the current stock for this <store,item> pair
            // change the value type
            .aggregate(
                () ->  new StoreInventory(), // initializer when there was no store in the table
                (store , newItem, existingStoreInventory) 
                    -> existingStoreInventory.updateStockQuantity(store,newItem), 
                    materializeAsItemInventoryKafkaStore());       
        itemInventoryAsStream.produceItemInventoryToInventoryOutputStream(storeItemInventory);
        // Next is to accumulate item stock at the item level
        accumulateGlobalItemCountInDistributedTables();
        return inItemsAsStream.run();
    }


    /**
     * Keep in a table the item stock from the inventory output stream
     * We can process the stream which is still in memory
     */
    public void accumulateGlobalItemCountInDistributedTables(){
        itemInventoryAsStream.joinBuilder(inItemsAsStream.builder);
        itemInventoryAsStream.buildInventoryStream()
        .flatMap( ( storeID, itemInventory) ->  itemInventoryAsStream.getItemSold(storeID,itemInventory))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
        .reduce(Long::sum,
                materializeAsItemGlobalCountStore());
    }


    private static Materialized<String, StoreInventory, KeyValueStore<Bytes, byte[]>> materializeAsItemInventoryKafkaStore() {
        return Materialized.<String, StoreInventory, KeyValueStore<Bytes, byte[]>>as(STORE_INVENTORY_KAFKA_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(StoreInventory.storeInventorySerde);
    }

    /**
     * Create a key-value store named ITEMS_STORE_NAME for item current stock count
     */
    public static Materialized<String,Long,KeyValueStore<Bytes,byte[]>> materializeAsItemGlobalCountStore(){
        return Materialized.<String,Long,KeyValueStore<Bytes,byte[]>>as(ITEMS_STOCK_KAFKA_STORE_NAME)
        .withKeySerde(Serdes.String())
        .withValueSerde(Serdes.Long());
    }
}