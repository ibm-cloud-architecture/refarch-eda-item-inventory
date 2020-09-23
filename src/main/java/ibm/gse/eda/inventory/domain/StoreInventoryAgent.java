package ibm.gse.eda.inventory.domain;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

import ibm.gse.eda.inventory.infrastructure.InventoryAggregate;
import ibm.gse.eda.inventory.infrastructure.ItemStream;

/**
 * The agent process item from the event stream and build an inventory aggregation 
 * per store. It accumulates sale quantity per item 
 * and stock amount per store per item
 */
@ApplicationScoped
public class StoreInventoryAgent {
    
    @Inject
    public ItemStream itemStream;

    @Inject
    public InventoryAggregate inventoryAggregate;

    /**
     * The topology process the items stream into two different paths: one
     * to compute the sum of items sold per item-id, the other to compute
     * the inventory per store. An app can have one topology.
     **/  
    @Produces
    public Topology processItemStream(){
        KStream<String,Item> items = itemStream.getItemStreams();     
        // process items and aggregate at the store level 
        KTable<String,Inventory> inventory = items
            // use store name as key
            .groupByKey(ItemStream.buildGroupDefinition())
            // update the current stock for this store - item pair
            // change the value type
            .aggregate(
                () ->  new Inventory(), // initializer
                (k , newItem, existingInventory) 
                    -> existingInventory.updateStockQuantity(k,newItem), 
                    InventoryAggregate.materializeAsInventoryStore());       
        inventoryAggregate.produceInventoryStockStream(inventory);
        
        accumulateGlobalItemCount();
        return itemStream.run();
    }


    public void accumulateGlobalItemCount(){
        inventoryAggregate.joinBuilder(itemStream.builder);
        inventoryAggregate.getInventoryStreams()
        .flatMap( ( k, v) ->  inventoryAggregate.getItemSold(k,v))
        .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
        .reduce(Long::sum,
                ItemStream.materializeAsItemGlobalCountStore());
    }
}