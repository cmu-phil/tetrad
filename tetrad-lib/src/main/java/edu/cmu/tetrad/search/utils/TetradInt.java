package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ordered tetrad of nodes.
 *
 * @param i The first node.
 * @param j The second node.
 * @param k The third node.
 * @param l The fourth node.
 * @author josephramsey
 * @version $Id: $Id
 */
public record TetradInt(int i, int j, int k, int l) implements TetradSerializable {

    // Check distinctness if i, j, k, l
    public TetradInt {
        if (i == j || i == k || i == l || j == k || j == l || k == l) {
            throw new IllegalArgumentException("Nodes must be distinct.");
        }
    }

    /**
     * Computes the hash code for this object using the sum of its components.
     *
     * @return The hash code computed as the sum of i, j, k, and l.
     */
    public int hashCode() {
        return i + j + k + l;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of equality with another Tetrad2 instance.
     */
    public boolean equals(Object o) {
        if (!(o instanceof TetradInt(int i1, int j1, int k1, int l1))) return false;

        boolean leftEquals = (this.i == i1 && this.j == j1) ||
                             (this.i == j1 && this.j == i1);

        boolean rightEquals = (this.k == k1 && this.l == l1) ||
                              (this.k == l1 && this.l == k1);

        return leftEquals && rightEquals;
    }
}
