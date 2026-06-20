package nothing.chatfilter.detect;

public class LengthBasedThreshold {

    private final int maxDistShort;
    private final int maxDistMedium;
    private final int maxDistLong;
    private final double maxRelDist;
    private final int shortBoundary;
    private final int mediumBoundary;

    public LengthBasedThreshold() {
        this(1, 2, 3, 0.35, 4, 8);
    }

    public LengthBasedThreshold(int maxDistShort, int maxDistMedium, int maxDistLong,
                                double maxRelDist, int shortBoundary, int mediumBoundary) {
        this.maxDistShort = maxDistShort;
        this.maxDistMedium = maxDistMedium;
        this.maxDistLong = maxDistLong;
        this.maxRelDist = maxRelDist;
        this.shortBoundary = shortBoundary;
        this.mediumBoundary = mediumBoundary;
    }

    public int getMaxDistance(int wordLength) {
        if (wordLength <= shortBoundary) return maxDistShort;
        if (wordLength <= mediumBoundary) return maxDistMedium;
        return maxDistLong;
    }

    public double getMaxRelDist() {
        return maxRelDist;
    }

    public boolean isWithinThreshold(int distance, int wordLength) {
        return distance <= getMaxDistance(wordLength)
                && (double) distance / wordLength <= maxRelDist;
    }
}

