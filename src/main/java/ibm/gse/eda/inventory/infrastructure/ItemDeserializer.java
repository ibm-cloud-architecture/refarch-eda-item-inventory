package ibm.gse.eda.inventory.infrastructure;

import ibm.gse.eda.inventory.domain.Item;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ItemDeserializer extends JsonbDeserializer<Item> {
    public ItemDeserializer(){
        // pass the class to the parent.
        super(Item.class);
    }
}