package nothing.chatfilter.detect;

import java.util.*;

/**
 * Aho-Corasick automaton with:
 *  - Array-backed children (O(1), cache-friendly) instead of HashMap
 *  - Complete DFA via goto-compression (no while-loop in search)
 *  - Output propagation through failure links during build
 *  - ArrayDeque instead of LinkedList for BFS
 */
public class AhoCorasick {

    // Restrict to printable ASCII. Bump to 65536 for full Unicode (higher memory cost).
    private static final int ALPHA = 128;

    private static final class Node {
        final Node[] next = new Node[ALPHA]; // goto table (completed during build)
        Node fail;
        boolean output;
        String pattern; // the pattern ending at this node, null if not an output
    }

    private final Node root = new Node();
    private final List<String> patterns = new ArrayList<>();

    /** Add a pattern before calling {@link #buildFailureLinks()}. */
    public void addPattern(String pattern) {
        patterns.add(pattern);
        Node node = root;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c >= ALPHA) throw new IllegalArgumentException(
                    "Character out of supported range: '" + c + "' (code " + (int) c + ")");
            if (node.next[c] == null) node.next[c] = new Node();
            node = node.next[c];
        }
        node.output = true;
        node.pattern = pattern;
    }

    /**
     * Builds failure links AND completes the goto table so that every
     * (node, char) pair has a valid transition â€” no while-loop needed at search time.
     */
    public void buildFailureLinks() {
        // Phase 1: root's missing transitions loop back to root (standard trick).
        for (int c = 0; c < ALPHA; c++) {
            if (root.next[c] == null) root.next[c] = root;
        }

        // Phase 2: BFS â€” set failure links and compress missing transitions.
        Deque<Node> queue = new ArrayDeque<>();
        for (int c = 0; c < ALPHA; c++) {
            Node child = root.next[c];
            if (child != root) {          // real child, not a loopback
                child.fail = root;
                queue.add(child);
            }
        }

        while (!queue.isEmpty()) {
            Node cur = queue.poll();

            // Propagate output: if any node on the fail-chain is an output node, so is cur.
            if (cur.fail.output) {
                cur.output = true;
                if (cur.pattern == null) cur.pattern = cur.fail.pattern;
            }

            for (int c = 0; c < ALPHA; c++) {
                Node child = cur.next[c];
                if (child == null) {
                    // Goto compression: missing transition follows the failure link's transition.
                    cur.next[c] = cur.fail.next[c];
                } else {
                    // Real child: its failure link is the failure-node's goto on c.
                    child.fail = cur.fail.next[c];
                    queue.add(child);
                }
            }
        }
    }

    /**
     * O(n) search â€” one array lookup per character, zero failure-link traversal.
     * Non-ASCII characters reset the automaton to root (word boundary).
     *
     * @param text the text to search
     * @return true if any registered pattern is found
     */
    public boolean containsAny(String text) {
        return findAny(text) != null;
    }

    /**
     * O(n) search returning the first matched pattern, or null if none found.
     */
    public String findAny(String text) {
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= ALPHA) {
                node = root;
                continue;
            }
            node = node.next[c];
            if (node.output) return node.pattern;
        }
        return null;
    }
    /** Returns all patterns added to the automaton. */
    public List<String> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }
}
