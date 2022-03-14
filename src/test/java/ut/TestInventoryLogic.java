package ut;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ibm.gse.eda.items.domain.ItemInventory;
import ibm.gse.eda.items.domain.ItemTransaction;

public class TestInventoryLogic {

    @Test
    public void shouldUpdateItemSoldQuantityTo10(){
        ItemTransaction i1 = new ItemTransaction();
        i1.sku = "Item_1";
        i1.type = "SALE";
        i1.quantity = 10;
        ItemInventory inventory = new ItemInventory();
        ItemInventory out = inventory.updateStockQuantityFromTransaction(i1.sku,i1);
        Assertions.assertEquals(-10,out.currentStock);
    }

    @Test
    public void shouldUpdateItemRestockQuantityTo10(){
        ItemTransaction i1 = new ItemTransaction();
        i1.sku = "Item_1";
        i1.type = "RESTOCK";
        i1.quantity = 10;
        ItemInventory inventory = new ItemInventory();
        ItemInventory out = inventory.updateStockQuantityFromTransaction(i1.sku,i1);
        Assertions.assertEquals(10,out.currentStock);
    }

    @Test
    public void shouldGetRightNumberAfterStockAndResale(){
        ItemTransaction i1 = new ItemTransaction();
        i1.sku = "Item_1";
        i1.type = "RESTOCK";
        i1.quantity = 10;
        ItemInventory inventory = new ItemInventory();
        inventory.updateStockQuantityFromTransaction("Store_1",i1);
        i1.type = "SALE";
        ItemInventory out = inventory.updateStockQuantityFromTransaction(i1.sku,i1);
        Assertions.assertEquals(0,out.currentStock);
    }
}
