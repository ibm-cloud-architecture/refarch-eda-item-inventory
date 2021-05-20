package ibm.gse.eda.inventory.infra;

import ibm.gse.eda.inventory.domain.StoreInventory;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class InventoryDeserializer extends JsonbDeserializer<StoreInventory> {
 
    public InventoryDeserializer() {
        super(StoreInventory.class);
    }
}