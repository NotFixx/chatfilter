package nothing.chatfilter.normalize;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class UnicodeNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

    public String normalize(String input) {
        String nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC);
        String nfkd = Normalizer.normalize(nfkc, Normalizer.Form.NFKD);
        return COMBINING_MARKS.matcher(nfkd).replaceAll("");
    }
}

