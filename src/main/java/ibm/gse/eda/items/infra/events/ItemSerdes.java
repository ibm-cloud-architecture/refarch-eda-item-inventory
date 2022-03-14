package ibm.gse.eda.items.infra.events;

import org.apache.kafka.common.serialization.Serde;

import ibm.gse.eda.items.domain.ItemInventory;
import ibm.gse.eda.items.domain.ItemTransaction;

public class ItemSerdes {
    
    public static Serde<ItemTransaction> ItemTransactionSerde() {
        return new JSONSerde<ItemTransaction>(ItemTransaction.class.getCanonicalName());
    }

    public static Serde<ItemInventory> ItemInventorySerde() {
        return new JSONSerde<ItemInventory>(ItemInventory.class.getCanonicalName());
    }
}
