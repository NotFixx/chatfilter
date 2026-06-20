package nothing.chatfilter.normalize;

import java.util.regex.Pattern;

public class SeparatorNormalizer {

    private static final Pattern DOT_VARIANTS =
            Pattern.compile("\\(\\s*d[o0]t\\s*\\)|\\[dot]| dot ", Pattern.CASE_INSENSITIVE);

    private static final Pattern DOTS_BETWEEN_LETTERS =
            Pattern.compile("(?<=\\p{L})\\.+(?=\\p{L})");

    private static final Pattern SPACED_LETTERS =
            Pattern.compile("\\b([a-zA-Z])( [a-zA-Z])+\\b");

    private static final Pattern REPEATED_PUNCTUATION =
            Pattern.compile("([^a-zA-Z0-9])\\1+");

    private static final Pattern SEPARATOR_CLEANUP =
            Pattern.compile("\\s*([*_\\-|])(?!\\s)");
    private static final Pattern BRACKET_CLEANUP =
            Pattern.compile("(?<=\\p{L})[()\\[\\]{}]+(?=\\p{L})");
    private static final Pattern INLINE_PUNCT_CLEANUP =
            Pattern.compile("(?<=\\p{L})[*_'\\-|](?=\\p{L})");

    public String normalize(String input) {
        String s = DOT_VARIANTS.matcher(input).replaceAll(".");
        s = DOTS_BETWEEN_LETTERS.matcher(s).replaceAll("");
        s = SPACED_LETTERS.matcher(s).replaceAll(m -> m.group().replace(" ", ""));
        s = REPEATED_PUNCTUATION.matcher(s).replaceAll("$1");
        s = SEPARATOR_CLEANUP.matcher(s).replaceAll("$1");
        s = BRACKET_CLEANUP.matcher(s).replaceAll("");
        s = INLINE_PUNCT_CLEANUP.matcher(s).replaceAll("");
        return s;
    }
}
