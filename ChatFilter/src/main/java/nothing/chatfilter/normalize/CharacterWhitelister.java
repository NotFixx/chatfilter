package nothing.chatfilter.normalize;

import nothing.chatfilter.ConfigManager;
import java.util.BitSet;
import java.util.List;

public class CharacterWhitelister {

    private final BitSet allowed = new BitSet(65536);
    private final boolean enabled;

    public CharacterWhitelister(ConfigManager config) {
        this.enabled = config.isCharWhitelistEnabled();
        buildTable(config.getAllowedUnicodeBlocks());
    }

    private void buildTable(List<String> blocks) {
        for (String block : blocks) {
            switch (block) {
                case "BASIC_LATIN"         -> allowed.set(0x0000, 0x0080);
                case "LATIN_1_SUPPLEMENT"  -> allowed.set(0x0080, 0x0100);
                case "LATIN_EXTENDED_A"    -> allowed.set(0x0100, 0x0180);
                case "GENERAL_PUNCTUATION" -> allowed.set(0x2000, 0x2070);
                case "CURRENCY_SYMBOLS"    -> allowed.set(0x20A0, 0x20D0);
            }
        }
    }

    public String apply(String input) {
        if (!enabled) return input;
        int len = input.length();
        for (int i = 0; i < len; i++) {
            if (!allowed.get(input.charAt(i))) return null;
        }
        return input;
    }
}
