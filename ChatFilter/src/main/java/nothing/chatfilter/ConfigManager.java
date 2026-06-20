package nothing.chatfilter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class ConfigManager {

    private final ChatFilterPlugin plugin;
    private final File configFile; // built once, reused in saveDefaultConfig() and reload()

    // --- Character Whitelist ---
    private volatile boolean      charWhitelistEnabled;
    private volatile List<String> allowedUnicodeBlocks;
    private volatile String       charWhitelistBlockMessage;

    // Message length limit
    private volatile boolean maxMessageLengthEnabled;
    private volatile int     maxMessageLength;
    private volatile String  maxMessageBlockMessage;

    // --- IP Detection ---
    private volatile boolean ipDetectionEnabled;
    private volatile String  ipBlockMessage;
    private volatile boolean blockHexIp;
    private volatile boolean blockObfuscated;

    // --- Domain Detection ---
    private volatile boolean domainDetectionEnabled;
    private volatile boolean blockGenericDomains;
    private volatile boolean blockDiscord;
    private volatile boolean blockInvitePhrases;
    private volatile String  domainBlockMessage;

    // --- Swear Detection ---
    private volatile boolean swearDetectionEnabled;
    private volatile String  swearBlockMessage;

    // Repeated character spam
    private volatile boolean repeatedCharSpamEnabled;
    private volatile int     maxRepeatedChars;
    private volatile String  repeatedCharBlockMessage;

    // --- Server Name ---
    private volatile String serverNameBlockMessage;

    // --- Bypass ---
    private volatile boolean opsBypass;

    // --- Block Sound ---
    private volatile boolean blockSoundEnabled;
    private volatile String  blockSoundName;
    private volatile double  blockSoundVolume;
    private volatile double  blockSoundPitch;

    // --- Stats Messages ---
    private volatile String statsTotalHeader;
    private volatile String statsTotalChecked;
    private volatile String statsTotalBlocked;
    private volatile String statsTotalBlockRate;
    private volatile String statsPlayerHeader;
    private volatile String statsPlayerViolations;
    private volatile String statsPlayerLastViolation;
    private volatile String statsPlayerNoData;
    private volatile String statsTopHeader;
    private volatile String statsTopEntry;
    private volatile String statsTopNoData;

    // --- Rate Limit ---
    private volatile boolean rateLimitEnabled;
    private volatile int     rateLimitMaxMessages;
    private volatile int     rateLimitWindowSeconds;
    private volatile String  rateLimitBlockMessage;

    // --- Flood Detection ---
    private volatile boolean floodDetectionEnabled;
    private volatile double  floodSimilarityThreshold;
    private volatile int     floodWindowSeconds;
    private volatile int     floodMaxRepeats;
    private volatile String  floodBlockMessage;

    // --- Custom Regex Rules ---
    private volatile List<CustomRule> customRules;

    // --- Mixed Script Detection ---
    private volatile boolean mixedScriptDetectionEnabled;
    private volatile String  mixedScriptBlockMessage;

    // --- MiniMessage Filter ---
    private volatile boolean miniMessageFilterEnabled;
    private volatile String  miniMessageBlockMessage;

    // --- Legacy Color Code Filter ---
    private volatile boolean colorCodeFilterEnabled;
    private volatile String  colorCodeBlockMessage;

    // --- Audit Log ---
    private volatile boolean auditLogEnabled;

    public ConfigManager(ChatFilterPlugin plugin) {
        this.plugin     = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        saveDefaultConfig();
        reload();
    }

    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    /**
     * Reads config.yml from disk and caches every value into typed fields.
     * After this method returns the FileConfiguration object is released,
     * all getters are plain field accesses â€” no YAML map lookups at runtime.
     */
    public void reload() {
        // Load defaults from the bundled config.yml inside the JAR.
        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(plugin.getResource("config.yml")))
        );
        // Load the user's config on disk.
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        // Save any newly-added default keys back to the file (e.g. new sections
        // from a plugin update).
        try {
            config.save(configFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save updated config.yml: " + e.getMessage());
        }

        // Character Whitelist
        charWhitelistEnabled      = config.getBoolean("character_whitelist.enabled", true);
        allowedUnicodeBlocks      = config.getStringList("character_whitelist.allowed_unicode_blocks");
        charWhitelistBlockMessage = config.getString("character_whitelist.block_message",
                "&cPlease use only standard English characters.");

        // Message Length Limit
        maxMessageLengthEnabled = config.getBoolean("max_message_length.enabled", true);
        maxMessageLength        = config.getInt("max_message_length.max_length", 256);
        maxMessageBlockMessage  = config.getString("max_message_length.block_message",
                "&cYour message is too long. Keep it under %max% characters.");

        // IP Detection
        ipDetectionEnabled = config.getBoolean("ip_detection.enabled", true);
        blockHexIp         = config.getBoolean("ip_detection.block_hex_ip", true);
        blockObfuscated    = config.getBoolean("ip_detection.block_obfuscated", true);
        ipBlockMessage     = config.getString("ip_detection.block_message",
                "&cAdvertising IP addresses is not allowed.");

        // Domain Detection
        domainDetectionEnabled = config.getBoolean("domain_detection.enabled", true);
        blockGenericDomains    = config.getBoolean("domain_detection.block_generic", true);
        blockDiscord           = config.getBoolean("domain_detection.block_discord", true);
        blockInvitePhrases     = config.getBoolean("domain_detection.block_invite_phrases", true);
        domainBlockMessage     = config.getString("domain_detection.block_message",
                "&cAdvertising servers or domains is not allowed.");

        // Swear Detection
        swearDetectionEnabled = config.getBoolean("swear_detection.enabled", true);
        swearBlockMessage     = config.getString("swear_detection.block_message",
                "&cPlease keep chat respectful.");

        // Repeated Character Spam
        repeatedCharSpamEnabled = config.getBoolean("repeated_char_spam.enabled", true);
        maxRepeatedChars        = config.getInt("repeated_char_spam.max_repeats", 5);
        repeatedCharBlockMessage = config.getString("repeated_char_spam.block_message",
                "&cPlease don't spam characters.");

        // Server Name
        serverNameBlockMessage = config.getString("server_name_block_message",
                "&cPlease don't say other servers name (%name%)");

        // Bypass
        opsBypass = config.getBoolean("bypass.ops", false);

        // Block Sound
        blockSoundEnabled = config.getBoolean("block_sound.enabled", true);
        blockSoundName    = config.getString("block_sound.sound", "BLOCK_NOTE_BLOCK_BASS");
        blockSoundVolume  = config.getDouble("block_sound.volume", 1.0);
        blockSoundPitch   = config.getDouble("block_sound.pitch", 1.0);

        // Stats Messages
        statsTotalHeader         = config.getString("stats.total_header",          "&6--- ChatFilter Global Stats ---");
        statsTotalChecked        = config.getString("stats.total_checked",          "&eMessages checked: &f%checked%");
        statsTotalBlocked        = config.getString("stats.total_blocked",          "&eMessages blocked: &f%blocked%");
        statsTotalBlockRate      = config.getString("stats.total_block_rate",       "&eBlock rate: &f%rate%%");
        statsPlayerHeader        = config.getString("stats.player_header",          "&6--- Stats for %player% ---");
        statsPlayerViolations    = config.getString("stats.player_violations",      "&cViolations: &f%violations%");
        statsPlayerLastViolation = config.getString("stats.player_last_violation",  "&cLast violation: &f%last%");
        statsPlayerNoData        = config.getString("stats.player_no_data",         "&eNo stats recorded for this player.");
        statsTopHeader           = config.getString("stats.top_header",             "&6--- Top Violators (Page %page%) ---");
        statsTopEntry            = config.getString("stats.top_entry",
                "&c%rank%. &f%player% &8- &c%violations% violations &7(last: %last%)");
        statsTopNoData           = config.getString("stats.top_no_data",            "&eNo violations recorded yet.");

        // Rate Limit
        rateLimitEnabled      = config.getBoolean("rate_limit.enabled", false);
        rateLimitMaxMessages  = config.getInt("rate_limit.max_messages", 5);
        rateLimitWindowSeconds = config.getInt("rate_limit.window_seconds", 10);
        rateLimitBlockMessage = config.getString("rate_limit.block_message",
                "&cYou're chatting too fast.");

        // Flood Detection
        floodDetectionEnabled    = config.getBoolean("flood_detection.enabled", false);
        floodSimilarityThreshold = config.getDouble("flood_detection.similarity_threshold", 0.85);
        floodWindowSeconds       = config.getInt("flood_detection.window_seconds", 30);
        floodMaxRepeats          = config.getInt("flood_detection.max_repeats", 2);
        floodBlockMessage        = config.getString("flood_detection.block_message",
                "&cPlease don't repeat messages.");

        // Custom Regex Rules
        customRules = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("custom_rules")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> section = (Map<String, Object>) raw;
            String name    = (String) section.getOrDefault("name", "rule");
            String pattern = (String) section.get("pattern");
            String msg     = (String) section.getOrDefault("block_message", "&cThat message is not allowed.");
            boolean en     = (boolean) section.getOrDefault("enabled", true);
            if (pattern != null) {
                try {
                    customRules.add(new CustomRule(name, Pattern.compile(pattern), msg, en));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid regex rule '" + name + "': " + pattern);
                }
            }
        }

        // Mixed Script Detection
        mixedScriptDetectionEnabled = config.getBoolean("mixed_script_detection.enabled", false);
        mixedScriptBlockMessage     = config.getString("mixed_script_detection.block_message",
                "&cPlease use only standard English characters.");

        // MiniMessage Filter
        miniMessageFilterEnabled = config.getBoolean("minimessage_filter.enabled", true);
        miniMessageBlockMessage  = config.getString("minimessage_filter.block_message",
                "&cPlease don't use chat formatting codes.");

        // Legacy Color Code Filter
        colorCodeFilterEnabled = config.getBoolean("color_code_filter.enabled", true);
        colorCodeBlockMessage  = config.getString("color_code_filter.block_message",
                "&cPlease don't use chat formatting codes.");

        // Audit Log
        auditLogEnabled = config.getBoolean("audit_log.enabled", false);

        // --- Validation ---
        if (maxMessageLengthEnabled && maxMessageLength <= 0) {
            plugin.getLogger().warning("max_message_length.max_length must be > 0, got " + maxMessageLength);
        }
        if (repeatedCharSpamEnabled && maxRepeatedChars <= 0) {
            plugin.getLogger().warning("repeated_char_spam.max_repeats must be > 0, got " + maxRepeatedChars);
        }
        if (rateLimitEnabled && (rateLimitMaxMessages <= 0 || rateLimitWindowSeconds <= 0)) {
            plugin.getLogger().warning("rate_limit values must be > 0");
        }
    }

    // -------------------------------------------------------------------------
    // Getters â€” plain field reads, no YAML lookup cost
    // -------------------------------------------------------------------------

    // Character Whitelist
    public boolean     isCharWhitelistEnabled()      { return charWhitelistEnabled; }
    public List<String> getAllowedUnicodeBlocks()      { return allowedUnicodeBlocks; }
    public String      getCharWhitelistBlockMessage() { return charWhitelistBlockMessage; }

    public boolean isMaxMessageLengthDisabled() { return !maxMessageLengthEnabled; }
    public int     getMaxMessageLength()       { return maxMessageLength; }
    public String  getMaxMessageBlockMessage() { return maxMessageBlockMessage; }

    // IP Detection
    public boolean isIpDetectionEnabled() { return ipDetectionEnabled; }
    public String  getIpBlockMessage()    { return ipBlockMessage; }
    public boolean isBlockHexIp()        { return blockHexIp; }
    public boolean isBlockObfuscated()   { return blockObfuscated; }

    // Domain Detection

    public boolean isDomainDetectionEnabled() { return !domainDetectionEnabled; }
    public boolean blockGenericDomains()      { return blockGenericDomains; }
    public boolean blockDiscord()             { return blockDiscord; }
    public boolean blockInvitePhrases()       { return blockInvitePhrases; }
    public String  getDomainBlockMessage()    { return domainBlockMessage; }

    // Swear Detection
    public boolean isSwearDetectionEnabled() { return swearDetectionEnabled; }
    public String  getSwearBlockMessage()    { return swearBlockMessage; }

    public boolean isRepeatedCharSpamDisabled()  { return !repeatedCharSpamEnabled; }
    public int     getMaxRepeatedChars()        { return maxRepeatedChars; }
    public String  getRepeatedCharBlockMessage() { return repeatedCharBlockMessage; }

    // Server Name
    public String getServerNameBlockMessage() { return serverNameBlockMessage; }

    // Bypass
    public boolean isOpsBypass() { return opsBypass; }

    // Block Sound
    public boolean isBlockSoundDisabled() { return !blockSoundEnabled; }
    public String  getBlockSoundName()   { return blockSoundName; }
    public double  getBlockSoundVolume() { return blockSoundVolume; }
    public double  getBlockSoundPitch()  { return blockSoundPitch; }

    // Stats Messages
    public String getStatsTotalHeader()         { return statsTotalHeader; }
    public String getStatsTotalChecked()        { return statsTotalChecked; }
    public String getStatsTotalBlocked()        { return statsTotalBlocked; }
    public String getStatsTotalBlockRate()      { return statsTotalBlockRate; }
    public String getStatsPlayerHeader()        { return statsPlayerHeader; }
    public String getStatsPlayerViolations()    { return statsPlayerViolations; }
    public String getStatsPlayerLastViolation() { return statsPlayerLastViolation; }
    public String getStatsPlayerNoData()        { return statsPlayerNoData; }
    public String getStatsTopHeader()           { return statsTopHeader; }
    public String getStatsTopEntry()            { return statsTopEntry; }
    public String getStatsTopNoData()           { return statsTopNoData; }

    // Rate Limit
    public boolean isRateLimitDisabled()         { return !rateLimitEnabled; }
    public int     getRateLimitMaxMessages()    { return rateLimitMaxMessages; }
    public int     getRateLimitWindowSeconds()  { return rateLimitWindowSeconds; }
    public String  getRateLimitBlockMessage()   { return rateLimitBlockMessage; }

    // Flood Detection
    public boolean isFloodDetectionDisabled()     { return !floodDetectionEnabled; }
    public double  getFloodSimilarityThreshold() { return floodSimilarityThreshold; }
    public int     getFloodWindowSeconds()       { return floodWindowSeconds; }
    public int     getFloodMaxRepeats()          { return floodMaxRepeats; }
    public String  getFloodBlockMessage()        { return floodBlockMessage; }

    // Custom Rules
    public List<CustomRule> getCustomRules() { return customRules; }

    // Audit Log
    public boolean isAuditLogEnabled() { return auditLogEnabled; }

    // Mixed Script Detection
    public boolean isMixedScriptDetectionDisabled() { return !mixedScriptDetectionEnabled; }
    public String  getMixedScriptBlockMessage()    { return mixedScriptBlockMessage; }

    // MiniMessage Filter
    public boolean isMiniMessageFilterDisabled() { return !miniMessageFilterEnabled; }
    public String  getMiniMessageBlockMessage()  { return miniMessageBlockMessage; }

    // Legacy Color Code Filter
    public boolean isColorCodeFilterDisabled() { return !colorCodeFilterEnabled; }
    public String  getColorCodeBlockMessage()  { return colorCodeBlockMessage; }

    public record CustomRule(String name, Pattern pattern, String blockMessage, boolean enabled) {}
}
