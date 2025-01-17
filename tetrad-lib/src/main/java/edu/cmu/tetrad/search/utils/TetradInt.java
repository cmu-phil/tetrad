package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a tetrad of nodes for vanishing tetrad constraints.
 * Equivalent tetrads result from permutations of {i, j, k, l} that
 * yield the same set of vanishing tetrad equalities.
 *
 * @param i First node.
 * @param j Second node.
 * @param k Third node.
 * @param l Fourth node.
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
        // Sort all nodes to ensure equivalent tetrads have the same hash
        int[] sortedNodes = {i, j, k, l};
        Arrays.sort(sortedNodes);
        return Arrays.hashCode(sortedNodes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TetradInt other)) return false;

        // Sort nodes for both tetrads and compare
        int[] thisNodes = {this.i, this.j, this.k, this.l};
        int[] otherNodes = {other.i, other.j, other.k, other.l};
        Arrays.sort(thisNodes);
        Arrays.sort(otherNodes);

        return Arrays.equals(thisNodes, otherNodes);
    }

    @Override
    public String toString() {
        // Sort nodes for consistent representation
        int[] sortedNodes = {i, j, k, l};
        Arrays.sort(sortedNodes);
        return String.format("TetradInt({%d, %d, %d, %d})",
                sortedNodes[0], sortedNodes[1], sortedNodes[2], sortedNodes[3]);
    }
}
