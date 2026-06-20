package nothing.chatfilter.io;

import nothing.chatfilter.ChatFilterPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class FilterDataDB {

    private final ChatFilterPlugin plugin;
    private Connection connection;

    // In-memory cache â€” avoids a DB round-trip on every chat message check
    private final ConcurrentMap<String, Integer> swearCache         = new ConcurrentHashMap<>(); // word â†’ level (fuzzy)
    private final Set<String>                    basicSwearCache    = ConcurrentHashMap.newKeySet(); // exact-match only
    private final Set<String>                    serverNameCache    = ConcurrentHashMap.newKeySet(); // O(1) contains
    private final ConcurrentMap<String, Pattern> serverNamePatterns = new ConcurrentHashMap<>(); // pre-compiled patterns

    // Pre-compiled write statements; reads go through the cache
    private PreparedStatement psAddSwear;
    private PreparedStatement psRemoveSwear;
    private PreparedStatement psAddBasicSwear;
    private PreparedStatement psRemoveBasicSwear;
    private PreparedStatement psAddServer;
    private PreparedStatement psRemoveServer;

    public FilterDataDB(ChatFilterPlugin plugin) {
        this.plugin = plugin;
        openConnection();
        createTables();
        prepareStatements();
        populateCache();
    }

    private void openConnection() {
        File dbFile = new File(plugin.getDataFolder(), "filter_data.db");
        String url  = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupt filter_data.db, recreating...");
            if (dbFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dbFile.renameTo(new File(plugin.getDataFolder(),
                        "filter_data.db.corrupted." + System.currentTimeMillis()));
            }
            try {
                connection = DriverManager.getConnection(url);
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot create filter_data.db", ex);
            }
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS swear_words (
                        word  TEXT    PRIMARY KEY,
                        level INTEGER NOT NULL DEFAULT 2)""");
            stmt.execute("CREATE TABLE IF NOT EXISTS server_names (name TEXT PRIMARY KEY)");
            stmt.execute("CREATE TABLE IF NOT EXISTS swear_words_basic (word TEXT PRIMARY KEY)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create filter data tables", e);
        }
    }

    private void prepareStatements() {
        try {
            psAddSwear        = connection.prepareStatement("INSERT OR IGNORE INTO swear_words (word, level) VALUES (?, ?)");
            psRemoveSwear     = connection.prepareStatement("DELETE FROM swear_words WHERE word = ?");
            psAddBasicSwear   = connection.prepareStatement("INSERT OR IGNORE INTO swear_words_basic (word) VALUES (?)");
            psRemoveBasicSwear = connection.prepareStatement("DELETE FROM swear_words_basic WHERE word = ?");
            psAddServer       = connection.prepareStatement("INSERT OR IGNORE INTO server_names (name) VALUES (?)");
            psRemoveServer    = connection.prepareStatement("DELETE FROM server_names WHERE name = ?");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare statements", e);
        }
    }

    /** Loads all persisted data into memory once at startup. */
    private void populateCache() {
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT word, level FROM swear_words")) {
                while (rs.next()) swearCache.put(rs.getString("word"), rs.getInt("level"));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT word FROM swear_words_basic")) {
                while (rs.next()) basicSwearCache.add(rs.getString("word"));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM server_names")) {
                while (rs.next()) serverNameCache.add(rs.getString("name"));
            }
            rebuildPatternCache();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to populate filter cache", e);
        }
    }

    private void rebuildPatternCache() {
        serverNamePatterns.clear();
        for (String name : serverNameCache) {
            serverNamePatterns.put(name,
                    Pattern.compile(".*\\b" + Pattern.quote(name) + "\\b.*"));
        }
    }

    // ---------- Swear words ----------

    public void addSwearWord(String word) {
        String lower = word.toLowerCase();
        try {
            psAddSwear.setString(1, lower);
            psAddSwear.setInt(2, 2);
            psAddSwear.executeUpdate();
            swearCache.put(lower, 2);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add swear word: " + word, e);
        }
    }

    public void removeSwearWord(String word) {
        String lower = word.toLowerCase();
        try {
            psRemoveSwear.setString(1, lower);
            psRemoveSwear.executeUpdate();
            swearCache.remove(lower);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove swear word: " + word, e);
        }
    }

    /** O(1) cache lookup â€” no DB hit. */
    public boolean swearWordExists(String word) {
        return swearCache.containsKey(word.toLowerCase());
    }

    public List<String> getAllSwearWords() {
        List<String> all = new ArrayList<>(swearCache.keySet());
        all.addAll(basicSwearCache);
        return all;
    }

    /** Returns only fuzzy (non-basic) swear words â€” for BKTree. */
    public List<String> getAllFuzzySwearWords() {
        return new ArrayList<>(swearCache.keySet());
    }

    /** Returns only basic (exact-only) swear words â€” for tab completion. */
    public List<String> getAllBasicSwearWords() {
        return new ArrayList<>(basicSwearCache);
    }

    public boolean basicSwearWordExists(String word) {
        return basicSwearCache.contains(word.toLowerCase());
    }

    public void addBasicSwearWord(String word) {
        String lower = word.toLowerCase();
        try {
            psAddBasicSwear.setString(1, lower);
            psAddBasicSwear.executeUpdate();
            basicSwearCache.add(lower);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add basic swear word: " + word, e);
        }
    }

    public void removeBasicSwearWord(String word) {
        String lower = word.toLowerCase();
        try {
            psRemoveBasicSwear.setString(1, lower);
            psRemoveBasicSwear.executeUpdate();
            basicSwearCache.remove(lower);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove basic swear word: " + word, e);
        }
    }


    // ---------- Server names ----------

    public void addServerName(String name) {
        String lower = name.toLowerCase();
        try {
            psAddServer.setString(1, lower);
            psAddServer.executeUpdate();
            serverNameCache.add(lower);
            serverNamePatterns.put(lower,
                    Pattern.compile(".*\\b" + Pattern.quote(lower) + "\\b.*"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add server name: " + name, e);
        }
    }

    public void removeServerName(String name) {
        String lower = name.toLowerCase();
        try {
            psRemoveServer.setString(1, lower);
            psRemoveServer.executeUpdate();
            serverNameCache.remove(lower);
            serverNamePatterns.remove(lower);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove server name: " + name, e);
        }
    }

    /** O(1) cache lookup â€” no DB hit. */
    public boolean serverNameExists(String name) {
        return serverNameCache.contains(name.toLowerCase());
    }

    public List<String> getAllServerNames() {
        return new ArrayList<>(serverNameCache);
    }

    /* Runs entirely from cache â€” no DB hit on the hot chat path. */
    /*
     * Checks if any blacklisted server name appears as a full word in the text.
     * No fuzzy matching â€“ only exact wordâ€‘boundary matches.
     */
    public String findBlockedServerName(String text) {
        if (serverNameCache.isEmpty()) return null;
        String lower = text.toLowerCase();

        for (String name : serverNameCache) {
            Pattern p = serverNamePatterns.computeIfAbsent(name,
                    n -> Pattern.compile(".*\\b" + Pattern.quote(n) + "\\b.*"));
            if (p.matcher(lower).matches()) {
                return name;
            }
        }
        return null;
    }

    public void close() {
        try {
            if (psAddSwear        != null) psAddSwear.close();
            if (psRemoveSwear     != null) psRemoveSwear.close();
            if (psAddBasicSwear   != null) psAddBasicSwear.close();
            if (psRemoveBasicSwear != null) psRemoveBasicSwear.close();
            if (psAddServer       != null) psAddServer.close();
            if (psRemoveServer    != null) psRemoveServer.close();
            if (connection        != null) connection.close();
        } catch (SQLException ignored) {}
    }
}
