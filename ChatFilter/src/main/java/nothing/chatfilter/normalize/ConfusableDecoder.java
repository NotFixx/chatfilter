package nothing.chatfilter.normalize;

import java.util.HashMap;
import java.util.Map;

public class ConfusableDecoder {

    private static final Map<Integer, String> MAP = new HashMap<>();

    static {
        // Cyrillic homoglyphs
        map(0x0430, "a"); // Ð°
        map(0x0435, "e"); // Ðµ
        map(0x0456, "i"); // Ñ–
        map(0x0458, "j"); // Ñ˜
        map(0x043E, "o"); // Ð¾
        map(0x0440, "p"); // Ñ€
        map(0x0441, "c"); // Ñ
        map(0x0455, "s"); // Ñ•
        map(0x0445, "x"); // Ñ…
        map(0x0443, "y"); // Ñƒ
        map(0x04BB, "h"); // Ò»
        map(0x0501, "d"); // Ô
        map(0x051B, "q"); // Ô›
        map(0x051D, "w"); // Ô
        map(0x04CF, "l"); // Ó
        map(0x0433, "r"); // Ð³ (ghe â†’ r)

        // Greek homoglyphs
        map(0x03B1, "a"); // Î±
        map(0x03BF, "o"); // Î¿
        map(0x03B5, "e"); // Îµ
        map(0x03BA, "k"); // Îº
        map(0x03BD, "v"); // Î½
        map(0x03C1, "p"); // Ï
        map(0x03C3, "c"); // Ïƒ
        map(0x03C4, "t"); // Ï„
        map(0x03C7, "x"); // Ï‡

        // Mathematical Bold (U+1D400 - U+1D419: A-Z, U+1D41A - U+1D433: a-z)
        for (int i = 0; i < 26; i++) map(0x1D400 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D41A + i, String.valueOf((char) ('a' + i)));

        // Mathematical Italic (U+1D434 - U+1D44D: A-Z, U+1D44E - U+1D467: a-z)
        for (int i = 0; i < 26; i++) map(0x1D434 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D44E + i, String.valueOf((char) ('a' + i)));

        // Mathematical Bold Italic (U+1D468 - U+1D481: A-Z, U+1D482 - U+1D49B: a-z)
        for (int i = 0; i < 26; i++) map(0x1D468 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D482 + i, String.valueOf((char) ('a' + i)));

        // Mathematical Script (U+1D49C - U+1D4B5: A-Z, U+1D4B6 - U+1D4CF: a-z) â€” skips gaps
        int[] scriptUpper = {0x1D49C,0x1D49D,0x1D49E,0x1D49F,0x1D4A2,0x1D4A5,0x1D4A6,
                0x1D4A9,0x1D4AA,0x1D4AB,0x1D4AC,0x1D4AE,0x1D4AF,0x1D4B0,0x1D4B1,0x1D4B2,
                0x1D4B3,0x1D4B4,0x1D4B5};
        char scriptUpperC = 'A';
        for (int cp : scriptUpper) map(cp, String.valueOf(scriptUpperC++));

        int[] scriptLower = {0x1D4B6,0x1D4B7,0x1D4B8,0x1D4B9,0x1D4BB,0x1D4BD,0x1D4BE,
                0x1D4BF,0x1D4C0,0x1D4C1,0x1D4C2,0x1D4C3,0x1D4C5,0x1D4C6,0x1D4C7,0x1D4C8,
                0x1D4C9,0x1D4CA,0x1D4CB,0x1D4CC,0x1D4CD,0x1D4CE,0x1D4CF};
        char scriptLowerC = 'a';
        for (int cp : scriptLower) map(cp, String.valueOf(scriptLowerC++));

        // Mathematical Double-Struck (U+1D538 - U+1D551: A-Z, U+1D552 - U+1D56B: a-z)
        for (int i = 0; i < 26; i++) map(0x1D538 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D552 + i, String.valueOf((char) ('a' + i)));

        // Mathematical Fraktur (U+1D504 - U+1D51D: A-Z, U+1D51E - U+1D537: a-z)
        for (int i = 0; i < 26; i++) map(0x1D504 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D51E + i, String.valueOf((char) ('a' + i)));

        // Mathematical Sans-Serif (U+1D5A0 - U+1D5B9: A-Z, U+1D5BA - U+1D5D3: a-z)
        for (int i = 0; i < 26; i++) map(0x1D5A0 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D5BA + i, String.valueOf((char) ('a' + i)));

        // Mathematical Sans-Serif Bold (U+1D5D4 - U+1D5ED: A-Z, U+1D5EE - U+1D607: a-z)
        for (int i = 0; i < 26; i++) map(0x1D5D4 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D5EE + i, String.valueOf((char) ('a' + i)));

        // Mathematical Monospace (U+1D670 - U+1D689: A-Z, U+1D68A - U+1D6A3: a-z)
        for (int i = 0; i < 26; i++) map(0x1D670 + i, String.valueOf((char) ('A' + i)));
        for (int i = 0; i < 26; i++) map(0x1D68A + i, String.valueOf((char) ('a' + i)));
    }

    private static void map(int codePoint, String ascii) {
        MAP.put(codePoint, ascii);
    }

    public String decode(String input) {
        int len = input.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c < 128) {
                sb.append(c);
            } else if (Character.isHighSurrogate(c) && i + 1 < len) {
                char low = input.charAt(i + 1);
                int cp = Character.toCodePoint(c, low);
                String replacement = MAP.get(cp);
                if (replacement != null) {
                    sb.append(replacement);
                } else {
                    sb.append(c);
                    sb.append(low);
                }
                i++;
            } else {
                String replacement = MAP.get((int) c);
                if (replacement != null) {
                    sb.append(replacement);
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}

