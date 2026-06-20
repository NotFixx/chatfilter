package nothing.chatfilter.pipeline;

public record FilterResult(Decision decision, String reason, String rule) {
    public static final FilterResult PASS = new FilterResult(Decision.PASS, null, null);

    public static FilterResult block(String reason, String rule) {
        return new FilterResult(Decision.BLOCK, reason, rule);
    }

    public enum Decision { PASS, BLOCK }
}

