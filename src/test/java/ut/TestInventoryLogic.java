package ut;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ibm.gse.eda.inventory.domain.Inventory;
import ibm.gse.eda.inventory.domain.Item;

public class TestInventoryLogic {

    @Test
    public void shouldUpdateItemSoldQuantityTo10(){
        Item i1 = new Item();
        i1.sku = " item_1";
        i1.type = "SALE";
        i1.quantity = 10;
        Inventory inventory = new Inventory();
        Inventory out = inventory.updateStockQuantity("Store_1",i1);
        Assertions.assertEquals(-10,out.stock.get(i1.sku));
    }

    @Test
    public void shouldUpdateItemRestockQuantityTo10(){
        Item i1 = new Item();
        i1.sku = " item_1";
        i1.type = "RESTOCK";
        i1.quantity = 10;
        Inventory inventory = new Inventory();
        Inventory out = inventory.updateStockQuantity("Store_1",i1);
        Assertions.assertEquals(10,out.stock.get(i1.sku));
    }
}
