package nothing.chatfilter.pipeline;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ChatFilter {
    FilterResult filter(Player player, String original, String normalized);
}

