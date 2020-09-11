package ibm.gse.eda.inventory.domain;

import java.util.HashMap;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Inventory {
    public String storeName;
    public HashMap<String,Long> stock = new HashMap<String,Long>();

    public Inventory(){}

    public Inventory(String storeName) {
        this.storeName = storeName;
    }

    public Inventory(String storeName, String sku, int quantity) {
        this.storeName = storeName;
        this.updateStock(sku, quantity);
    }

    public Inventory updateStockQuantity(String k, Item newValue) {
        this.storeName = k;
        if (newValue.type.equals("SALE")) 
            newValue.quantity=-newValue.quantity;
        return this.updateStock(newValue.sku,newValue.quantity);
    }

    public Inventory updateStock(String sku, long newV) {
        if (stock.get(sku) == null) {
            stock.put(sku, Long.valueOf(newV));
        } else {
            Long currentValue = stock.get(sku);
            stock.put(sku, Long.valueOf(newV) + currentValue );
        }
        return this;
    }

}