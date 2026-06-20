package nothing.chatfilter.detect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class CommonEnglishWords {

    private static final Set<String> WORDS = new HashSet<>(550000, 0.75f);

    private static final Set<String> ENGLISH_TWO_LETTER = Set.of(
        "am", "an", "as", "at", "be", "by", "do", "go", "he", "hi",
        "if", "in", "is", "it", "my", "no", "of", "oh", "ok", "on",
        "or", "so", "to", "up", "us", "we", "ye"
    );

    static {
        try (InputStream is = CommonEnglishWords.class.getClassLoader()
                .getResourceAsStream("common_words.txt")) {
            if (is == null) throw new IOException("common_words.txt not found on classpath");
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (String token : text.split("[,\r\n]+")) {
                String w = token.trim();
                if (!w.isEmpty()) WORDS.add(w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load common_words.txt", e);
        }
    }

    public static boolean isCommonWord(String word) {
        if (word == null || word.isEmpty()) return false;
        // Already lowercased by all callers (isSuspiciousTokenLower)
        int len = word.length();

        if (len == 1) {
            char c = word.charAt(0);
            return c >= 'a' && c <= 'z';
        }
        if (len == 2) return ENGLISH_TWO_LETTER.contains(word);
        if (len <= 4 && isAllDigits(word)) return true;

        return WORDS.contains(word);
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
}

