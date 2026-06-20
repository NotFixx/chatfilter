package nothing.chatfilter.detect;

import nothing.chatfilter.ConfigManager;
import nothing.chatfilter.io.FilterDataDB;

import java.util.Set;
import java.util.regex.Pattern;

public class SwearDetector {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHA  = Pattern.compile("[^a-z\\s]");

    private final boolean     enabled;
    private final AhoCorasick exactAutomaton = new AhoCorasick();
    private final BKTree      fuzzyTree      = new BKTree();
    private final Set<String> basicWords; // exact-match only, checked as whole tokens
    private final LengthBasedThreshold threshold;

    public SwearDetector(ConfigManager config, FilterDataDB filterData) {
        this(config, filterData, new LengthBasedThreshold());
    }

    public SwearDetector(ConfigManager config, FilterDataDB filterData, LengthBasedThreshold threshold) {
        this.enabled = config.isSwearDetectionEnabled();
        this.threshold = threshold;
        if (!enabled) { this.basicWords = Set.of(); return; }

        // Fuzzy words go into the exact automaton (AC) for substring detection.
        for (String word : filterData.getAllFuzzySwearWords()) {
            exactAutomaton.addPattern(word);
        }
        // Only fuzzy words go into the BKTree. Basic (exact-match) words are
        // checked separately and skip fuzzy matching entirely.
        for (String word : filterData.getAllFuzzySwearWords()) {
            fuzzyTree.add(word);
        }
        // Basic words: exact-match only, checked against the whole token.
        this.basicWords = Set.copyOf(filterData.getAllBasicSwearWords());
        exactAutomaton.buildFailureLinks();
    }

    public boolean matches(String text) {
        if (!enabled) return false;

        String lower = text.toLowerCase();
        String[] tokens = WHITESPACE.split(lower);

        int nonEmptyCount = 0;
        String first = null, second = null;

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            nonEmptyCount++;
            if (nonEmptyCount == 1) first = token;
            else if (nonEmptyCount == 2) second = token;

            if (isSuspiciousTokenLower(token)) return true;
        }

        // Bare split basic word: exactly 2 non-empty tokens that form a basic
        // word when concatenated. Catches "not fix" â†’ "notfix" with no extra text.
        if (nonEmptyCount == 2 && first != null && second != null
                && basicWords.contains(first + second)) return true;

        return false;
    }

    /** Internal â€” assumes token is already lowercased, skips redundant toLowerCase(). */
    private boolean isSuspiciousTokenLower(String token) {
        token = token.replaceAll("^[^a-z]+|[^a-z]+$", "");
        if (token.isEmpty()) return false;
        if (CommonEnglishWords.isCommonWord(token)) return false;
        if (basicWords.contains(token)) return true;
        // findAny returns the first pattern found as a substring.
        // Patterns â‰¥ 4 chars match at start or end of the word (prevents
        // false positives like "tit" in "titanium" / "title").
        // Patterns of exactly 3 chars match only as a suffix â€” this catches
        // compound evasions like "dumass" containing "ass" while still
        // avoiding "assassin" / "brassiere" start-of-word FPs.
        String matched = exactAutomaton.findAny(token);
        if (matched != null) {
            int mLen = matched.length();
            if (mLen >= 4) {
                int idx = token.indexOf(matched);
                if (idx == 0 || idx + mLen == token.length()) return true;
            } else if (mLen >= 3) {
                int idx = token.indexOf(matched);
                if (idx + mLen == token.length()) return true;
            }
        }
        int len = token.length();
        if (len < 3) return false;
        int    maxDist    = threshold.getMaxDistance(len);
        double maxRelDist = threshold.getMaxRelDist();
        // For short words (â‰¤4), reject same-length fuzzy matches at dist=1 â€” they're
        // usually false positives like "tvt"â†’"tit" or "raye"â†’"rape". Only
        // exact tree matches (dist=0) or cross-length matches pass.
        if (len <= 4) {
            return fuzzyTree.containsSimilar(token, maxDist, maxRelDist,
                    (m, d) -> !(d == 1 && m.length() == len));
        }
        // Nearest neighbor: find the closest matching swear word within threshold.
        return fuzzyTree.nearestDistance(token, maxDist, maxRelDist) >= 0;
    }

    public boolean isObfuscatedSwear(String originalMessage) {
        if (!enabled) return false;

        String   cleaned  = NON_ALPHA.matcher(originalMessage.toLowerCase()).replaceAll("");
        String[] tokens   = WHITESPACE.split(cleaned);
        String   squished = WHITESPACE.matcher(cleaned).replaceAll("");

        // 1. Check exact automaton (spaced-out exact substring matches)
        if (exactAutomaton.containsAny(squished)) {
            for (String swear : exactAutomaton.getPatterns()) {
                int idx = squished.indexOf(swear);
                if (idx == -1) continue;

                int charPos = 0, startToken = -1, endToken = -1;
                for (int i = 0; i < tokens.length; i++) {
                    int len = tokens[i].length();
                    if (len == 0) continue;
                    if (charPos < idx + swear.length() && charPos + len > idx) {
                        if (startToken == -1) startToken = i;
                        endToken = i;
                    }
                    charPos += len;
                }

                if (startToken == -1) continue;

                int firstTokenStart = 0;
                for (int i = 0; i < startToken; i++) firstTokenStart += tokens[i].length();

                StringBuilder blob      = new StringBuilder();
                boolean       allCommon = true;

                for (int i = startToken; i <= endToken; i++) {
                    String t = tokens[i];
                    blob.append(t);
                    if (!CommonEnglishWords.isCommonWord(t)) allCommon = false;
                }

                String blobStr = blob.toString();
                if (CommonEnglishWords.isCommonWord(blobStr)) continue;
                if (allCommon && idx > firstTokenStart) continue;

                if (startToken == endToken) {
                    int swearIdxInToken = idx - firstTokenStart;
                    String singleToken = tokens[startToken];
                    if (swearIdxInToken > 0
                            && swearIdxInToken + swear.length() < singleToken.length())
                        continue;
                }

                return true;
            }
        }

        // 2. BKTree fuzzy check: remove common words, squish remainder, fuzzy match
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (!CommonEnglishWords.isCommonWord(token)) {
                sb.append(token);
            }
        }
        String minimal = sb.toString();
        if (minimal.length() >= 3) {
            int ml = minimal.length();
            int maxDist    = threshold.getMaxDistance(ml);
            double maxRelDist = threshold.getMaxRelDist();
            if (ml <= 4) {
                if (fuzzyTree.containsSimilar(minimal, maxDist, maxRelDist,
                        (m, d) -> !(d == 1 && m.length() == ml))) return true;
            } else {
                if (fuzzyTree.nearestDistance(minimal, maxDist, maxRelDist) >= 0) return true;
            }
        }

        return false;
    }
}
