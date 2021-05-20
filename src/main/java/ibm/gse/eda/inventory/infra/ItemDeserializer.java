package ibm.gse.eda.inventory.infra;

import ibm.gse.eda.inventory.domain.ItemTransaction;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ItemDeserializer extends JsonbDeserializer<ItemTransaction> {
    public ItemDeserializer(){
        // pass the class to the parent.
        super(ItemTransaction.class);
    }
}