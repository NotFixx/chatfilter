package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

public class LengthFilter implements ChatFilter {

    private final ConfigManager config;

    public LengthFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isMaxMessageLengthDisabled()) return FilterResult.PASS;
        int max = config.getMaxMessageLength();
        if (original.length() > max) {
            return FilterResult.block(
                    config.getMaxMessageBlockMessage().replace("%max%", String.valueOf(max)),
                    "Message Too Long");
        }
        return FilterResult.PASS;
    }
}

