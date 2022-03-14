package it;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.ImmutableMap;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.strimzi.test.container.StrimziKafkaCluster;


public class TestGetCluster {

    StrimziKafkaCluster strimziKafkaContainer;
    AdminClient adminClient;
    int numberOfReplicas;
    final String recordKey = "Item_01";
    final String recordValue = "{\"StoreName\":\"store_1\",\"quantity\": 1}";

    @BeforeEach
    void setUp() {
        final int numberOfBrokers = 1;
        numberOfReplicas = numberOfBrokers;

        strimziKafkaContainer = new StrimziKafkaCluster(numberOfBrokers);
        strimziKafkaContainer.start();
    }

    @AfterEach
    void tearDown() {
        strimziKafkaContainer.stop();
    }

    @Test
    public void verifyStartProduceConsume() {
        final String topicName = "items";
        final Collection<NewTopic> topics = Collections.singletonList(new NewTopic(topicName, 1, (short) 1));
        try {
            System.out.println("BOOTSTRAP:" + strimziKafkaContainer.getBootstrapServers());
            adminClient = AdminClient.create(ImmutableMap.of(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, strimziKafkaContainer.getBootstrapServers()));
            DescribeClusterResult r = adminClient.describeCluster();
            Assertions.assertNotNull(r);
            Assertions.assertNotNull(r.clusterId().get());
            System.out.println("Cluster ID: " + r.clusterId().get().toString());

            System.out.println("Create topic");
            
            adminClient.createTopics(topics).all().get(30, TimeUnit.SECONDS);


            KafkaProducer<String, String> producer = new KafkaProducer<>(
                 ImmutableMap.of(
                     ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, strimziKafkaContainer.getBootstrapServers(),
                     ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString()
                 ),
                 new StringSerializer(),
                 new StringSerializer()
             );
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                 ImmutableMap.of(
                     ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, strimziKafkaContainer.getBootstrapServers(),
                     ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                     ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  OffsetResetStrategy.EARLIEST.name().toLowerCase(Locale.ROOT)
                 ),
                 new StringDeserializer(),
                 new StringDeserializer()
             );
             consumer.subscribe(Collections.singletonList(topicName));
             System.out.println("Send message -> " + recordValue);
             producer.send(new ProducerRecord<>(topicName, recordKey, recordValue)).get();

             Utils.waitFor("Consumer records are present", Duration.ofSeconds(10).toMillis(), Duration.ofMinutes(2).toMillis(),
             () -> {
                 ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                 if (records.isEmpty()) {
                     return false;
                 }

                 // verify count
                 assertThat(records.count(), is(1));

                 ConsumerRecord<String, String> consumerRecord = records.records(topicName).iterator().next();

                 // verify content of the record
                 assertThat(consumerRecord.topic(), is(topicName));
                 assertThat(consumerRecord.key(), is(recordKey));
                 assertThat(consumerRecord.value(), is(recordValue));

                 return true;
             });
             
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            fail();
        }
    }
}
