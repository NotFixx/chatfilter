package nothing.chatfilter.normalize;

import java.util.BitSet;

public class ZeroWidthStripper {

    private static final BitSet STRIP = new BitSet(65536);
    static {
        STRIP.set(0x00AD);          // Soft Hyphen
        STRIP.set(0x034F);          // Combining Grapheme Joiner
        STRIP.set(0x061C);          // Arabic Letter Mark
        STRIP.set(0x115F);          // Hangul Choseong Filler
        STRIP.set(0x1160);          // Hangul Jungseong Filler
        STRIP.set(0x17B4);          // Khmer Vowel Inherent Aq
        STRIP.set(0x17B5);          // Khmer Vowel Inherent AA
        STRIP.set(0x180E);          // Mongolian Vowel Separator
        STRIP.set(0x200B);          // Zero Width Space
        STRIP.set(0x200C);          // Zero Width Non-Joiner
        STRIP.set(0x200D);          // Zero Width Joiner
        STRIP.set(0x200E);          // Left-to-Right Mark
        STRIP.set(0x200F);          // Right-to-Left Mark
        STRIP.set(0x202A);          // Left-to-Right Embedding
        STRIP.set(0x202B);          // Right-to-Left Embedding
        STRIP.set(0x202C);          // Pop Directional Formatting
        STRIP.set(0x202D);          // Left-to-Right Override
        STRIP.set(0x202E);          // Right-to-Left Override
        STRIP.set(0x205F);          // Medium Mathematical Space
        STRIP.set(0x2060);          // Word Joiner
        STRIP.set(0x2061);          // Function Application
        STRIP.set(0x2062);          // Invisible Times
        STRIP.set(0x2063);          // Invisible Separator
        STRIP.set(0x2064);          // Invisible Plus
        STRIP.set(0x2066);          // Left-to-Right Isolate
        STRIP.set(0x2067);          // Right-to-Left Isolate
        STRIP.set(0x2068);          // First Strong Isolate
        STRIP.set(0x2069);          // Pop Directional Isolate
        STRIP.set(0x2800);          // Braille Pattern Blank
        STRIP.set(0x3164);          // Hangul Filler
        STRIP.set(0xFE00, 0xFE10);  // Variation Selectors 1-16
        STRIP.set(0xFEFF);          // BOM / Zero Width No-Break Space
        STRIP.set(0xFFA0);          // Halfwidth Hangul Filler
        STRIP.set(0xE0100, 0xE01F0); // Variation Selectors Supplement
    }

    public String strip(String input) {
        int len = input.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (STRIP.get(c)) continue;
            if (Character.isHighSurrogate(c) && i + 1 < len) {
                int cp = Character.toCodePoint(c, input.charAt(i + 1));
                if (cp >= 0xE0000 && cp <= 0xE007F) { i++; continue; }
                sb.append(c);
                sb.append(input.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

