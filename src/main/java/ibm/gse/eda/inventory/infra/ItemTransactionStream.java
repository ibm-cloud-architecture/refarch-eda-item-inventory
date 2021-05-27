package ibm.gse.eda.inventory.infra;

import java.util.Optional;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.eclipse.microprofile.config.ConfigProvider;

import ibm.gse.eda.inventory.domain.ItemTransaction;

/**
 * Represents the input stream of the items sold/restock in store.
 * The name of the items topic is configured externally
 * The Event is in JSON format
 */
public class ItemTransactionStream {

    public String itemSoldInputStreamName = "items";
        
    public StreamsBuilder builder;
      
    public ItemTransactionStream(){
        builder = new StreamsBuilder();
        Optional<String> v =ConfigProvider.getConfig().getOptionalValue("app.items.topic", String.class);
        if (v.isPresent()) {
            this.itemSoldInputStreamName = v.get();
        }
    }

    public KStream<String,ItemTransaction> getItemStreams(){
        return builder.stream(itemSoldInputStreamName, 
                        Consumed.with(Serdes.String(), ItemTransaction.itemTransactionSerde));
    }

	public Topology run() {
		return builder.build();
    }
}
