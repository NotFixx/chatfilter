package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.detect.DomainDetector;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class DomainFilter implements ChatFilter {

    private final ConfigManager config;
    private final DomainDetector detector;

    // Same patterns used by the normalization pipeline
    private static final Pattern INLINE_SEP = Pattern.compile("(?<=\\p{L})[*_'\\-|](?=\\p{L})");
    private static final Pattern COLLAPSE = Pattern.compile("(.)\\1{2,}");

    // Single-char leet substitutions the normalization pipeline applies
    private static final char[] LEET = new char[128];
    static {
        LEET['1'] = 'i'; LEET['!'] = 'i';
        LEET['3'] = 'e';
        LEET['4'] = 'a'; LEET['@'] = 'a';
        LEET['5'] = 's'; LEET['$'] = 's';
        LEET['7'] = 't'; LEET['+'] = 't';
        LEET['0'] = 'o';
        LEET['8'] = 'b';
        LEET['6'] = 'g'; LEET['9'] = 'g';
        LEET['2'] = 'z';
    }

    public DomainFilter(ConfigManager config) {
        this.config = config;
        this.detector = new DomainDetector(config);
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isDomainDetectionEnabled()) return FilterResult.PASS;

        // Build enriched player name set (leet-decoded, sep-stripped) for
        // SQUISHED_DOMAIN bypass on the normalized text.
        Set<String> playerNames = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName().toLowerCase();
            playerNames.add(name);
            name = decodeLeet(name);
            playerNames.add(name);
            name = INLINE_SEP.matcher(name).replaceAll("");
            playerNames.add(name);
            name = COLLAPSE.matcher(name).replaceAll("$1$1");
            playerNames.add(name);
        }

        // Check original text first â€” catches "leaf.com" via DIRECT_DOMAIN (dot intact).
        // Player-name bypass is active here too (exact-match only in DIRECT_DOMAIN,
        // plus TLD-strip and common-word prefix in SQUISHED_DOMAIN) so that player
        // names containing TLDs like "MayankBest" aren't falsely flagged.
        if (detector.matches(original, playerNames, playerNames)) {
            return FilterResult.block(config.getDomainBlockMessage(), "Domain");
        }

        // Then check normalized text â€” catches leet-obfuscated stuff like "l33t.c0m"
        // after leet decoding â†’ "leet.com". Player-name bypass active here so that
        // "orbitdevilname" (from "Orb1tDeviL_name") isn't falsely flagged.
        if (detector.matches(normalized, playerNames, playerNames)) {
            return FilterResult.block(config.getDomainBlockMessage(), "Domain");
        }

        return FilterResult.PASS;
    }

    private static String decodeLeet(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c < 128 && LEET[c] != 0) chars[i] = LEET[c];
        }
        return new String(chars);
    }
}

