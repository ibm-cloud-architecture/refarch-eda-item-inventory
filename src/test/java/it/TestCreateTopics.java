package it;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.lifecycle.Startables;

public class TestCreateTopics extends KafkaTestResource {
    
    @BeforeAll
    public static void startAll() {
        Startables.deepStart(Stream.of(
        kafkaContainer)).join();    
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.getBootstrapServers());
        System.setProperty("QUARKUS_KAFKA_STREAMS_BOOTSTRAP_SERVERS", kafkaContainer.getBootstrapServers());
    }

    @AfterAll
    public static void stopAll() {
        kafkaContainer.stop();
       
    }
    
    @Test
    public void testCreateTopic() throws InterruptedException, ExecutionException{
        System.out.println(System.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        String[] topicNames = new String[]{"items","item.inventory"};
        kafkaContainer.createTopics(topicNames);
        AdminClient admin = AdminClient.create(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("KAFKA_BOOTSTRAP_SERVERS")));
        DescribeClusterResult r = admin.describeCluster();
        Assertions.assertNotNull(r);
        Assertions.assertNotNull(r.clusterId().get());
        System.out.println(r.clusterId().get().toString());
        ListTopicsResult topics = admin.listTopics();
        System.out.println(topics.listings().get().toString());
    }
}
