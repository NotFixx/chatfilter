package nothing.chatfilter.pipeline.filter;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.pipeline.ChatFilter;
import nothing.chatfilter.pipeline.FilterResult;
import org.bukkit.entity.Player;

public class MixedScriptFilter implements ChatFilter {

    private final ConfigManager config;

    public MixedScriptFilter(ConfigManager config) {
        this.config = config;
    }

    @Override
    public FilterResult filter(Player player, String original, String normalized) {
        if (config.isMixedScriptDetectionDisabled()) return FilterResult.PASS;

        int len = normalized.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(normalized.charAt(i))) i++;
            if (i >= len) break;

            int start = i;
            while (i < len && !Character.isWhitespace(normalized.charAt(i))) i++;

            if (hasMixedScripts(normalized, start, i)) {
                return FilterResult.block(config.getMixedScriptBlockMessage(), "Mixed Script");
            }
        }
        return FilterResult.PASS;
    }

    private static boolean hasMixedScripts(String text, int start, int end) {
        Character.UnicodeScript firstScript = null;
        for (int i = start; i < end; ) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);

            if (cp < 128) {
                i += charCount;
                continue;
            }

            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (firstScript == null) {
                firstScript = script;
            } else if (script != firstScript) {
                return true;
            }
            i += charCount;
        }
        return false;
    }
}

