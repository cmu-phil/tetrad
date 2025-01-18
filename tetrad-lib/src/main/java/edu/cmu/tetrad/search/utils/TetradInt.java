package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Objects;

/**
 * Represents an ordered tetrad (quartet) of nodes,
 * where the order of nodes within {i, j} and {k, l} does not matter,
 * but the order of the pairs <{i, j}, {k, l}> does matter.
 *
 * @param i The first node.
 * @param j The second node.
 * @param k The third node.
 * @param l The fourth node.
 */
public record TetradInt(int i, int j, int k, int l) implements TetradSerializable {

    // Ensure distinct nodes
    public TetradInt {
        if (i == j || i == k || i == l || j == k || j == l || k == l) {
            throw new IllegalArgumentException("Nodes must be distinct.");
        }
    }

    @Override
    public int hashCode() {
        // Sort nodes within each pair, then hash the ordered pairs
        int min1 = Math.min(i, j);
        int max1 = Math.max(i, j);
        int min2 = Math.min(k, l);
        int max2 = Math.max(k, l);

        // Hash the ordered pairs <{i, j}, {k, l}>
        return Objects.hash(min1, max1, min2, max2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TetradInt(int i1, int j1, int k1, int l1))) return false;

        // Sort nodes within each pair
        int min1This = Math.min(this.i, this.j);
        int max1This = Math.max(this.i, this.j);
        int min2This = Math.min(this.k, this.l);
        int max2This = Math.max(this.k, this.l);

        int min1Other = Math.min(i1, j1);
        int max1Other = Math.max(i1, j1);
        int min2Other = Math.min(k1, l1);
        int max2Other = Math.max(k1, l1);

        // Compare ordered pairs <{i, j}, {k, l}>
        return min1This == min1Other && max1This == max1Other &&
               min2This == min2Other && max2This == max2Other;
    }

    @Override
    public String toString() {
        return String.format("TetradInt(<{%d, %d}, {%d, %d}>)",
                Math.min(i, j), Math.max(i, j),
                Math.min(k, l), Math.max(k, l));
    }
}
