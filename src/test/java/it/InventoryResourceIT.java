package it;

import static io.restassured.RestAssured.given;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ibm.gse.eda.inventory.domain.ItemTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.mutiny.Multi;

@QuarkusTest
@QuarkusTestResource(KafkaResource.class)
public class InventoryResourceIT {
    
    @Outgoing("items")
    public  Multi<ItemTransaction> sendItemEventsToKafka() {
        List<ItemTransaction> items = new ArrayList<ItemTransaction>();
        items.add(new ItemTransaction("Store-1","Item-1",ItemTransaction.RESTOCK,5));
        items.add(new ItemTransaction("Store-1","Item-1",ItemTransaction.SALE,2,30.0));
        return Multi.createFrom().iterable(items);
    }
    
    @BeforeEach
    public void setup(){
        // send items to kafka
        sendItemEventsToKafka();
    }
    
    @Test
    public void shouldGetOneInventory(){
        Response r = given().headers("Content-Type", ContentType.JSON, "Accept", ContentType.JSON)
        .when()
        .get("/inventory/Store-1/Item-1")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .extract()
        .response();

        System.out.println(r.jsonPath());
    }
}