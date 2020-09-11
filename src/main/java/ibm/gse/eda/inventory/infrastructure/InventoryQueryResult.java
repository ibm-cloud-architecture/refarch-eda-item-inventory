package ibm.gse.eda.inventory.infrastructure;

import java.util.Optional;
import java.util.OptionalInt;

import org.apache.kafka.streams.state.HostInfo;

import ibm.gse.eda.inventory.domain.Inventory;

public class InventoryQueryResult {
    private static InventoryQueryResult NOT_FOUND = new InventoryQueryResult(null, null, null);
    private final Inventory result;
    private final String host;
    private final Integer port;

    public InventoryQueryResult(Inventory result, String host, Integer port) {
        this.result = result;
        this.host = host;
        this.port = port;
    }

    public static InventoryQueryResult notFound() {
        return NOT_FOUND;
    }

    public static InventoryQueryResult found(Inventory data) {
        return new InventoryQueryResult(data, null, null);
    }

    public static InventoryQueryResult foundRemotely(HostInfo host) {
        return new InventoryQueryResult(null, host.host(), host.port());
    }

    public Optional<Inventory> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }

    public OptionalInt getPort() {
        return port != null ? OptionalInt.of(port) : OptionalInt.empty();
    }
}