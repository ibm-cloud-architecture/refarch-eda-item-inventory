package ibm.gse.eda.inventory.infrastructure;

import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ibm.gse.eda.inventory.domain.Inventory;

@ApplicationScoped
public class InteractiveQueries {

    private static final Logger LOG = Logger.getLogger(InteractiveQueries.class);
    
    @ConfigProperty(name = "hostname")
    String host;

    @Inject
    KafkaStreams streams;

    public List<PipelineMetadata> getStockStoreMetaData() {
        return streams.allMetadataForStore(StoreInventoryAgent.STOCKS_STORE_NAME)
                .stream()
                .map(m -> new PipelineMetadata(
                        m.hostInfo().host() + ":" + m.hostInfo().port(),
                        m.topicPartitions()
                                .stream()
                                .map(TopicPartition::toString)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toList());
    }

    public InventoryQueryResult getStoreStock(String storeID) {
        KeyQueryMetadata metadata = null;
        LOG.warnv("Search metadata for key {0}", storeID);
        try {
            metadata = streams.queryMetadataForKey(
            StoreInventoryAgent.STOCKS_STORE_NAME,
                storeID,
                Serdes.String().serializer());
        } catch (Exception e) {
            e.printStackTrace();
            return InventoryQueryResult.notFound();
        }
        if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
            LOG.warnv("Found no metadata for key {0}", storeID);
            return InventoryQueryResult.notFound();
        } else if (metadata.getActiveHost().host().equals(host)) {
            LOG.infov("Found data for key {0} locally", storeID);
            Inventory result = getStockStore().get(storeID);

            if (result != null) {
                return InventoryQueryResult.found(result);
            } else {
                return InventoryQueryResult.notFound();
            }
        } else {
            LOG.infov("Found data for key {0} on remote host {1}:{2}", storeID, metadata.getActiveHost().host(), metadata.getActiveHost().port());
            return InventoryQueryResult.foundRemotely(metadata.getActiveHost());
        }
    }

    private ReadOnlyKeyValueStore<String, Inventory> getStockStore() {
        while (true) {
            try {
                StoreQueryParameters<ReadOnlyKeyValueStore<String,Inventory>> parameters = StoreQueryParameters.fromNameAndType(StoreInventoryAgent.STOCKS_STORE_NAME,QueryableStoreTypes.keyValueStore());
                return streams.store(parameters);
             } catch (InvalidStateStoreException e) {
                // ignore, store not ready yet
            }
        }
    }

    private ReadOnlyKeyValueStore<String, Long> getItemStore() {
        while (true) {
            try {
                StoreQueryParameters<ReadOnlyKeyValueStore<String,Long>> parameters = StoreQueryParameters.fromNameAndType(StoreInventoryAgent.ITEMS_STORE_NAME,QueryableStoreTypes.keyValueStore());
                return streams.store(parameters);
             } catch (InvalidStateStoreException e) {
                // ignore, store not ready yet
            }
        }
    }

	public ItemQueryResult getItemStock(String sku) {
        KeyQueryMetadata metadata = null;
        LOG.warnv("Search metadata for key {0}", sku);
        try {
            metadata = streams.queryMetadataForKey(
            StoreInventoryAgent.ITEMS_STORE_NAME,
            sku,
            Serdes.String().serializer());
        } catch (Exception e) {
            e.printStackTrace();
            return ItemQueryResult.notFound();
        }
        if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
            LOG.warnv("Found no metadata for key {0}", sku);
            return ItemQueryResult.notFound();
        } else if (metadata.getActiveHost().host().equals(host)) {
            LOG.infov("Found data for key {0} locally", sku);
            Long result = getItemStore().get(sku);

            if (result != null) {
                return ItemQueryResult.found(result);
            } else {
                return ItemQueryResult.notFound();
            }
        } else {
            LOG.infov("Found data for key {0} on remote host {1}:{2}", sku, metadata.getActiveHost().host(), metadata.getActiveHost().port());
            return ItemQueryResult.foundRemotely(metadata.getActiveHost());
        }
	}
}