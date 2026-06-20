package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.normalize.CharacterWhitelister;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

public class CharWhitelistFilter implements ChatFilter {

    private final CharacterWhitelister whitelister;
    private final ConfigManager config;

    public CharWhitelistFilter(ConfigManager config) {
        this.whitelister = new CharacterWhitelister(config);
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (!config.isCharWhitelistEnabled()) return FilterResult.PASS;
        if (whitelister.apply(original) == null) {
            return FilterResult.block(config.getCharWhitelistBlockMessage(), "Illegal Characters");
        }
        return FilterResult.PASS;
    }
}

