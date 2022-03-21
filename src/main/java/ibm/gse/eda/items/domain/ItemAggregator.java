package ibm.gse.eda.items.domain;

import java.util.logging.Logger;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

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
import org.apache.kafka.streams.state.KeyValueStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import ibm.gse.eda.items.infra.events.ItemSerdes;

/**
 * The agent processes item from the items stream and build an inventory aggregate
 * per item
 */
@Singleton
public class ItemAggregator {
    private static final Logger LOG = Logger.getLogger(ItemAggregator.class.getName()); 
    // store to keep stock per item-id
    public static String ITEMS_STOCK_KAFKA_STORE_NAME = "ItemStock";
     // Input stream is item transaction from the store machines
    @ConfigProperty(name="app.item.topic", defaultValue = "items")
    public String itemSoldInputStreamName;

    @ConfigProperty(name="app.item.inventory.topic",defaultValue = "item.inventory")
    public String itemInventoryOutputStreamName;
    
    public ItemAggregator() {
        LOG.info("ItemProcessingAgent created produce to " + itemInventoryOutputStreamName);
    }

    /**
     * The topology processes the items stream to compute the sum of items sold per item-id, 
     * cross stores
     **/  
    @Produces
    public Topology buildProcessFlow(){
        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String,ItemTransaction> items = builder.stream(itemSoldInputStreamName, 
        Consumed.with(Serdes.String(),  ItemSerdes.ItemTransactionSerde()));      
        // process items and aggregate at the item level 
        KTable<String,ItemInventory> itemItemInventory = items
            // the key is the store name
            .map((k,transaction) -> {
                ItemInventory newRecord = new ItemInventory();
                newRecord.updateStockQuantityFromTransaction(transaction.sku, transaction);
               return  new KeyValue<String,ItemInventory>(newRecord.itemID,newRecord);
            })
            .groupByKey( Grouped.with(Serdes.String(),ItemSerdes.ItemInventorySerde())).
            aggregate( () -> new ItemInventory(),
                (itemID,newValue,currentValue) -> currentValue.updateStockQuantity(itemID,newValue.currentStock),
                materializeAsStoreInventoryKafkaStore());
        produceStoreInventoryToOutputStream(itemItemInventory);
        return builder.build();
    }

    private static Materialized<String, ItemInventory, KeyValueStore<Bytes, byte[]>> materializeAsStoreInventoryKafkaStore() {
        return Materialized.<String, ItemInventory, KeyValueStore<Bytes, byte[]>>as(ITEMS_STOCK_KAFKA_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(ItemSerdes.ItemInventorySerde());
    }

    public void produceStoreInventoryToOutputStream(KTable<String, ItemInventory> itemInventory) {
        KStream<String, ItemInventory> inventories = itemInventory.toStream();
        inventories.print(Printed.toSysOut());
        inventories.to(itemInventoryOutputStreamName, Produced.with(Serdes.String(), ItemSerdes.ItemInventorySerde()));
    }

}