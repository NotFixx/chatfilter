package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class MiniMessageFilter implements ChatFilter {

    private static final Pattern TAG = Pattern.compile(
            "<[/]?[a-z][a-z0-9_]*(?::[^>]*)?>",
            Pattern.CASE_INSENSITIVE
    );

    private final ConfigManager config;

    public MiniMessageFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isMiniMessageFilterDisabled()) return FilterResult.PASS;
        if (TAG.matcher(original).find()) {
            return FilterResult.block(config.getMiniMessageBlockMessage(), "MiniMessage");
        }
        return FilterResult.PASS;
    }
}

