package ibm.gse.eda.inventory.infrastructure;

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

import ibm.gse.eda.inventory.domain.Inventory;
import io.quarkus.kafka.client.serialization.JsonbSerde;

@ApplicationScoped
public class InventoryAggregate {

    // store to keep stock per store-id
    public static String INVENTORY_STORE_NAME = "StoreInventoryStock";

    @Inject
    @ConfigProperty(name = "mp.messaging.outgoing.inventory-channel.topic")
    public String inventoryStockOutputStreamName;

    private static JsonbSerde<Inventory> inventorySerde = new JsonbSerde<>(Inventory.class);
    KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore(INVENTORY_STORE_NAME);

    public StreamsBuilder builder;

    public InventoryAggregate() {
        builder = new StreamsBuilder();
    }

    public void joinBuilder(StreamsBuilder builder) {
        this.builder = builder;
    }
    public KStream<String, Inventory> getInventoryStreams() {
        if (builder == null) {
            builder = new StreamsBuilder();
        }
        return builder.stream(inventoryStockOutputStreamName, Consumed.with(Serdes.String(), inventorySerde));
    }

    /**
     * Create a key value store named INVENTORY_STORE_NAME to persist store inventory
     * @return
     */
    public static Materialized<String, Inventory, KeyValueStore<Bytes, byte[]>> materializeAsInventoryStore() {
        return Materialized.<String, Inventory, KeyValueStore<Bytes, byte[]>>as(INVENTORY_STORE_NAME)
                .withKeySerde(Serdes.String()).withValueSerde(inventorySerde);
    }

    public void produceInventoryStockStream(KTable<String, Inventory> inventory) {
        KStream<String, Inventory> inventories = inventory.toStream();
        inventories.print(Printed.toSysOut());

        inventories.to(inventoryStockOutputStreamName, Produced.with(Serdes.String(), inventorySerde));
    }

    public List<KeyValue<String, Long>> getItemSold(String k, Inventory v) {
        List<KeyValue<String, Long>> result = new LinkedList<>();
        v.stock.forEach((k2, v2) -> result.add(new KeyValue<String, Long>(k2, -v2)));
        return result;
    }
}
