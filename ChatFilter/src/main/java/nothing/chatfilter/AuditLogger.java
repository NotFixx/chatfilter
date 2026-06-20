package nothing.chatfilter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class AuditLogger {

    private final Path file;
    private final boolean enabled;

    public AuditLogger(Path dataFolder, boolean enabled) {
        this.enabled = enabled;
        this.file = dataFolder.resolve("audit.log");
    }

    public void log(String playerName, UUID playerId, String rule, String original) {
        if (!enabled) return;
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            w.write(String.format("[%tF %tT] %s (%s) | %s | %s%n",
                    System.currentTimeMillis(), System.currentTimeMillis(),
                    playerName, playerId, rule, original));
        } catch (IOException ignored) {}
    }
}

