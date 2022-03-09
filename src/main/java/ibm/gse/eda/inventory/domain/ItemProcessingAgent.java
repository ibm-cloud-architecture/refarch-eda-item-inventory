package ibm.gse.eda.inventory.domain;

import java.util.Optional;
import java.util.logging.Logger;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import ibm.gse.eda.inventory.infra.ItemTransactionStream;

/**
 * The agent processes item from the items stream and build an inventory aggregate
 * per item
 */
@Singleton
public class ItemProcessingAgent {
    private static final Logger LOG = Logger.getLogger(ItemProcessingAgent.class.getName()); 
    // store to keep stock per store-id
    public static String ITEMS_STOCK_KAFKA_STORE_NAME = "ItemStock";
    @ConfigProperty(name="app.item.inventory.topic",defaultValue = "item.inventory")
    public String itemInventoryOutputStreamName;

    // input streams
    public ItemTransactionStream inItemsAsStream;
    
    public ItemProcessingAgent() {
       /* 
        Optional<String> v =ConfigProvider.getConfig().getOptionalValue("app.item.inventory.topic", String.class);
        if (v.isPresent()) {
            this.itemInventoryOutputStreamName = v.get();
        }
        */
        LOG.info("ItemProcessingAgent created produce to " + itemInventoryOutputStreamName);
    }

    /**
     * The topology processes the items stream to compute the sum of items sold per item-id, 
     **/  
    @Produces
    public Topology processItemTransaction(){
        this.inItemsAsStream = new ItemTransactionStream();
        KStream<String,ItemTransaction> items = inItemsAsStream.getItemStreams();     
        // process items and aggregate at the item level 
        KTable<String,ItemInventory> itemItemInventory = items
            // the key is the store name
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