package it;

import static io.restassured.RestAssured.given;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.junit.jupiter.api.Test;

import ibm.gse.eda.items.domain.ItemTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.mutiny.Multi;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
public class TestInventoryResourceIT {
    
    @Outgoing("items")
    public  Multi<ItemTransaction> sendItemEventsToKafka() {
        List<ItemTransaction> items = new ArrayList<ItemTransaction>();
        items.add(new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5));
        items.add(new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,30.0));
        return Multi.createFrom().iterable(items);
    }
    
    
    @Test
    public void shouldGetOneInventory() throws InterruptedException{
        
                 
        System.out.println(System.getProperty("KAFKA_BOOTSTRAP_SERVERS"));

        Multi<ItemTransaction> txs= sendItemEventsToKafka();
        
        Thread.sleep(5000);
        Response r = given().headers("Content-Type", ContentType.JSON, "Accept", ContentType.JSON)
        .when()
        .get("/api/v1/items/Item-1")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .extract()
        .response();

        System.out.println(r.jsonPath().prettyPrint());
    }
}