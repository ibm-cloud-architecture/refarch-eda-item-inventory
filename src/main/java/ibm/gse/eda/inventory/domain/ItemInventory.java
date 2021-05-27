package ibm.gse.eda.inventory.domain;

import io.quarkus.kafka.client.serialization.JsonbSerde;

/**
 * Represents the item , so it is used to accumulate the stock for a given item_ID
 */
//@RegisterForReflection
public class ItemInventory {

    public static JsonbSerde<ItemInventory> itemInventorySerde = new JsonbSerde<>(ItemInventory.class);
   
    public String itemID;
    public Long currentStock = 0L;

    public ItemInventory() {
        super();
    }

    public ItemInventory(long initStock) {
        super();
        this.currentStock = initStock;
    }

    public ItemInventory updateStockQuantityFromTransaction(String itemID, ItemTransaction tx){
        this.itemID = itemID;
        if (tx.type != null && ItemTransaction.SALE.equals(tx.type)) {
            this.currentStock-=tx.quantity;
        } else {
            this.currentStock+=tx.quantity;
        }
        return this;
    }

    public ItemInventory updateStockQuantity(String itemID,Long newValue){
        this.itemID = itemID;
        this.currentStock+=newValue;
        return this;
    }

    public ItemInventory updateStockQuantity(Long newValue){
        this.currentStock+=newValue;
        return this;
    }

    public String toString(){
        String s = "{ itemID: " + itemID + " -> " + currentStock.toString();
        return s;
    }
}
