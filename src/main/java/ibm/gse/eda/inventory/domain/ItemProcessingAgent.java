package ibm.gse.eda.inventory.domain;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.eclipse.microprofile.config.ConfigProvider;

import ibm.gse.eda.inventory.infra.ItemTransactionStream;

/**
 * The agent processes item from the items stream and build an inventory aggregate
 * per item
 */
@ApplicationScoped
public class ItemProcessingAgent {
    // store to keep stock per store-id
    public static String ITEMS_STOCK_KAFKA_STORE_NAME = "ItemStock";
    public String itemInventoryOutputStreamName= "item.inventory";

    // input streams
    public ItemTransactionStream inItemsAsStream;
    
    public ItemProcessingAgent() {
        this.inItemsAsStream = new ItemTransactionStream();
        Optional<String> v =ConfigProvider.getConfig().getOptionalValue("app.item.inventory.topic", String.class);
        if (v.isPresent()) {
            this.itemInventoryOutputStreamName = v.get();
        }
    }

    /**
     * The topology processes the items stream into two different paths: one
     * to compute the sum of items sold per item-id, the other to compute
     * the inventory per store. An app can have one topology.
     **/  
    @Produces
    public Topology processItemTransaction(){
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
        produceStoreInventoryToOutputStream(itemItemInventory);
        return inItemsAsStream.run();
    }

    private static Materialized<String, ItemInventory, KeyValueStore<Bytes, byte[]>> materializeAsStoreInventoryKafkaStore() {
        return Materialized.<String, ItemInventory, KeyValueStore<Bytes, byte[]>>as(ITEMS_STOCK_KAFKA_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(ItemInventory.itemInventorySerde);
    }

    public void produceStoreInventoryToOutputStream(KTable<String, ItemInventory> itemInventory) {
        KStream<String, ItemInventory> inventories = itemInventory.toStream();
        inventories.print(Printed.toSysOut());
        inventories.to(itemInventoryOutputStreamName, Produced.with(Serdes.String(), ItemInventory.itemInventorySerde));
    }

}