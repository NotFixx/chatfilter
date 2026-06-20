package nothing.chatfilter.detect;

import java.util.*;
import java.util.function.BiPredicate;

public class BKTree {
    private Node root;

    // Reuse levenshtein row arrays per thread â€” avoids two int[] allocations per call
    private static final ThreadLocal<int[][]> ROWS =
            ThreadLocal.withInitial(() -> new int[2][256]);

    private static class Node {
        final String word;
        // BK tree nodes rarely have more than a few children; initial cap 4 saves memory
        final Map<Integer, Node> children = new HashMap<>(4);
        Node(String word) { this.word = word; }
    }

    public void add(String word) {
        if (root == null) { root = new Node(word); return; }
        add(root, word);
    }

    private void add(Node node, String word) {
        int dist = levenshtein(node.word, word);
        if (dist == 0) return;
        Node child = node.children.get(dist);
        if (child == null) node.children.put(dist, new Node(word));
        else add(child, word);
    }

    /**
     * Fuzzy search with both an absolute max distance and a relative similarity
     * ratio.  A match requires:
     *   editDistance <= maxDist   AND
     *   editDistance / max(wordLen, queryLen) <= maxRelDist
     * <p>
     * The relative check prevents false positives where a short swear word
     * happens to be a small edit from a much longer innocent word.
     * <p>
     * Example: with maxDist=2, maxRelDist=0.40:
     *   "class" (5) vs "ass" (3): dist=2, 2/5=0.40 â†’ match
     *   "truck" (5) vs "fuck" (4): dist=2, 2/5=0.40 â†’ match
     *   With maxRelDist=0.35: both rejected â†’ fewer false positives
     */
    public boolean containsSimilar(String word, int maxDist, double maxRelDist) {
        return root != null && containsSimilar(root, word, maxDist, maxRelDist);
    }

    /**
     * Like {@link #containsSimilar(String, int, double)} but with an additional
     * predicate that is called for every candidate match.  Only matches that
     * pass the predicate are returned.
     */
    public boolean containsSimilar(String word, int maxDist, double maxRelDist,
                                   BiPredicate<String, Integer> filter) {
        return root != null && containsSimilar(root, word, maxDist, maxRelDist, filter);
    }

    /**
     * Returns the minimum edit distance to any word in the tree that falls
     * within the given thresholds, or -1 if no such word exists.
     * <p>
     * This enables "nearest neighbor wins" logic: the closest matching swear
     * word determines the result, and its distance can inform confidence.
     */
    public int nearestDistance(String word, int maxDist, double maxRelDist) {
        if (root == null) return -1;
        int[] best = {Integer.MAX_VALUE};
        nearestDistance(root, word, maxDist, maxRelDist, best);
        return best[0] == Integer.MAX_VALUE ? -1 : best[0];
    }

    /**
     * Returns both the nearest matching word and its edit distance, or null
     * if no match is within the threshold.
     */
    public Match nearestMatch(String word, int maxDist, double maxRelDist) {
        if (root == null) return null;
        String[] bestWord = {null};
        int[] bestDist = {Integer.MAX_VALUE};
        nearestMatch(root, word, maxDist, maxRelDist, bestWord, bestDist);
        return bestWord[0] != null ? new Match(bestWord[0], bestDist[0]) : null;
    }

    public record Match(String word, int distance) {}

    private boolean containsSimilar(Node node, String word, int maxDist, double maxRelDist) {
        int dist = levenshtein(node.word, word);
        if (dist <= maxDist) {
            double rel = (double) dist / Math.max(node.word.length(), word.length());
            if (rel <= maxRelDist) return true;
        }
        int lo = Math.max(1, dist - maxDist);
        int hi = dist + maxDist;
        for (int d = lo; d <= hi; d++) {
            Node child = node.children.get(d);
            if (child != null && containsSimilar(child, word, maxDist, maxRelDist)) return true;
        }
        return false;
    }

    private boolean containsSimilar(Node node, String word, int maxDist, double maxRelDist,
                                    BiPredicate<String, Integer> filter) {
        int dist = levenshtein(node.word, word);
        if (dist <= maxDist) {
            double rel = (double) dist / Math.max(node.word.length(), word.length());
            if (rel <= maxRelDist && filter.test(node.word, dist)) return true;
        }
        int lo = Math.max(1, dist - maxDist);
        int hi = dist + maxDist;
        for (int d = lo; d <= hi; d++) {
            Node child = node.children.get(d);
            if (child != null && containsSimilar(child, word, maxDist, maxRelDist, filter)) return true;
        }
        return false;
    }

    private void nearestDistance(Node node, String word, int maxDist, double maxRelDist, int[] best) {
        int dist = levenshtein(node.word, word);
        if (dist < best[0] && dist <= maxDist) {
            double rel = (double) dist / Math.max(node.word.length(), word.length());
            if (rel <= maxRelDist) best[0] = dist;
        }
        int lo = Math.max(1, dist - maxDist);
        int hi = dist + maxDist;
        if (lo > hi) return;
        for (int d = lo; d <= hi; d++) {
            Node child = node.children.get(d);
            if (child != null) nearestDistance(child, word, maxDist, maxRelDist, best);
        }
    }

    private void nearestMatch(Node node, String word, int maxDist, double maxRelDist,
                               String[] bestWord, int[] bestDist) {
        int dist = levenshtein(node.word, word);
        if (dist < bestDist[0] && dist <= maxDist) {
            double rel = (double) dist / Math.max(node.word.length(), word.length());
            if (rel <= maxRelDist) {
                bestDist[0] = dist;
                bestWord[0] = node.word;
            }
        }
        int lo = Math.max(1, dist - maxDist);
        int hi = dist + maxDist;
        if (lo > hi) return;
        for (int d = lo; d <= hi; d++) {
            Node child = node.children.get(d);
            if (child != null) nearestMatch(child, word, maxDist, maxRelDist, bestWord, bestDist);
        }
    }

    private int levenshtein(String a, String b) {
        if (a.equals(b)) return 0;
        int aLen = a.length(), bLen = b.length();
        if (aLen == 0) return bLen;
        if (bLen == 0) return aLen;

        int[][] rows = ROWS.get();
        // Grow if the cached arrays are too small for this input
        if (rows[0].length <= bLen) {
            rows = new int[2][bLen + 16];
            ROWS.set(rows);
        }

        int[] prev = rows[0];
        int[] curr = rows[1];

        for (int j = 0; j <= bLen; j++) prev[j] = j;

        for (int i = 1; i <= aLen; i++) {
            curr[0] = i;
            char ai = a.charAt(i - 1); // cached â€” was re-called bLen times per outer iteration
            for (int j = 1; j <= bLen; j++) {
                int cost = ai == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }

        return prev[bLen];
    }
}
