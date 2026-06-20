package nothing.chatfilter.command;

import nothing.chatfilter.ChatFilterPlugin;
import nothing.chatfilter.io.PlayerStatsDB;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FilterCommand implements CommandExecutor, TabCompleter {

    // â”€â”€ Constants â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static final int PAGE_SIZE = 10;

    // Allocated once at class load â€” never mutated
    private static final List<String> ROOT_SUBS = List.of(
            "test", "stats", "top", "reload", "whitelist",
            "addword", "removeword", "listwords",
            "addwordbasic", "removewordbasic",
            "addserver", "removeserver", "listservers", "history");

    private static final List<String> WHITELIST_SUBS = List.of("addplayer", "removeplayer", "list");

    // â”€â”€ Fields â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private final ChatFilterPlugin plugin;
    private final MiniMessage mm     = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public FilterCommand(ChatFilterPlugin plugin) {
        this.plugin = plugin;
    }

    // â”€â”€ Dispatch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "test"         -> handleTest(sender, args);
            case "stats"        -> handleStats(sender, args);
            case "top"          -> handleTop(sender, args);
            case "reload"       -> handleReload(sender);
            case "whitelist"    -> handleWhitelist(sender, args);
            case "addword"         -> handleAddWord(sender, args);
            case "removeword"      -> handleRemoveWord(sender, args);
            case "addwordbasic"    -> handleAddWordBasic(sender, args);
            case "removewordbasic" -> handleRemoveWordBasic(sender, args);
            case "listwords"       -> handleListWords(sender);
            case "addserver"    -> handleAddServer(sender, args);
            case "removeserver" -> handleRemoveServer(sender, args);
            case "listservers"  -> handleListServers(sender);
            case "history"      -> handleHistory(sender, args);
            default -> sendMsg(sender,
                    "<red>Unknown subcommand. Use: " + String.join(", ", ROOT_SUBS) + "</red>");
        }
        return true;
    }

    // â”€â”€ Subcommand handlers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void handleTest(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.test")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter test <message></red>"); return; }

        String msg    = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String result = plugin.processMessage(msg, null);
        if (result == null) sendMsg(sender, "<green>Allowed â€“ message would pass the filter.</green>");
        else                sendLegacy(sender, "&cBlocked: " + result);
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.stats")) return;

        if (args.length >= 2) {
            UUID targetUuid = resolvePlayerUuid(args[1]);
            if (targetUuid == null) {
                sendMsg(sender, "<red>Player not found.</red>");
                return;
            }
            String targetName = args[1];

            PlayerStatsDB.PlayerStats stats = plugin.getPlayerStatsDB().getPlayerStats(targetUuid);
            if (stats == null || stats.violationCount() == 0) {
                sendLegacy(sender, plugin.getConfigManager().getStatsPlayerNoData());
                return;
            }
            var cfg = plugin.getConfigManager();
            sendLegacy(sender, cfg.getStatsPlayerHeader().replace("%player%", targetName));
            sendLegacy(sender, cfg.getStatsPlayerViolations()
                    .replace("%violations%", String.valueOf(stats.violationCount())));
            sendLegacy(sender, cfg.getStatsPlayerLastViolation()
                    .replace("%last%", stats.lastViolation() != null ? stats.lastViolation() : "never"));
            return;
        }

        long   checked = plugin.getMessagesChecked();
        long   blocked = plugin.getMessagesBlocked();
        double rate    = checked > 0 ? 100.0 * blocked / checked : 0;
        var    cfg     = plugin.getConfigManager();

        sendLegacy(sender, cfg.getStatsTotalHeader());
        sendLegacy(sender, cfg.getStatsTotalChecked().replace("%checked%", String.valueOf(checked)));
        sendLegacy(sender, cfg.getStatsTotalBlocked().replace("%blocked%", String.valueOf(blocked)));
        sendLegacy(sender, cfg.getStatsTotalBlockRate().replace("%rate%", String.format("%.2f", rate)));
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.stats")) return;

        int page   = parsePage(args, 1);
        int offset = (page - 1) * PAGE_SIZE;
        List<PlayerStatsDB.TopViolator> topList =
                plugin.getPlayerStatsDB().getTopViolators(PAGE_SIZE, offset);

        if (topList.isEmpty()) {
            sendLegacy(sender, plugin.getConfigManager().getStatsTopNoData());
            return;
        }
        String entryTemplate = plugin.getConfigManager().getStatsTopEntry();
        sendLegacy(sender, plugin.getConfigManager().getStatsTopHeader()
                .replace("%page%", String.valueOf(page)));

        int rank = offset + 1;
        for (PlayerStatsDB.TopViolator entry : topList) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.uuid()).getName())
                    .orElse(entry.uuid().toString().substring(0, 8));
            sendLegacy(sender, entryTemplate
                    .replace("%rank%",       String.valueOf(rank++))
                    .replace("%player%",     name)
                    .replace("%violations%", String.valueOf(entry.violationCount()))
                    .replace("%last%",       entry.lastViolation() != null ? entry.lastViolation() : "never"));
        }
    }

    // â”€â”€ Whitelist Subcommand â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.whitelist.manage")) return;
        if (args.length < 2) {
            sendMsg(sender, "<red>Usage: /filter whitelist <addplayer|removeplayer|list> [player]</red>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "addplayer" -> {
                Player target = resolveOnlinePlayer(sender, args);
                if (target == null) return;
                plugin.getWhitelistManager().add(target.getUniqueId());
                sendMsg(sender, "<green>Added <white><player></white> to whitelist.</green>",
                        Placeholder.unparsed("player", target.getName()));
            }
            case "removeplayer" -> {
                Player target = resolveOnlinePlayer(sender, args);
                if (target == null) return;
                plugin.getWhitelistManager().remove(target.getUniqueId());
                sendMsg(sender, "<green>Removed <white><player></white> from whitelist.</green>",
                        Placeholder.unparsed("player", target.getName()));
            }
            case "list" -> {
                List<UUID> uuids = plugin.getWhitelistManager().list();
                List<String> names = new ArrayList<>(uuids.size());
                for (UUID id : uuids) {
                    Player p = plugin.getServer().getPlayer(id);
                    // Escape so a player named "<red>foo</red>" can't inject tags
                    names.add(p != null ? mm.escapeTags(p.getName()) : id.toString());
                }
                sendMsg(sender, "<green>Whitelisted players: " + String.join(", ", names) + "</green>");
            }
            default -> sendMsg(sender, "<red>Unknown whitelist action.</red>");
        }
    }

    private void handleReload(CommandSender sender) {
        if (noPermission(sender, "filter.reload")) return;
        plugin.getConfigManager().reload();
        plugin.rebuildPipeline();
        plugin.rebuildCachedSound();
        plugin.rebuildAuditLogger();
        sendMsg(sender, "<green>Configuration reloaded.</green>");
    }

    private void handleAddWord(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.slur.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter addword <word></red>"); return; }

        String word = args[1].toLowerCase();
        plugin.getFilterDataDB().addSwearWord(word);
        plugin.rebuildPipeline();
        sendMsg(sender, "<green>Added swear word: <white><word></white></green>",
                Placeholder.unparsed("word", word));
    }

    private void handleRemoveWord(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.slur.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter removeword <word></red>"); return; }

        String word = args[1].toLowerCase();
        if (!plugin.getFilterDataDB().swearWordExists(word)) {
            sendMsg(sender, "<red>That word is not in the swear list.</red>"); return;
        }
        plugin.getFilterDataDB().removeSwearWord(word);
        plugin.rebuildPipeline();
        sendMsg(sender, "<green>Removed swear word: <white><word></white></green>",
                Placeholder.unparsed("word", word));
    }

    private void handleAddWordBasic(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.slur.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter addwordbasic <word></red>"); return; }

        String word = args[1].toLowerCase();
        plugin.getFilterDataDB().addBasicSwearWord(word);
        plugin.rebuildPipeline();
        sendMsg(sender, "<green>Added basic (non-fuzzy) swear word: <white><word></white></green>",
                Placeholder.unparsed("word", word));
    }

    private void handleRemoveWordBasic(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.slur.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter removewordbasic <word></red>"); return; }

        String word = args[1].toLowerCase();
        if (!plugin.getFilterDataDB().basicSwearWordExists(word)) {
            sendMsg(sender, "<red>That word is not in the basic swear list.</red>"); return;
        }
        plugin.getFilterDataDB().removeBasicSwearWord(word);
        plugin.rebuildPipeline();
        sendMsg(sender, "<green>Removed basic swear word: <white><word></white></green>",
                Placeholder.unparsed("word", word));
    }

    private void handleListWords(CommandSender sender) {
        if (noPermission(sender, "filter.slur.manage")) return;
        sendMsg(sender, "<green>Swear words: <white><words></white></green>",
                Placeholder.unparsed("words",
                        String.join(", ", plugin.getFilterDataDB().getAllSwearWords())));
    }

    private void handleAddServer(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.servers.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter addserver <name></red>"); return; }

        String name = args[1].toLowerCase();
        plugin.getFilterDataDB().addServerName(name);
        sendMsg(sender, "<green>Added server name to blacklist: <white><name></white></green>",
                Placeholder.unparsed("name", name));
    }

    private void handleRemoveServer(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.servers.manage")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter removeserver <name></red>"); return; }

        String name = args[1].toLowerCase();
        if (!plugin.getFilterDataDB().serverNameExists(name)) {
            sendMsg(sender, "<red>That server name is not in the blacklist.</red>"); return;
        }
        plugin.getFilterDataDB().removeServerName(name);
        sendMsg(sender, "<green>Removed server name from blacklist: <white><name></white></green>",
                Placeholder.unparsed("name", name));
    }

    private void handleListServers(CommandSender sender) {
        if (noPermission(sender, "filter.servers.manage")) return;
        sendMsg(sender, "<green>Blacklisted server names: <white><names></white></green>",
                Placeholder.unparsed("names",
                        String.join(", ", plugin.getFilterDataDB().getAllServerNames())));
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (noPermission(sender, "filter.history")) return;
        if (args.length < 2) { sendMsg(sender, "<red>Usage: /filter history <player> [page]</red>"); return; }

        UUID targetUuid = resolvePlayerUuid(args[1]);
        if (targetUuid == null) {
            sendMsg(sender, "<red>Player not found.</red>");
            return;
        }
        String targetName = args[1];

        int page   = parsePage(args, 2);
        int offset = (page - 1) * PAGE_SIZE;
        List<PlayerStatsDB.ViolationEntry> history =
                plugin.getPlayerStatsDB().getRecentViolations(targetUuid, PAGE_SIZE, offset);

        if (history.isEmpty()) {
            sendMsg(sender,
                    page > 1 ? "<yellow>No violations found for <player> on page <page>.</yellow>"
                            : "<yellow>No violations found for <player>.</yellow>",
                    Placeholder.unparsed("player", targetName),
                    Placeholder.unparsed("page",   String.valueOf(page)));
            return;
        }
        sendMsg(sender, "<gold>--- <player>'s Violation History (Page <page>) ---</gold>",
                Placeholder.unparsed("player", targetName),
                Placeholder.unparsed("page",   String.valueOf(page)));

        int rank = offset + 1;
        for (PlayerStatsDB.ViolationEntry entry : history) {
            sendMsg(sender,
                    "<red><rank>. <gray>[<time>]</gray> <white><msg></white> <dark_gray>(Reason: <reason>)</dark_gray></red>",
                    Placeholder.unparsed("rank",   String.valueOf(rank++)),
                    Placeholder.unparsed("time",   entry.timestamp()),
                    Placeholder.unparsed("msg",    entry.message()),
                    Placeholder.unparsed("reason", entry.reason()));
        }
    }

    // â”€â”€ Tab completion â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) return filterPrefix(ROOT_SUBS, args[0]);

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "whitelist"          -> filterPrefix(WHITELIST_SUBS, args[1]);
                case "stats", "history"   -> onlinePlayerNames(args[1]);
                case "removeword"         -> filterPrefix(plugin.getFilterDataDB().getAllSwearWords(),    args[1]);
                case "removewordbasic"    -> filterPrefix(plugin.getFilterDataDB().getAllBasicSwearWords(), args[1]);
                case "removeserver"       -> filterPrefix(plugin.getFilterDataDB().getAllServerNames(), args[1]);
                case "top"                -> filterPrefix(List.of("1", "2", "3"), args[1]);
                default                   -> List.of();
            };
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("whitelist") &&
                    (args[1].equalsIgnoreCase("addplayer") || args[1].equalsIgnoreCase("removeplayer")))
                return onlinePlayerNames(args[2]);

            if (args[0].equalsIgnoreCase("history"))
                return filterPrefix(List.of("1", "2", "3"), args[2]);
        }

        return List.of();
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns {@code true} and sends an error if {@code sender} lacks the permission.
     * Enables the guard pattern: {@code if (noPermission(sender, "...")) return;}
     */
    private boolean noPermission(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return false;
        sendMsg(sender, "<red>No permission.</red>");
        return true;
    }

    /** Parses a 1-based page number from {@code args[idx]}, defaulting to 1. */
    private int parsePage(String[] args, int idx) {
        if (args.length > idx) {
            try { return Math.max(1, Integer.parseInt(args[idx])); }
            catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    /**
     * Looks up an online player from the fixed expected argument index.
     * Sends the appropriate error and returns {@code null} on failure.
     */
    private Player resolveOnlinePlayer(CommandSender sender, String[] args) {
        if (args.length <= 2) {
            sendMsg(sender, "<red>Usage: /filter whitelist <addplayer|removeplayer|list> [player]</red>");
            return null;
        }
        Player target = plugin.getServer().getPlayer(args[2]);
        if (target == null) sendMsg(sender, "<red>Player not found.</red>");
        return target;
    }

    /** Resolves a player name to UUID â€” tries online, offline, then stats-DB fallback. */
    private UUID resolvePlayerUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        UUID offlineUuid = offline.getUniqueId();

        // Even if hasPlayedBefore() is false, the server may have the correct
        // UUID cached (e.g. usercache.json). Try it against our stats DB.
        if (offline.hasPlayedBefore() || plugin.getPlayerStatsDB().getPlayerStats(offlineUuid) != null)
            return offlineUuid;

        // Offline-mode UUID fallback (for offline-mode servers)
        UUID fallback = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
        if (plugin.getPlayerStatsDB().getPlayerStats(fallback) != null) return fallback;

        // Last resort: scan all known violators for a name match via UUID
        for (PlayerStatsDB.TopViolator v : plugin.getPlayerStatsDB().getTopViolators(10000, 0)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(v.uuid());
            if (op.getName() != null && op.getName().equalsIgnoreCase(name))
                return v.uuid();
        }

        return null;
    }

    private List<String> filterPrefix(List<String> source, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : source) if (s.startsWith(lower)) out.add(s);
        return out;
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getName().toLowerCase().startsWith(lower)) out.add(p.getName());
        return out;
    }

    /**
     * Deserializes and sends a MiniMessage string with optional {@link TagResolver}s.
     * Always use {@link Placeholder#unparsed} to inject user-controlled values.
     */
    private void sendMsg(CommandSender sender, String miniMessage, TagResolver... resolvers) {
        sender.sendMessage(mm.deserialize(miniMessage, resolvers));
    }

    private void sendLegacy(CommandSender sender, String legacyAmpersand) {
        sender.sendMessage(legacy.deserialize(legacyAmpersand));
    }
}
