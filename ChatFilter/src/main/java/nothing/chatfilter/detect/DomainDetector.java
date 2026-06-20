package nothing.chatfilter.detect;

import nothing.chatfilter.ConfigManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DomainDetector {

    // â”€â”€ All TLDs â€“ no duplicates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final String ALL_TLDS = deduplicate(
            "com", "net", "org", "io", "gg", "xyz", "me", "tv", "to", "co", "fun", "biz",
            "info", "online", "site", "store", "club", "live", "pro", "dev", "app",
            "tech", "ai", "cc", "top", "icu", "cfd", "gdn", "loan", "ooo", "win",
            "best", "work", "kim", "pub", "lol", "wtf", "pics", "mom", "casa", "wiki",
            "bid", "trade", "cricket", "stream", "racing", "accountant", "science",
            "party", "date", "faith", "review", "support", "ninja", "solutions",
            "company", "life", "network", "world", "email", "website", "click",
            "link", "help", "design", "guru", "photography", "photos", "exposed",
            "software", "legal", "engineer", "lawyer", "doctor", "dentist", "finance",
            "fund", "market", "media", "news", "press", "vodka", "wine", "beer",
            "coffee", "pizza", "recipes", "restaurant", "sarl", "schule", "sucks",
            "claims", "credit", "insure", "tax", "vet", "actor", "band", "cab",
            "camp", "cards", "careers", "catering", "center", "chat", "cheap",
            "church", "city", "cleaning", "clinic", "clothing", "coach", "codes",
            "community", "computer", "condos", "construction", "consulting",
            "contractors", "cooking", "cool", "country", "coupons", "courses",
            "creditcard", "cruises", "dad", "dance", "dating", "deals", "degree",
            "delivery", "democrat", "dental", "diamonds", "diet", "digital",
            "direct", "directory", "discount", "diy", "dog", "domains", "earth",
            "education", "energy", "engineering", "enterprises", "equipment",
            "estate", "events", "exchange", "expert", "fail", "family", "farm",
            "fashion", "feedback", "film", "fitness", "flights", "florist",
            "flowers", "football", "forsale", "foundation", "gallery", "garden",
            "gift", "gives", "glass", "global", "gmbh", "graphics", "gratis",
            "green", "gripe", "group", "guide", "guitars", "hamburg", "haus",
            "healthcare", "hockey", "holdings", "holiday", "hosting", "house",
            "how", "immobilien", "industries", "ink", "institute", "international",
            "investments", "irish", "jetzt", "jewelry", "jobs", "kitchen", "land",
            "lease", "leclerc", "limited", "loans", "london", "lotto", "love",
            "ltd", "management", "map", "marketing", "mba", "memorial", "mobi",
            "moda", "money", "movie", "nagoya", "name", "navy", "okinawa", "one",
            "onl", "organic", "partners", "parts", "pet", "physio", "pink", "place",
            "plumbing", "plus", "poker", "productions", "promo", "properties",
            "qpon", "reisen", "rent", "rentals", "repair", "report", "republican",
            "reviews", "rich", "rip", "rocks", "rodeo", "ruhr", "run", "sale",
            "school", "services", "sex", "sexy", "shiksha", "shoes", "shopping",
            "show", "singles", "ski", "soccer", "social", "solar", "space",
            "storage", "studio", "style", "supplies", "supply", "surgery", "sydney",
            "systems", "taipei", "tattoo", "taxi", "team", "technology", "tennis",
            "theater", "tienda", "tips", "tires", "today", "tools", "tours",
            "town", "toys", "training", "travel", "university", "vacations",
            "ventures", "viajes", "video", "villas", "vision", "voyage", "watch",
            "webcam", "wedding", "whoswho", "wine", "work", "works", "world",
            "yoga", "zone");

    // â”€â”€ TLDs of 3+ characters for squished detection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final String SQUISHED_TLDS = Arrays.stream(ALL_TLDS.split("\\|"))
            .filter(tld -> tld.length() >= 3)
            .collect(Collectors.joining("|"));

    // â”€â”€ All TLDs as a Set for runtime label extraction â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final Set<String> TLD_SET = Arrays.stream(ALL_TLDS.split("\\|"))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    // â”€â”€ Repeated character collapse (like SwearDetector) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final Pattern COLLAPSE = Pattern.compile("(.)\\1{2,}");

    // â”€â”€ Domain detection â€“ explicit separators only (no raw spaces â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
    private static final Pattern DIRECT_DOMAIN = Pattern.compile(
            "(?i)\\b(?:[a-z0-9-]+\\s*(?:\\.|\\(dot\\)|\\[dot]|dot|,|-|_|\\*)\\s*)+(?:" + ALL_TLDS + ")"
                    + "(?:\\s*:\\s*\\d{1,5})?\\b"
    );

    // â”€â”€ Squished domains (no separator) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Exclusion handled by CommonEnglishWords + player name checks at runtime.
    @SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
    private static final Pattern SQUISHED_DOMAIN = Pattern.compile(
            "(?i)\\b[a-z0-9-]{3,}(?:" + SQUISHED_TLDS + ")\\b"
    );

    // â”€â”€ Discord pattern â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final Pattern DISCORD = Pattern.compile(
            "(?i)discord\\s*(?:\\.|\\(dot\\)|\\[dot]|dot|,|-|_|\\*|\\s)+" +
                    "(?:gg/\\w+|com/invite/\\w+)"
    );

    // â”€â”€ Invite phrases â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final Pattern INVITE_PHRASES = Pattern.compile(
            "(?i)(?:join\\s+(?:my\\s+)?server|\\bip:\\s*[a-z0-9.\\s]+|" +
                    "\\bjoin\\s+\\w+(?:\\s*,?\\s*\\w+)+[,.:]\\s*\\d{3,5})"
    );

    private final ConfigManager config;

    public DomainDetector(ConfigManager config) {
        this.config = config;
    }

    public boolean matches(String text) {
        return matches(text, Collections.emptySet());
    }

    public boolean matches(String text, Set<String> playerNames) {
        return matches(text, playerNames, playerNames);
    }

    // â”€â”€ Single-character domain separators from DIRECT_DOMAIN â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final String DIRECT_SEPARATORS = ".,_-*";

    /**
     * @param playerNames      used for DIRECT_DOMAIN exact-match bypass only
     * @param squishedNames    used for SQUISHED_DOMAIN checks (includes leet-decoded variants)
     */
    public boolean matches(String text, Set<String> playerNames, Set<String> squishedNames) {
        if (config.isDomainDetectionEnabled()) return false;
        text = COLLAPSE.matcher(text).replaceAll("$1$1");
        if (config.blockDiscord() && DISCORD.matcher(text).find()) return true;
        if (config.blockInvitePhrases() && INVITE_PHRASES.matcher(text).find()) return true;
        if (config.blockGenericDomains()) {
            java.util.regex.Matcher dm = DIRECT_DOMAIN.matcher(text);
            while (dm.find()) {
                String lower = dm.group().toLowerCase();
                if (playerNames.contains(lower)) continue;
                if (CommonEnglishWords.isCommonWord(lower)) continue;
                // If the TLD portion is a common word and there's only one
                // separator, skip â€” likely not a real domain attempt.
                if (isSingleSepCommonTld(lower)) continue;
                return true;
            }
            java.util.regex.Matcher sm = SQUISHED_DOMAIN.matcher(text);
            while (sm.find()) {
                String word = sm.group();
                String lower = word.toLowerCase();
                if (squishedNames.contains(lower)) continue;
                if (CommonEnglishWords.isCommonWord(lower)) continue;
                // Fallback: try removing each TLD suffix and check if the
                // remaining label matches an online player name.
                if (matchesPlayerNameByTldStrip(lower, squishedNames)) continue;
                // Check if the label before the TLD is a common English word.
                // Catches false positives like "suckdad" ("suck" + TLD "dad").
                if (isPrefixCommonWord(lower)) continue;
                return true;
            }
        }
        return false;
    }

    private boolean isSingleSepCommonTld(String match) {
        int sepCount = 0, sepIdx = -1;
        char sepChar = 0;
        for (int i = 0; i < match.length(); i++) {
            char c = match.charAt(i);
            if (DIRECT_SEPARATORS.indexOf(c) >= 0) { sepCount++; sepIdx = i; sepChar = c; }
        }
        if (sepCount != 1) return false;
        // Dots between labels are always real domain attempts â€” don't exempt
        if (sepChar == '.') return false;
        String tld = match.substring(sepIdx + 1).trim();
        return !tld.isEmpty() && CommonEnglishWords.isCommonWord(tld);
    }

    /** Checks if stripping any TLD suffix from the match leaves a player name. */
    private static boolean matchesPlayerNameByTldStrip(String lower, Set<String> playerNames) {
        for (String tld : TLD_SET) {
            if (lower.length() > tld.length() && lower.endsWith(tld)) {
                String label = lower.substring(0, lower.length() - tld.length());
                if (playerNames.contains(label)) return true;
            }
        }
        return false;
    }

    /** Checks if stripping any TLD suffix leaves a common English word. */
    private static boolean isPrefixCommonWord(String lower) {
        for (String tld : TLD_SET) {
            if (lower.length() > tld.length() && lower.endsWith(tld)) {
                String label = lower.substring(0, lower.length() - tld.length());
                if (CommonEnglishWords.isCommonWord(label)) return true;
            }
        }
        return false;
    }

    /** Removes duplicate TLDs while keeping insertion order. */
    // harmless â€“ the parameter is always a constant list
    private static String deduplicate(String... tlds) {
        Set<String> seen = new LinkedHashSet<>();
        for (String tld : tlds) {
            seen.add(tld.toLowerCase());
        }
        return String.join("|", seen);
    }
}
