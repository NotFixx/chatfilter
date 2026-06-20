package nothing.chatfilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.UUID;

public class PassCache {

    private final Cache<Key, Boolean> cache;

    public PassCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
    }

    public boolean isCachedPass(UUID playerId, String message) {
        Boolean result = cache.getIfPresent(new Key(playerId, message));
        return Boolean.TRUE.equals(result);
    }

    public void recordPass(UUID playerId, String message) {
        cache.put(new Key(playerId, message), Boolean.TRUE);
    }

    public void invalidatePlayer(UUID playerId) {
        cache.asMap().keySet().removeIf(k -> k.playerId.equals(playerId));
    }

    private record Key(UUID playerId, String message) {}
}

