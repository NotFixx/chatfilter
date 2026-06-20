package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.detect.SwearDetector;
import nothing.chatfilter.io.FilterDataDB;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class SwearFilter implements ChatFilter {

    private static final Pattern COLLAPSE = Pattern.compile("(.)\\1{2,}");

    private final ConfigManager config;
    private final SwearDetector detector;

    public SwearFilter(ConfigManager config, FilterDataDB filterData) {
        this.config = config;
        this.detector = new SwearDetector(config, filterData);
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (!config.isSwearDetectionEnabled()) return FilterResult.PASS;
        String collapsed = COLLAPSE.matcher(normalized).replaceAll("$1$1");
        if (detector.matches(collapsed)) {
            return FilterResult.block(config.getSwearBlockMessage(), "Swear");
        }
        if (detector.isObfuscatedSwear(collapsed)) {
            return FilterResult.block(config.getSwearBlockMessage(), "Swear");
        }
        return FilterResult.PASS;
    }
}

