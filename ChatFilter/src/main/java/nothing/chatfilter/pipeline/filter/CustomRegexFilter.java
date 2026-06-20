package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

public class CustomRegexFilter implements ChatFilter {

    private final ConfigManager config;

    public CustomRegexFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        List<ConfigManager.CustomRule> rules = config.getCustomRules();
        if (rules == null || rules.isEmpty()) return FilterResult.PASS;

        for (ConfigManager.CustomRule rule : rules) {
            if (!rule.enabled()) continue;
            if (rule.pattern().matcher(normalized).find()) {
                return FilterResult.block(rule.blockMessage(), "Custom: " + rule.name());
            }
        }
        return FilterResult.PASS;
    }
}

