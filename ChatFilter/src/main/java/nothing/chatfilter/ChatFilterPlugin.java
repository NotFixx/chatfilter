package nothing.chatfilter;

import nothing.chatfilter.command.BypassCommand;
import nothing.chatfilter.command.FilterCommand;
import nothing.chatfilter.gate.BypassGate;
import nothing.chatfilter.io.FilterDataDB;
import nothing.chatfilter.io.PlayerStatsDB;
import nothing.chatfilter.io.WhitelistManager;
import nothing.chatfilter.normalize.NormalizationPipeline;
import nothing.chatfilter.pipeline.FilterPipeline;
import nothing.chatfilter.pipeline.filter.CharWhitelistFilter;
import nothing.chatfilter.pipeline.filter.DomainFilter;
import nothing.chatfilter.pipeline.filter.IpFilter;
import nothing.chatfilter.pipeline.filter.LengthFilter;
import nothing.chatfilter.pipeline.filter.ColorCodeFilter;
import nothing.chatfilter.pipeline.filter.CustomRegexFilter;
import nothing.chatfilter.pipeline.filter.FloodFilter;
import nothing.chatfilter.pipeline.filter.MiniMessageFilter;
import nothing.chatfilter.pipeline.filter.MixedScriptFilter;
import nothing.chatfilter.pipeline.filter.RateLimitFilter;
import nothing.chatfilter.pipeline.filter.RepeatedCharFilter;
import nothing.chatfilter.pipeline.filter.ServerNameFilter;
import nothing.chatfilter.pipeline.filter.SwearFilter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFilterPlugin extends JavaPlugin {

    // --- Pre-compiled patterns (compiled once, reused on every message) ---
    private static final Pattern PRIVATE_MESSAGE_COMMAND = Pattern.compile(
            "/(?:msg|tell|w|whisper|reply|r)\\s+(\\S+)\\s+(.*)", Pattern.CASE_INSENSITIVE);


    // --- Cached serializers (stateless singletons â€” safe as constants) ---
    private static final PlainTextComponentSerializer PLAIN  = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer   LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ConfigManager configManager;
    private BypassGate bypassGate;
    private WhitelistManager whitelistManager;
    private PlayerStatsDB playerStatsDB;
    private FilterDataDB filterDataDB;

    private final AtomicLong messagesChecked = new AtomicLong();
    private final AtomicLong messagesBlocked = new AtomicLong();

    private NormalizationPipeline normalizationPipeline;
    private FilterPipeline prePipeline;
    private FilterPipeline postPipeline;
    private PassCache passCache;
    private AuditLogger auditLogger;
    private BypassCommand bypassCmd;

    /**
     * Cached block sound. Null means disabled or the configured name was invalid.
     * Rebuilt on startup and whenever the config is reloaded.
     */
    private Sound cachedBlockSound;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Failed to create plugin data folder! Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        configManager    = new ConfigManager(this);
        whitelistManager = new WhitelistManager(this);
        playerStatsDB    = new PlayerStatsDB(this);
        filterDataDB     = new FilterDataDB(this);

        normalizationPipeline = new NormalizationPipeline();
        passCache = new PassCache();
        auditLogger = new AuditLogger(getDataFolder().toPath(), configManager.isAuditLogEnabled());

        rebuildPipeline();
        rebuildCachedSound();

        Bukkit.getPluginManager().registerEvents(new ChatListener(), this);
        Bukkit.getPluginManager().registerEvents(new CommandListener(), this);

        PluginCommand filterCommand = getCommand("filter");
        if (filterCommand != null) {
            FilterCommand filterCmd = new FilterCommand(this);
            filterCommand.setExecutor(filterCmd);
            filterCommand.setTabCompleter(filterCmd);
        }

        PluginCommand bypassCommand = getCommand("filterbypass");
        bypassCmd = new BypassCommand();
        bypassCmd.setOnToggle(id -> passCache.invalidatePlayer(id));
        if (bypassCommand != null) {
            bypassCommand.setExecutor(bypassCmd);
            bypassCommand.setTabCompleter(bypassCmd);
        }

        bypassGate = new BypassGate(whitelistManager, bypassCmd);

        if (configManager.isOpsBypass()) {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                    Player player = event.getPlayer();
                    if (player.isOp()) bypassCmd.enableAutoBypass(player.getUniqueId());
                }
            }, this);
        }
    }

    @Override
    public void onDisable() {
        if (playerStatsDB != null) playerStatsDB.close();
        if (filterDataDB  != null) filterDataDB.close();
        getLogger().info("ChatFilter disabled.");
    }

    // -------------------------------------------------------------------------
    // Rebuild helpers â€” call after any config reload
    // -------------------------------------------------------------------------

    public void rebuildPipeline() {
        prePipeline = new FilterPipeline()
                .add(new CharWhitelistFilter(configManager))
                .add(new MiniMessageFilter(configManager))
                .add(new ColorCodeFilter(configManager))
                .add(new LengthFilter(configManager))
                .add(new RepeatedCharFilter(configManager))
                .add(new IpFilter(configManager))
                .add(new RateLimitFilter(configManager))
                .add(new FloodFilter(configManager));

        postPipeline = new FilterPipeline()
                .add(new DomainFilter(configManager))
                .add(new SwearFilter(configManager, filterDataDB))
                .add(new ServerNameFilter(configManager, filterDataDB))
                .add(new CustomRegexFilter(configManager))
                .add(new MixedScriptFilter(configManager));
    }

    /**
     * Validates the configured sound name and caches the result.
     * Call this whenever the config is reloaded so the hot path stays allocation-free.
     */
    public void rebuildCachedSound() {
        cachedBlockSound = null;
        if (configManager.isBlockSoundDisabled()) return;
        String name = configManager.getBlockSoundName();
        if (name == null || name.isEmpty()) {
            getLogger().warning("Block sound name is missing in config");
            return;
        }
        try {
            cachedBlockSound = Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound in config: " + name);
        }
    }

    public void rebuildAuditLogger() {
        auditLogger = new AuditLogger(getDataFolder().toPath(), configManager.isAuditLogEnabled());
    }

    // -------------------------------------------------------------------------
    // Core filter logic
    // -------------------------------------------------------------------------
    public String processMessage(String original, Player player) {
        if (bypassGate.shouldBypass(player)) {
            messagesChecked.incrementAndGet();
            return null;
        }

        String normalized = normalizationPipeline.process(original);

        FilterPipeline.Result pre = prePipeline.execute(player, original, normalized);
        if (pre != null) {
            block(player, original, pre.rule());
            return pre.blockMessage();
        }

        UUID playerId = player != null ? player.getUniqueId() : null;
        if (playerId != null && passCache.isCachedPass(playerId, original)) {
            messagesChecked.incrementAndGet();
            return null;
        }

        FilterPipeline.Result post = postPipeline.execute(player, original, normalized);
        if (post != null) {
            block(player, original, post.rule());
            return post.blockMessage();
        }

        if (playerId != null) passCache.recordPass(playerId, original);
        messagesChecked.incrementAndGet();
        return null;
    }
    private void block(Player player, String original, String reason) {
        messagesChecked.incrementAndGet();
        messagesBlocked.incrementAndGet();
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        playerStatsDB.incrementViolation(playerId, original, reason);
        auditLogger.log(player.getName(), playerId, reason, original);

        if (cachedBlockSound != null) {
            final Sound sound  = cachedBlockSound;
            final float volume = (float) configManager.getBlockSoundVolume();
            final float pitch  = (float) configManager.getBlockSoundPitch();
            Bukkit.getScheduler().runTask(this, () ->
                    player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch));
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public ConfigManager    getConfigManager()    { return configManager; }
    public WhitelistManager getWhitelistManager() { return whitelistManager; }
    public PlayerStatsDB    getPlayerStatsDB()    { return playerStatsDB; }
    public FilterDataDB     getFilterDataDB()     { return filterDataDB; }
    public long getMessagesChecked()              { return messagesChecked.get(); }
    public long getMessagesBlocked()              { return messagesBlocked.get(); }

    // -------------------------------------------------------------------------
    // Inner listeners
    // -------------------------------------------------------------------------

    private class ChatListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onAsyncChat(AsyncChatEvent event) {
            String original     = PLAIN.serialize(event.message());
            String blockMessage = processMessage(original, event.getPlayer());
            if (blockMessage != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(LEGACY.deserialize(blockMessage));
            }
        }
    }

    private class CommandListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
            Matcher matcher = PRIVATE_MESSAGE_COMMAND.matcher(event.getMessage());
            if (!matcher.matches()) return;

            Player sender     = event.getPlayer();
            String msgContent = matcher.group(2);

            String blockReason = processMessage(msgContent, sender);
            if (blockReason != null) {
                event.setCancelled(true);
                sender.sendMessage(LEGACY.deserialize(blockReason));
                getLogger().info("Blocked private message from " + sender.getName()
                        + " to " + matcher.group(1) + ": " + msgContent);
            }
        }
    }

}
