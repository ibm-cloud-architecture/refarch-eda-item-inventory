package ibm.gse.eda.inventory.infra.api;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import ibm.gse.eda.inventory.infra.api.dto.ItemCountQueryResult;
import ibm.gse.eda.inventory.infra.api.dto.PipelineMetadata;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;


@ApplicationScoped
@Path("/api/v1/items")
public class InventoryResource {
    private static final Logger LOG = Logger.getLogger(InventoryResource.class.getName()); 
    private final Client client = ClientBuilder.newBuilder().build();

    @Inject
    public ItemCountQueries itemQueries;

    @GET
    @Path("/{itemID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ItemCountQueryResult> getItemStock(@PathParam("itemID") String itemID){
        ItemCountQueryResult result = itemQueries.getItemGlobalStock(itemID);
        if (result.getResult().isPresent()) {
            LOG.info(itemID + " has " + result.getResult().get());
            return Uni.createFrom().item(result);
        } else if (result.getHost().isPresent()) {
            LOG.info("data is remote on " + result.getHost());
            // this is a questionable implementation. here for demo purpose.
            return queryRemoteItemCount(result.getHost().get(), result.getPort().getAsInt(), itemID);
        } else {
            return Uni.createFrom().item(ItemCountQueryResult.notFound());
        }
    }

    @GET
    @Path("/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<PipelineMetadata> getItemMetaData() {
        return Multi.createFrom().items(itemQueries.getItemCountStoreMetadata().stream());
    }

    private Uni<ItemCountQueryResult> queryRemoteItemCount(final String host, final int port, String itemID) {
        String url = String.format("http://%s:%d/api/v1/items/%s", host, port, itemID);
        LOG.info("Data found on " + url);
        // System.out.println(url);
        ItemCountQueryResult rep = client.target(url)
                .request(MediaType.APPLICATION_JSON)
                .get(ItemCountQueryResult.class);
        return Uni.createFrom().item(rep);
    }
}