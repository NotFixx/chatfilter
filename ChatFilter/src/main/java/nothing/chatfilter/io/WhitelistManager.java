package nothing.chatfilter.io;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nothing.chatfilter.ChatFilterPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WhitelistManager {

    private static final Gson GSON = new Gson();
    private static final Type UUID_LIST_TYPE = new TypeToken<List<String>>(){}.getType();

    private final ChatFilterPlugin plugin;
    private final File file;
    private final Set<UUID> whitelist = ConcurrentHashMap.newKeySet();

    public WhitelistManager(ChatFilterPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "whitelist.json");
        load();
    }

    private void load() {
        if (!file.exists()) {
            save();
            return;
        }
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            List<String> list = GSON.fromJson(reader, UUID_LIST_TYPE);
            if (list != null) {
                for (String uuidStr : list) {
                    try {
                        whitelist.add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in whitelist: " + uuidStr);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load whitelist.json, renaming corrupted file.");
            File corrupted = new File(plugin.getDataFolder(), "whitelist.json.corrupted." + System.currentTimeMillis());
            //noinspection ResultOfMethodCallIgnored
            file.renameTo(corrupted);
            whitelist.clear();
            save();
        }
    }

    public void save() {
        List<String> uuidList = whitelist.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(uuidList, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save whitelist.json");
        }
    }

    public boolean isWhitelisted(UUID uuid) { return whitelist.contains(uuid); }
    public void add(UUID uuid)    { whitelist.add(uuid);    save(); }
    public void remove(UUID uuid) { whitelist.remove(uuid); save(); }
    public List<UUID> list()      { return new ArrayList<>(whitelist); }
}
