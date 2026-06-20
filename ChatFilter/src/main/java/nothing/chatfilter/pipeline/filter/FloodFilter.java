package nothing.chatfilter.pipeline.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FloodFilter implements ChatFilter {

    private final ConfigManager config;
    private final Cache<UUID, List<CachedMessage>> recentMessages;

    public FloodFilter(ConfigManager config) {
        this.config = config;
        this.recentMessages = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isFloodDetectionDisabled() || player == null) return FilterResult.PASS;
        double threshold = config.getFloodSimilarityThreshold();
        int windowSec = config.getFloodWindowSeconds();
        int maxRepeats = config.getFloodMaxRepeats();
        if (threshold <= 0 || windowSec <= 0 || maxRepeats <= 0) return FilterResult.PASS;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cutoff = now - (windowSec * 1000L);

        List<CachedMessage> msgs = recentMessages.get(id, k -> new ArrayList<>());
        synchronized (msgs) {
            msgs.removeIf(m -> m.timestamp < cutoff);

            int similarCount = 0;
            for (CachedMessage m : msgs) {
                if (levenshteinRatio(normalized, m.normalized) >= threshold) {
                    similarCount++;
                }
            }

            msgs.add(new CachedMessage(normalized, now));

            if (similarCount >= maxRepeats) {
                return FilterResult.block(config.getFloodBlockMessage(), "Flood");
            }
        }
        return FilterResult.PASS;
    }

    private static double levenshteinRatio(String a, String b) {
        if (a == null || b == null) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        int dist = levenshtein(a, b);
        return 1.0 - ((double) dist / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int aLen = a.length();
        int bLen = b.length();
        int[] prev = new int[bLen + 1];
        int[] curr = new int[bLen + 1];
        for (int j = 0; j <= bLen; j++) prev[j] = j;
        for (int i = 1; i <= aLen; i++) {
            curr[0] = i;
            for (int j = 1; j <= bLen; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[bLen];
    }

    private record CachedMessage(String normalized, long timestamp) {}
}

