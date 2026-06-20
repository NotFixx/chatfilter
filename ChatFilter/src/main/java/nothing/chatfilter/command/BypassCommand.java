package nothing.chatfilter.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BypassCommand implements CommandExecutor, TabCompleter {

    private final Set<UUID> bypassed = ConcurrentHashMap.newKeySet();
    private Consumer<UUID> onToggle;

    private static final Component NO_PERM_SELF = Component.text(
            "You don't have permission to toggle your own bypass.", NamedTextColor.RED);
    private static final Component NO_PERM_MANAGE = Component.text(
            "You don't have permission to manage other players' bypass.", NamedTextColor.RED);
    private static final Component CONSOLE_NEEDS_PLAYER = Component.text(
            "Console must specify a player.", NamedTextColor.RED);
    private static final Component PLAYER_NOT_FOUND = Component.text(
            "Player not found.", NamedTextColor.RED);
    private static final Component USE_ON_OFF = Component.text(
            "Use on or off.", NamedTextColor.RED);

    private static final List<String> ON_OFF = List.of("on", "off");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            Player self = requireSelfPlayer(sender);
            if (self == null) return true;
            toggleBypass(sender, self);
            return true;
        }

        String arg0 = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            if (arg0.equals("on") || arg0.equals("off")) {
                Player self = requireSelfPlayer(sender);
                if (self == null) return true;
                setBypass(sender, self, arg0.equals("on"));
                return true;
            }
            if (!sender.hasPermission("filter.bypass.manage")) {
                sender.sendMessage(NO_PERM_MANAGE);
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage(PLAYER_NOT_FOUND); return true; }
            toggleBypass(sender, target);
            return true;
        }

        if (!sender.hasPermission("filter.bypass.manage")) {
            sender.sendMessage(NO_PERM_MANAGE);
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { sender.sendMessage(PLAYER_NOT_FOUND); return true; }

        String state = args[1].toLowerCase(Locale.ROOT);
        if (state.equals("on") || state.equals("off")) {
            setBypass(sender, target, state.equals("on"));
        } else {
            sender.sendMessage(USE_ON_OFF);
        }
        return true;
    }

    private Player requireSelfPlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CONSOLE_NEEDS_PLAYER);
            return null;
        }
        if (!p.hasPermission("filter.bypass")) {
            p.sendMessage(NO_PERM_SELF);
            return null;
        }
        return p;
    }

    private void toggleBypass(CommandSender sender, Player target) {
        UUID id = target.getUniqueId();
        boolean wasEnabled = bypassed.remove(id);
        if (!wasEnabled) bypassed.add(id);
        if (onToggle != null) onToggle.accept(id);
        sendBypassMessage(sender, target.getName(), !wasEnabled);
    }

    private void setBypass(CommandSender sender, Player target, boolean enabled) {
        UUID id = target.getUniqueId();
        if (enabled) bypassed.add(id); else bypassed.remove(id);
        if (onToggle != null) onToggle.accept(id);
        sendBypassMessage(sender, target.getName(), enabled);
    }

    private static void sendBypassMessage(CommandSender sender, String name, boolean enabled) {
        sender.sendMessage(Component.text(
                "Bypass " + (enabled ? "enabled" : "disabled") + " for " + name + ".",
                NamedTextColor.GREEN));
    }

    public boolean isBypassed(UUID uuid) {
        return bypassed.contains(uuid);
    }

    public void setOnToggle(Consumer<UUID> onToggle) {
        this.onToggle = onToggle;
    }

    public void enableAutoBypass(UUID uuid) {
        bypassed.add(uuid);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),
                    completions);
            StringUtil.copyPartialMatches(args[0], ON_OFF, completions);
            Collections.sort(completions);
            return completions;
        }
        if (args.length == 2 && Bukkit.getPlayer(args[0]) != null) {
            return StringUtil.copyPartialMatches(args[1], ON_OFF, new ArrayList<>());
        }
        return List.of();
    }
}
