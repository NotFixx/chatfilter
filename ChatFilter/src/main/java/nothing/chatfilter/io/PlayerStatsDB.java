package nothing.chatfilter.io;

import nothing.chatfilter.ChatFilterPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class PlayerStatsDB {

    private final ChatFilterPlugin plugin;
    private Connection writeConnection;
    private Connection readConnection;

    // Single-threaded executor â€“ all write operations go here
    private final ExecutorService dbThread = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "chatfilter-db-write"));

    // ---- Write PreparedStatements (used only on dbThread) ----
    private PreparedStatement psUpsertStats;
    private PreparedStatement psInsertViolation;
    private PreparedStatement psTrimViolations;

    // ---- Read PreparedStatements (used on whatever thread reads) ----
    private PreparedStatement psGetStats;
    private PreparedStatement psGetViolations;
    private PreparedStatement psGetTopViolators;

    public PlayerStatsDB(ChatFilterPlugin plugin) {
        this.plugin = plugin;
        openConnections();
        createTables();
        prepareWriteStatements();
        prepareReadStatements();
    }

    private void openConnections() {
        File dbFile = new File(plugin.getDataFolder(), "player_stats.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            writeConnection = DriverManager.getConnection(url);
            readConnection  = DriverManager.getConnection(url);
            try (Statement stmt = writeConnection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
            // Read connection doesn't need to set PRAGMAs (they affect the whole DB)
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupt player_stats.db, recreating...");
            if (dbFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dbFile.renameTo(new File(plugin.getDataFolder(),
                        "player_stats.db.corrupted." + System.currentTimeMillis()));
            }
            try {
                writeConnection = DriverManager.getConnection(url);
                readConnection  = DriverManager.getConnection(url);
            } catch (SQLException ex) {
                throw new RuntimeException("Cannot create player_stats.db", ex);
            }
        }
    }

    private void createTables() {
        try (Statement stmt = writeConnection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT PRIMARY KEY,
                        violation_count INTEGER DEFAULT 0,
                        last_violation TEXT)""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS violations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        message TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        timestamp TEXT DEFAULT (datetime('now')))""");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_violations_uuid_ts ON violations(uuid, timestamp DESC)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create tables", e);
        }
    }

    private void prepareWriteStatements() {
        try {
            psUpsertStats = writeConnection.prepareStatement("""
                    INSERT INTO player_stats (uuid, violation_count, last_violation) VALUES (?, 1, datetime('now'))
                    ON CONFLICT(uuid) DO UPDATE SET
                        violation_count = violation_count + 1,
                        last_violation  = datetime('now')""");

            psInsertViolation = writeConnection.prepareStatement(
                    "INSERT INTO violations (uuid, message, reason) VALUES (?, ?, ?)");

            psTrimViolations = writeConnection.prepareStatement("""
                    DELETE FROM violations WHERE uuid = ?
                    AND id NOT IN (
                        SELECT id FROM violations WHERE uuid = ?
                        ORDER BY timestamp DESC LIMIT 100)""");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare write statements", e);
        }
    }

    private void prepareReadStatements() {
        try {
            psGetStats = readConnection.prepareStatement(
                    "SELECT violation_count, last_violation FROM player_stats WHERE uuid = ?");

            psGetViolations = readConnection.prepareStatement("""
                    SELECT message, reason, timestamp FROM violations
                    WHERE uuid = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?""");

            psGetTopViolators = readConnection.prepareStatement("""
                    SELECT uuid, violation_count, last_violation FROM player_stats
                    WHERE violation_count > 0 ORDER BY violation_count DESC LIMIT ? OFFSET ?""");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare read statements", e);
        }
    }

    public void incrementViolation(UUID uuid, String message, String reason) {
        String uuidStr = uuid.toString();
        CompletableFuture.runAsync(() -> {
            try {
                writeConnection.setAutoCommit(false);
                try {
                    psUpsertStats.setString(1, uuidStr);
                    psUpsertStats.executeUpdate();

                    psInsertViolation.setString(1, uuidStr);
                    psInsertViolation.setString(2, message);
                    psInsertViolation.setString(3, reason);
                    psInsertViolation.executeUpdate();

                    psTrimViolations.setString(1, uuidStr);
                    psTrimViolations.setString(2, uuidStr);
                    psTrimViolations.executeUpdate();

                    writeConnection.commit();
                } catch (SQLException e) {
                    writeConnection.rollback();
                    throw e;
                } finally {
                    writeConnection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record violation", e);
                throw new RuntimeException(e);
            }
        }, dbThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Violation write failed", ex);
            return null;
        });
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        try {
            psGetStats.setString(1, uuid.toString());
            try (ResultSet rs = psGetStats.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(rs.getInt("violation_count"), rs.getString("last_violation"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get player stats", e);
        }
        return null;
    }

    public List<ViolationEntry> getRecentViolations(UUID uuid, int limit, int offset) {
        List<ViolationEntry> list = new ArrayList<>();
        try {
            psGetViolations.setString(1, uuid.toString());
            psGetViolations.setInt(2, limit);
            psGetViolations.setInt(3, offset);
            try (ResultSet rs = psGetViolations.executeQuery()) {
                while (rs.next()) {
                    list.add(new ViolationEntry(
                            rs.getString("message"),
                            rs.getString("reason"),
                            rs.getString("timestamp")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get recent violations", e);
        }
        return list;
    }

    public List<TopViolator> getTopViolators(int limit, int offset) {
        List<TopViolator> list = new ArrayList<>();
        try {
            psGetTopViolators.setInt(1, limit);
            psGetTopViolators.setInt(2, offset);
            try (ResultSet rs = psGetTopViolators.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopViolator(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("violation_count"),
                            rs.getString("last_violation")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get top violators", e);
        }
        return list;
    }

    public void close() {
        dbThread.shutdown();
        try {
            if (!dbThread.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                dbThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbThread.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            if (psUpsertStats     != null) psUpsertStats.close();
            if (psInsertViolation != null) psInsertViolation.close();
            if (psTrimViolations  != null) psTrimViolations.close();
            if (psGetStats        != null) psGetStats.close();
            if (psGetViolations   != null) psGetViolations.close();
            if (psGetTopViolators != null) psGetTopViolators.close();
            if (writeConnection   != null) writeConnection.close();
            if (readConnection    != null) readConnection.close();
        } catch (SQLException ignored) {}
    }

    public record PlayerStats(int violationCount, String lastViolation) {}
    public record ViolationEntry(String message, String reason, String timestamp) {}
    public record TopViolator(UUID uuid, int violationCount, String lastViolation) {}
}
