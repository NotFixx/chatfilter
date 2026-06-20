package nothing.chatfilter.normalize;

public class LeetSpeakDecoder {

    private static final char[] SINGLE = new char[128];
    private static final String[][] MULTI = {
        {"|_|",  "u"},
        {"|3",   "b"},
        {"|2",   "r"},
        {"|\\/|", "m"},
        {"\\/\\/","w"},
        {"\\/",  "v"},
        {"'/",   "y"},
        {"><",   "x"},
        {"ph",   "f"},
    };

    static {
        SINGLE['@'] = 'a'; SINGLE['4'] = 'a';
        SINGLE['3'] = 'e';
        SINGLE['1'] = 'i';
        SINGLE['0'] = 'o';
        SINGLE['$'] = 's'; SINGLE['5'] = 's';
        SINGLE['7'] = 't'; SINGLE['+'] = 't';
        SINGLE['8'] = 'b';
        SINGLE['6'] = 'g'; SINGLE['9'] = 'g';
        SINGLE['!'] = 'i';
        SINGLE['2'] = 'z';
    }

    public String decode(String input) {
        int len = input.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);

            boolean matched = false;
            for (String[] entry : MULTI) {
                String pattern = entry[0];
                if (regionMatches(input, i, pattern)) {
                    sb.append(entry[1]);
                    i += pattern.length() - 1;
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            if (c < 128 && SINGLE[c] != 0) {
                sb.append(SINGLE[c]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean regionMatches(String s, int offset, String pattern) {
        if (offset + pattern.length() > s.length()) return false;
        for (int i = 0; i < pattern.length(); i++) {
            if (s.charAt(offset + i) != pattern.charAt(i)) return false;
        }
        return true;
    }
}

