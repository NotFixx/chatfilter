package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class ColorCodeFilter implements ChatFilter {

    private static final Pattern CODES = Pattern.compile(
            "[&Â§](?:[0-9a-fk-or]|x(?:[&Â§][0-9a-fA-F]){6})",
            Pattern.CASE_INSENSITIVE
    );

    private final ConfigManager config;

    public ColorCodeFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isColorCodeFilterDisabled()) return FilterResult.PASS;
        if (CODES.matcher(original).find()) {
            return FilterResult.block(config.getColorCodeBlockMessage(), "Color Code");
        }
        return FilterResult.PASS;
    }
}

