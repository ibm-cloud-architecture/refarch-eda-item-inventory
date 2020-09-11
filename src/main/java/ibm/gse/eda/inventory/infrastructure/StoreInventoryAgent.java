package ibm.gse.eda.inventory.infrastructure;

import java.util.LinkedList;
import java.util.List;

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
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
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


    /*
    This code may be used when we need to start the topology when the application start.
    the current problem of this code is that it has to define config by code. And not leverage CDI
    public void onStart(@Observes StartupEvent event) {
        final StreamsBuilder builder = new StreamsBuilder();
        Topology t = buildTopology(builder);
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, ConfigProvider.getConfig().getValue("quarkus.kafka-streams.application-id",String.class));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, ConfigProvider.getConfig().getValue("quarkus.kafka-streams.bootstrap-servers", String.class));
       
        streams = new KafkaStreams(t, props);
        streams.start();
    }

    void onStop(@Observes ShutdownEvent event) {
        streams.close();
    }
*/

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
            .map((k,v) ->  new KeyValue<>(v.storeName, v))
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
            inventory.toStream()
            .to(inventoryStockTopicName,
                Produced.with(Serdes.String(),inventorySerde));
      
            // accumulate the sum of items sold
            builder.stream(inventoryStockTopicName, Consumed.with(Serdes.String(), inventorySerde))
                .flatMap( (k,v) ->  { 
                    List<KeyValue<String,Long>> result = new LinkedList<>();
                    v.stock.forEach( (k2,v2) ->  result.add(new KeyValue<String,Long>(k2,-v2)));
                    return result;
                    } )
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .reduce(Long::sum,
                    Materialized.<String,Long,KeyValueStore<Bytes,byte[]>>as(ITEMS_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.Long()));
        return builder.build();
    }


  
    
}