package nothing.chatfilter.gate;

import nothing.chatfilter.command.BypassCommand;
import nothing.chatfilter.io.WhitelistManager;
import org.bukkit.entity.Player;

public class BypassGate {
    private final WhitelistManager whitelistManager;
    private final BypassCommand bypassCommand;

    public BypassGate(WhitelistManager whitelistManager, BypassCommand bypassCommand) {
        this.whitelistManager = whitelistManager;
        this.bypassCommand = bypassCommand;
    }

    public boolean shouldBypass(Player player) {
        if (player == null) return false;

        // 1. Inâ€‘memory toggle (set by /filterbypass or automatically for OPs on join)
        if (bypassCommand.isBypassed(player.getUniqueId())) return true;

        // 2. Whitelist
        return whitelistManager.isWhitelisted(player.getUniqueId());

        // 3. The old "ops bypass" config option now ONLY controls autoâ€‘toggle on join,
        //    NOT direct bypass. We remove the direct OP check here.
    }
}
