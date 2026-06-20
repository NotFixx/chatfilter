package nothing.chatfilter.pipeline;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FilterPipeline {

    private final List<ChatFilter> filters = new ArrayList<>();

    public FilterPipeline add(ChatFilter filter) {
        filters.add(filter);
        return this;
    }

    public Result execute(Player player, String original, String normalized) {
        for (ChatFilter filter : filters) {
            FilterResult fr = filter.filter(player, original, normalized);
            if (fr.decision() == FilterResult.Decision.BLOCK) {
                return new Result(fr.reason(), fr.rule());
            }
        }
        return null;
    }

    public record Result(String blockMessage, String rule) {
        public boolean blocked() { return blockMessage != null; }
    }
}

