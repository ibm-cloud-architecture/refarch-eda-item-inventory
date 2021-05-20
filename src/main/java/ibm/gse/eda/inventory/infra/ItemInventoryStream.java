package ibm.gse.eda.inventory.infra;

import java.util.LinkedList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import ibm.gse.eda.inventory.domain.StoreInventory;
import io.quarkus.kafka.client.serialization.JsonbSerde;

/**
 * Item inventory stream represents the stream of output message 
 * to share stock value for an item
 */
@ApplicationScoped
public class ItemInventoryStream {

    @Inject
    @ConfigProperty(name = "inventory.topic")
    public String inventoryStockOutputStreamName;

    
    public StreamsBuilder builder;

    public ItemInventoryStream() {
        builder = new StreamsBuilder();
    }

    public void joinBuilder(StreamsBuilder builder) {
        this.builder = builder;
    }

    public KStream<String, StoreInventory> buildInventoryStream() {
        if (builder == null) {
            builder = new StreamsBuilder();
        }
        return builder.stream(inventoryStockOutputStreamName, Consumed.with(Serdes.String(), StoreInventory.storeInventorySerde));
    }


    public void produceItemInventoryToInventoryOutputStream(KTable<String, StoreInventory> inventory) {
        KStream<String, StoreInventory> inventories = inventory.toStream();
        inventories.print(Printed.toSysOut());

        inventories.to(inventoryStockOutputStreamName, Produced.with(Serdes.String(), StoreInventory.storeInventorySerde));
    }

    // ! completely wrong
    public List<KeyValue<String, Long>> getItemSold(String storeID, StoreInventory v) {
        List<KeyValue<String, Long>> result = new LinkedList<>();
        v.stock.forEach((k2, v2) -> result.add(new KeyValue<String, Long>(k2, -v2)));
        return result;
    }
}
