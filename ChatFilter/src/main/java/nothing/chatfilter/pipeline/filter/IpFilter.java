package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.detect.IpDetector;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

public class IpFilter implements ChatFilter {

    private final ConfigManager config;
    private final IpDetector detector;

    public IpFilter(ConfigManager config) {
        this.config = config;
        this.detector = new IpDetector(config);
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (!config.isIpDetectionEnabled()) return FilterResult.PASS;
        if (detector.matches(original)) {
            return FilterResult.block(config.getIpBlockMessage(), "IP");
        }
        return FilterResult.PASS;
    }
}

