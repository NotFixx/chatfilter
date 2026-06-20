package nothing.chatfilter.pipeline.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RateLimitFilter implements ChatFilter {

    private final ConfigManager config;
    private final Cache<UUID, Queue<Long>> messageTimes;

    public RateLimitFilter(ConfigManager config) {
        this.config = config;
        this.messageTimes = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isRateLimitDisabled()) return FilterResult.PASS;
        if (player == null) return FilterResult.PASS;
        int max = config.getRateLimitMaxMessages();
        int window = config.getRateLimitWindowSeconds();
        if (max <= 0 || window <= 0) return FilterResult.PASS;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cutoff = now - (window * 1000L);

        Queue<Long> times = messageTimes.get(id, k -> new ConcurrentLinkedQueue<>());
        times.add(now);

        Long oldest;
        while ((oldest = times.peek()) != null && oldest < cutoff) {
            times.poll();
        }

        if (times.size() > max) {
            return FilterResult.block(config.getRateLimitBlockMessage(), "Rate Limit");
        }
        return FilterResult.PASS;
    }
}

