package nothing.chatfilter.normalize;

public class NormalizationPipeline {

    private final ZeroWidthStripper zeroWidthStripper;
    private final UnicodeNormalizer unicodeNormalizer;
    private final ConfusableDecoder confusableDecoder;
    private final LeetSpeakDecoder leetSpeakDecoder;
    private final SeparatorNormalizer separatorNormalizer;

    public NormalizationPipeline() {
        this.zeroWidthStripper    = new ZeroWidthStripper();
        this.unicodeNormalizer    = new UnicodeNormalizer();
        this.confusableDecoder    = new ConfusableDecoder();
        this.leetSpeakDecoder     = new LeetSpeakDecoder();
        this.separatorNormalizer  = new SeparatorNormalizer();
    }

    public String process(String input) {
        if (input == null || input.isEmpty()) return input;
        String s = zeroWidthStripper.strip(input);
        s = unicodeNormalizer.normalize(s);
        s = confusableDecoder.decode(s);
        s = leetSpeakDecoder.decode(s);
        s = separatorNormalizer.normalize(s);
        return s;
    }
}

