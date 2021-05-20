package ibm.gse.eda.inventory.infra.api;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import ibm.gse.eda.inventory.infra.api.dto.InventoryQueryResult;
import ibm.gse.eda.inventory.infra.api.dto.ItemCountQueryResult;
import ibm.gse.eda.inventory.infra.api.dto.PipelineMetadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;


@ApplicationScoped
@Path("/inventory")
public class InventoryResource {
    private final Client client = ClientBuilder.newBuilder().build();

    @Inject
    public StoreInventoryQueries inventoryQueries;

    @Inject
    public ItemCountQueries itemQueries;

    @GET
    @Path("/store/{storeID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<InventoryQueryResult> getStock(@PathParam("storeID") String storeID) {
        InventoryQueryResult result = inventoryQueries.getStoreStock(storeID);
        if (result.getResult().isPresent()) {
            System.out.println("result: " + result.getResult().get().storeName);
            return Uni.createFrom().item(result);
        } else if (result.getHost().isPresent()) {
            System.out.println("data is remote on " + result.getHost());
            // this is a questionable implementation. here for demo purpose.
            return queryRemoteInventoryStore(result.getHost().get(), result.getPort().getAsInt(), storeID);
        } else {
            return Uni.createFrom().item(InventoryQueryResult.notFound());
        }
    }

    @GET
    @Path("/store/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<PipelineMetadata> getStoreMetaData() {
        return Multi.createFrom().items(inventoryQueries.getStoreInventoryStoreMetadata().stream());
    }

    @GET
    @Path("/item/{itemID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ItemCountQueryResult> getItemCount(@PathParam("itemID") String itemID){
        ItemCountQueryResult result = itemQueries.getItemGlobalStock(itemID);
        if (result.getResult().isPresent()) {
            System.out.println(itemID + " has " + result.getResult().get());
            return Uni.createFrom().item(result);
        } else if (result.getHost().isPresent()) {
            System.out.println("data is remote on " + result.getHost());
            // this is a questionable implementation. here for demo purpose.
            return queryRemoteItemCountStore(result.getHost().get(), result.getPort().getAsInt(), itemID);
        } else {
            return Uni.createFrom().item(ItemCountQueryResult.notFound());
        }
    }

    @GET
    @Path("/item/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<PipelineMetadata> getItemMetaData() {
        return Multi.createFrom().items(itemQueries.getItemCountStoreMetadata().stream());
    }

    private Uni<InventoryQueryResult> queryRemoteInventoryStore(final String host, final int port, String storeId) {
        String url = String.format("http://%s:%d//inventory/store/%s", host, port, storeId);
        System.out.println("Data found on " + url);
        // System.out.println(url);
        InventoryQueryResult rep = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .get(InventoryQueryResult.class);
        return Uni.createFrom().item(rep);
    }

    private Uni<ItemCountQueryResult> queryRemoteItemCountStore(final String host, final int port, String itemID) {
        String url = String.format("http://%s:%d//inventory/item/%s", host, port, itemID);
        System.out.println("Data found on " + url);
        // System.out.println(url);
        ItemCountQueryResult rep = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .get(ItemCountQueryResult.class);
        return Uni.createFrom().item(rep);
    }
}