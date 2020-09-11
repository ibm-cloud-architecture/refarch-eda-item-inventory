package it;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.KafkaContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Define a kafka broker for testing. 
 * Injected with @QuarkusTestResource to any test class.
 * This resource is started before the first test is run, 
 * and is closed at the end of the test suite.
 */
public class KafkaResource implements QuarkusTestResourceLifecycleManager {

    private final KafkaContainer kafka = new KafkaContainer();

    @Override
    public Map<String, String> start() {
        kafka.start();
        HashMap<String,String> m = new HashMap<>();
        m.put("kafka.bootstrap.servers", kafka.getBootstrapServers());
        m.put("quarkus.kafka-streams.bootstrap-servers", kafka.getBootstrapServers());
        return Collections.synchronizedMap(m);
    }

    @Override
    public void stop() {
        kafka.close();
    }
    
}