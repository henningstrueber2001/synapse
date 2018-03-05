package de.otto.synapse.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.openhft.chronicle.hash.ChronicleHashClosedException;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link StateRepository} implementation using a {@link ChronicleMap}.
 *
 * @see <a href="https://github.com/OpenHFT/Chronicle-Map">https://github.com/OpenHFT/Chronicle-Map</a>
 */
public class ChronicleMapStateRepository<V> implements StateRepository<V> {

    protected final AtomicLong bytesUsed = new AtomicLong(0L);

    private final Class<V> clazz;
    private final ObjectMapper objectMapper;
    private final ChronicleMap<String, String> store;

    private ChronicleMapStateRepository(Builder<V> builder) {
        clazz = builder.clazz;
        objectMapper = builder.objectMapper;
        store = builder.store;
    }

    public static <V> Builder<V> builder(Class<V> clazz) {
        return new Builder<>(clazz);
    }

    @Override
    public synchronized void put(String key, V value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            bytesUsed.addAndGet(json.length() * 2);
            String oldJson = store.put(key, json);
            if (oldJson != null) {
                bytesUsed.addAndGet(-oldJson.length() * 2);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (ChronicleHashClosedException ignore) {
            // ignore, because this should happen on shutdown only
        }
    }

    @Override
    public Optional<V> get(String key) {
        try {
            String json = store.get(key);
            if (json != null) {
                try {
                    return Optional.of(objectMapper.readValue(json, clazz));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return Optional.empty();
        } catch (ChronicleHashClosedException ignore) {
            // ignore, because this should happen on shutdown only
            return Optional.empty();
        }
    }

    @Override
    public synchronized void remove(String key) {
        try {
            String oldJson = store.remove(key);
            if (oldJson != null) {
                bytesUsed.addAndGet(-oldJson.getBytes().length * 2);
            }
        } catch (ChronicleHashClosedException ignore) {
            // ignore, because this should happen on shutdown only
        }
    }

    @Override
    public void clear() {
        bytesUsed.set(0L);
        store.clear();
    }

    /**
     * only use in tests
     */
    void close() {
        store.close();
    }

    @Override
    public Iterable<String> getKeySetIterable() {
        return store.keySet();
    }

    @Override
    public long size() {
        try {
            return store.longSize();
        } catch (ChronicleHashClosedException ignore) {
            // ignore, because this should happen on shutdown only - size is 0 for closed maps.
            return 0;
        }
    }

    @Override
    public String getStats() {
        try {
            float gbUsed = bytesUsed.floatValue() / 1024 / 1024 / 1024;
            float gbTotal = (float) store.offHeapMemoryUsed() / 1024 / 1024 / 1024;
            int percentUsed = (int) (gbUsed / gbTotal * 100);
            long avgEntrySize = store.size() == 0 ? 0 : bytesUsed.get() / store.size();
            return String.format("Cache for %s contains %s entries and has ~%.3fGB/%.3fGB (%s%%) mem used. (Avg. Entry %s bytes)", clazz.getSimpleName(), store.size(), gbUsed, gbTotal, percentUsed, avgEntrySize);
        } catch (ChronicleHashClosedException ignore) {
            // ignore, because this should happen on shutdown only
            return "Chronicle Map already closed";
        }
    }


    public static final class Builder<V> {

        private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
        private static final int DEFAULT_KEY_SIZE_BYTES = 128;
        private static final double DEFAULT_VALUE_SIZE_BYTES = 512;
        private static final long DEFAULT_ENTRY_COUNT = 1_000_00;

        private final Class<V> clazz;
        private ObjectMapper objectMapper = DEFAULT_OBJECT_MAPPER;
        private ChronicleMap<String, String> store;

        private Builder(Class<V> clazz) {
            this.clazz = clazz;
        }

        public Builder<V> withStore(ChronicleMap<String, String> val) {
            store = val;
            return this;
        }

        public Builder<V> withObjectMapper(ObjectMapper val) {
            objectMapper = val;
            return this;
        }

        public ChronicleMapStateRepository<V> build() {
            if (store == null) {
                store = ChronicleMapBuilder.of(String.class, String.class)
                        .averageKeySize(DEFAULT_KEY_SIZE_BYTES)
                        .averageValueSize(DEFAULT_VALUE_SIZE_BYTES)
                        .entries(DEFAULT_ENTRY_COUNT)
                        .create();
            }
            return new ChronicleMapStateRepository<>(this);
        }
    }
}