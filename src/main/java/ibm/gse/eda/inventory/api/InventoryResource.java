package ibm.gse.eda.inventory.api;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ibm.gse.eda.inventory.infrastructure.InteractiveQueries;
import ibm.gse.eda.inventory.infrastructure.InventoryQueryResult;
import ibm.gse.eda.inventory.infrastructure.PipelineMetadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;


@ApplicationScoped
@Path("/inventory")
public class InventoryResource {
    private final Client client = ClientBuilder.newBuilder().build();

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
            System.out.println("data found remotly " + result.getHost());
            return fetchReeferData(result.getHost().get(), result.getPort().getAsInt(), storeID);
        } else {
            return Uni.createFrom().item(InventoryQueryResult.notFound());
        }
    }

    @GET
    @Path("/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<PipelineMetadata> getMetaData() {
        return Multi.createFrom().items(queries.getStockStoreMetaData().stream());
    }

    private Uni<InventoryQueryResult> fetchReeferData(final String host, final int port, String storeId) {
        String url = String.format("http://%s:%d//inventory/store/%s", host, port, storeId);
        System.out.println("Data found on " + url);
        // System.out.println(url);
        InventoryQueryResult rep = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .get(InventoryQueryResult.class);
        return Uni.createFrom().item(rep);
    }
}