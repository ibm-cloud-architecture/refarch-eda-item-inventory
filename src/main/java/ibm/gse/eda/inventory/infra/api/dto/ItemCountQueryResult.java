package ibm.gse.eda.inventory.infra.api.dto;

import java.util.Optional;
import java.util.OptionalInt;

import org.apache.kafka.streams.state.HostInfo;

import ibm.gse.eda.inventory.domain.ItemInventory;

public class ItemCountQueryResult {
    private static ItemCountQueryResult NOT_FOUND = new ItemCountQueryResult(null, null, null);
    private final ItemInventory result;
    private final String host;
    private final Integer port;

    public ItemCountQueryResult(){
        result = new ItemInventory();
        host="localhost";
        port=8080;
    }

    public ItemCountQueryResult(ItemInventory result, String host, Integer port) {
        this.result = result;
        this.host = host;
        this.port = port;
    }

    public static ItemCountQueryResult notFound() {
        return NOT_FOUND;
    }

    public static ItemCountQueryResult found(ItemInventory data) {
        return new ItemCountQueryResult(data, null, null);
    }

    public static ItemCountQueryResult foundRemotely(HostInfo host) {
        return new ItemCountQueryResult(null, host.host(), host.port());
    }

    public Optional<ItemInventory> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }

    public OptionalInt getPort() {
        return port != null ? OptionalInt.of(port) : OptionalInt.empty();
    }
}