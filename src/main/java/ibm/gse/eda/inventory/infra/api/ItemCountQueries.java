package ibm.gse.eda.inventory.infra.api;

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

import ibm.gse.eda.inventory.domain.ItemInventory;
import ibm.gse.eda.inventory.domain.ItemProcessingAgent;
import ibm.gse.eda.inventory.infra.api.dto.ItemCountQueryResult;
import ibm.gse.eda.inventory.infra.api.dto.PipelineMetadata;

@ApplicationScoped
public class ItemCountQueries {

    private static final Logger LOG = Logger.getLogger(ItemCountQueries.class);
   
    @ConfigProperty(name = "hostname")
    String host;

    @Inject
    KafkaStreams streams;

    public List<PipelineMetadata> getItemCountStoreMetadata() {
        return streams.allMetadataForStore(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME)
                .stream()
                .map(m -> new PipelineMetadata(
                        m.hostInfo().host() + ":" + m.hostInfo().port(),
                        m.topicPartitions()
                                .stream()
                                .map(TopicPartition::toString)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toList());
    }

    public ItemCountQueryResult getItemGlobalStock(String itemID) {
        KeyQueryMetadata metadata = null;
        LOG.warnv("Search metadata for key {0}", itemID);
        try {
            metadata = streams.queryMetadataForKey(
                ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME,
                itemID,
                Serdes.String().serializer());
        } catch (Exception e) {
            e.printStackTrace();
            return ItemCountQueryResult.notFound();
        }
        if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
            LOG.warnv("Found no metadata for key {0}", itemID);
            return ItemCountQueryResult.notFound();
        } else if (metadata.getActiveHost().host().equals(host)) {
            LOG.infov("Found data for key {0} locally", itemID);
            ItemInventory result = getItemStockStore().get(itemID);

            if (result != null) {
                return ItemCountQueryResult.found(result);
            } else {
                return ItemCountQueryResult.notFound();
            }
        } else {
            LOG.infov("Found data for key {0} on remote host {1}:{2}", itemID, metadata.getActiveHost().host(), metadata.getActiveHost().port());
            return ItemCountQueryResult.foundRemotely(metadata.getActiveHost());
        }
    }

    private ReadOnlyKeyValueStore<String, ItemInventory> getItemStockStore() {
        while (true) {
            try {
                StoreQueryParameters<ReadOnlyKeyValueStore<String,ItemInventory>> parameters = 
                    StoreQueryParameters.fromNameAndType(ItemProcessingAgent.ITEMS_STOCK_KAFKA_STORE_NAME,
                                                        QueryableStoreTypes.keyValueStore());
                return streams.store(parameters);
             } catch (InvalidStateStoreException e) {
                // ignore, store not ready yet
            }
        }
    }
}