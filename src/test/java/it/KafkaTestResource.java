package it;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.strimzi.test.container.StrimziKafkaContainer;

/**
 * Define a kafka broker for testing. 
 * Injected with @QuarkusTestResource to any test class.
 * This resource is started before the first test is run, 
 * and is closed at the end of the test suite.
 */
public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    private static Network network = Network.newNetwork();
    public static final StrimziKafkaContainer kafkaContainer = new StrimziKafkaContainer("cp.icr.io/cp/ibm-eventstreams-kafka:10.5.0")
    .withNetwork(network)
    .withNetworkAliases("kafka")
    .withExposedPorts(9092)
    .withKraft().waitForRunning();

    @Override
    public Map<String, String> start() {
        
        Startables.deepStart(Stream.of(kafkaContainer)).join();  
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer.getBootstrapServers());
        System.setProperty("QUARKUS_KAFKA_STREAMS_BOOTSTRAP_SERVERS", kafkaContainer.getBootstrapServers());
        HashMap<String,String> m = new HashMap<>();
        m.put("kafka.bootstrap.servers", kafkaContainer.getBootstrapServers());
        m.put("quarkus.kafka-streams.bootstrap-servers", kafkaContainer.getBootstrapServers());
        String[] topicNames = new String[]{"items","item.inventory","item-aggregator-ItemStock-repartition"};
        final AdminClient adminClient = AdminClient.create(ImmutableMap.of(
                 AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()));
        List<NewTopic> newTopics =
        Arrays.stream(topicNames)
            .map(topic -> new NewTopic(topic, 1, (short) 1))
            .collect(Collectors.toList());
       
            try {
                adminClient.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                fail();
            }
        
        
        return Collections.synchronizedMap(m);
    }

    @Override
    public void stop() {
        kafkaContainer.close();
    }
    
}