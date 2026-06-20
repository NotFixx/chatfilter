package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class RepeatedCharFilter implements ChatFilter {

    private final ConfigManager config;
    private final AtomicInteger lastMaxRepeats = new AtomicInteger(-1);
    private volatile Pattern pattern;

    public RepeatedCharFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isRepeatedCharSpamDisabled()) return FilterResult.PASS;
        int max = config.getMaxRepeatedChars();
        if (max <= 0) return FilterResult.PASS;

        Pattern p = pattern;
        if (lastMaxRepeats.get() != max) {
            p = Pattern.compile("(.)\\1{" + max + ",}");
            pattern = p;
            lastMaxRepeats.set(max);
        }
        if (p.matcher(original).find()) {
            return FilterResult.block(config.getRepeatedCharBlockMessage(), "Repeated Character Spam");
        }
        return FilterResult.PASS;
    }
}

