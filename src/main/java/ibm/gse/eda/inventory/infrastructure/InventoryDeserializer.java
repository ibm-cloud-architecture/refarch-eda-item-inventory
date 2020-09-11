package ibm.gse.eda.inventory.infrastructure;

import ibm.gse.eda.inventory.domain.Inventory;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class InventoryDeserializer extends JsonbDeserializer<Inventory> {
 
    public InventoryDeserializer() {
        super(Inventory.class);
    }
}