package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.io.FilterDataDB;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public class ServerNameFilter implements ChatFilter {

    private static final Pattern COLLAPSE = Pattern.compile("(.)\\1{2,}");

    private final ConfigManager config;
    private final FilterDataDB filterData;

    public ServerNameFilter(ConfigManager config, FilterDataDB filterData) {
        this.config = config;
        this.filterData = filterData;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        String collapsed = COLLAPSE.matcher(normalized).replaceAll("$1$1");
        String blockedServer = filterData.findBlockedServerName(collapsed);
        if (blockedServer != null) {
            return FilterResult.block(
                    config.getServerNameBlockMessage().replace("%name%", blockedServer),
                    "Server Name");
        }
        return FilterResult.PASS;
    }
}

