package ibm.gse.eda.inventory.app;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.event.Observes;
import javax.ws.rs.core.Application;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.streams.StreamsConfig;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.kafka.streams.runtime.KafkaStreamsPropertiesUtil;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.ConfigProvider;

@OpenAPIDefinition(
    tags = {
            @Tag(name="eda", description="IBM Event Driven Architecture labs"),
            @Tag(name="labs", description="Inventory end to end solution")
    },
    info = @Info(
        title="Item aggregators API",
        version = "1.0.0",
        contact = @Contact(
            name = "IBM Garage Solution Engineering",
            url = "http://https://ibm-cloud-architecture.github.io/refarch-eda-item-inventory/"),
        license = @License(
            name = "Apache 2.0",
            url = "http://www.apache.org/licenses/LICENSE-2.0.html"))
)
public class ItemAggregatorApplication extends Application {
    void onStart(@Observes StartupEvent ev){
        Properties kafkaStreamsProperties = getStreamsProperties();
        for (Object s : kafkaStreamsProperties.keySet()) {
            System.out.println("kstream prop key:" + s.toString() + " value: " + kafkaStreamsProperties.getProperty(s.toString()));
        }
        Admin kafkaAdminClient = Admin.create(getAdminClientConfig(kafkaStreamsProperties));
        ListTopicsResult topics = kafkaAdminClient.listTopics();
        Set<String> existingTopics;
        try {
            existingTopics = topics.names().get(10, TimeUnit.SECONDS);
            for (String s : existingTopics) {
                System.out.println("admin client topic: " + s);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    Properties getStreamsProperties(){
       
        Properties streamsProperties = KafkaStreamsPropertiesUtil.quarkusKafkaStreamsProperties();
        String bootstrapServersConfig = ConfigProvider.getConfig().getOptionalValue("kafka.bootstrap.servers", String.class)
                    .orElse("localhost:9092");
        streamsProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServersConfig);
        return streamsProperties;
    }

    private static Properties getAdminClientConfig(Properties properties) {
        Properties adminClientConfig = new Properties(properties);
        // include other AdminClientConfig(s) that have been configured
        for (final String knownAdminClientConfig : AdminClientConfig.configNames()) {
            // give preference to admin. first
            if (properties.containsKey(StreamsConfig.ADMIN_CLIENT_PREFIX + knownAdminClientConfig)) {
                adminClientConfig.put(knownAdminClientConfig,
                        properties.get(StreamsConfig.ADMIN_CLIENT_PREFIX + knownAdminClientConfig));
            } else if (properties.containsKey(knownAdminClientConfig)) {
                adminClientConfig.put(knownAdminClientConfig, properties.get(knownAdminClientConfig));
            }
        }
        return adminClientConfig;
    }
}

