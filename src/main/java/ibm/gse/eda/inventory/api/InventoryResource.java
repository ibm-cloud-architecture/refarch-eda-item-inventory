package ibm.gse.eda.inventory.api;

import java.net.URI;
import java.net.URISyntaxException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import ibm.gse.eda.inventory.infrastructure.InteractiveQueries;
import ibm.gse.eda.inventory.infrastructure.InventoryQueryResult;
import ibm.gse.eda.inventory.infrastructure.PipelineMetadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@Path("/inventory")
public class InventoryResource {
    
    @Inject
    public InteractiveQueries queries;

    @GET
    @Path("/store/{storeID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<InventoryQueryResult> getStock(@PathParam("storeID") String storeID) {
        InventoryQueryResult result = queries.getStoreStock(storeID);
        if (result.getResult().isPresent()) {
            System.out.println("result: " + result.getResult().get().storeName);
            return Uni.createFrom().item(result);
        } else if (result.getHost().isPresent()) {
            URI otherUri = getStoreOtherUri(result.getHost().get(), result.getPort().getAsInt(), "/inventory/store", storeID, null);
            System.out.println("host: " + otherUri.toString());
            return Uni.createFrom().item(InventoryQueryResult.notFound());
        } else {
            return  Uni.createFrom().item(InventoryQueryResult.notFound());
        }
    }

    @GET
    @Path("/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<PipelineMetadata> getMetaData() {
        return Multi.createFrom().items(queries.getStockStoreMetaData().stream());
    }

    private URI getStoreOtherUri(String host, int port, String path, String storeID, String sku) {
        try {
            if (storeID == null) {
                return new URI("http://" + host + ":" + port + path + "/" + sku);
        
            } else {
                return new URI("http://" + host + ":" + port + path + "/" + storeID);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}