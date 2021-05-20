package ibm.gse.eda.inventory.infra;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import ibm.gse.eda.inventory.domain.ItemTransaction;

/**
 * Represents the input stream of the items sold/restock in store.
 * The name of the items topic is configured externally and injected
 * The Event is in JSON format
 */
@ApplicationScoped
public class ItemStream {

    @Inject
    @ConfigProperty(name="items.topic")
    public String itemSoldInputStreamName;
    
    
    public StreamsBuilder builder;
      
    public ItemStream(){
        builder = new StreamsBuilder();
    }

    public KStream<String,ItemTransaction> getItemStreams(){
        return builder.stream(itemSoldInputStreamName, 
                        Consumed.with(Serdes.String(), ItemTransaction.itemTransactionSerde));
    }

	public Topology run() {
		return builder.build();
    }
    
	public static Grouped<String, ItemTransaction> buildGroupDefinitionType() {
		return Grouped.with(Serdes.String(),ItemTransaction.itemTransactionSerde);
    }
    
}
