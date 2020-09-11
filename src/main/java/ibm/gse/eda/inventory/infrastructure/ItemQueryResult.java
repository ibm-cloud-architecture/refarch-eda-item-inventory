package ibm.gse.eda.inventory.infrastructure;

import java.util.Optional;
import java.util.OptionalInt;

import org.apache.kafka.streams.state.HostInfo;

public class ItemQueryResult {
    private static ItemQueryResult NOT_FOUND = new ItemQueryResult(null, null, null);
    private final Long result;
    private final String host;
    private final Integer port;

    public ItemQueryResult(Long result, String host, Integer port) {
        this.result = result;
        this.host = host;
        this.port = port;
    }

    public static ItemQueryResult notFound() {
        return NOT_FOUND;
    }

    public static ItemQueryResult found(Long data) {
        return new ItemQueryResult(data, null, null);
    }

    public static ItemQueryResult foundRemotely(HostInfo host) {
        return new ItemQueryResult(null, host.host(), host.port());
    }

    public Optional<Long> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }

    public OptionalInt getPort() {
        return port != null ? OptionalInt.of(port) : OptionalInt.empty();
    }
}