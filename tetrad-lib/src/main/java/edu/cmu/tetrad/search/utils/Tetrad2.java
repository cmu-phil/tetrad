package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ordered sextad of nodes.
 *
 * @param i The first node.
 * @param j The second node.
 * @param k The third node.
 * @param l The fourth node.
 * @author josephramsey
 * @version $Id: $Id
 */
public record Tetrad2(int i, int j, int k, int l) implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructor.
     *
     * @param i an int
     * @param j an int
     * @param k an int
     * @param l an int
     */
    public Tetrad2 {
        testDistinctness(i, j, k, l);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link Tetrad2} object
     */
    public static Tetrad2 serializableInstance() {
        return new Tetrad2(0, 1, 2, 3);
    }

    /**
     * <p>Getter for the field <code>i</code>.</p>
     *
     * @return a int
     */
    @Override
    public int i() {
        return this.i;
    }

    /**
     * <p>Getter for the field <code>j</code>.</p>
     *
     * @return a int
     */
    @Override
    public int j() {
        return this.j;
    }

    /**
     * <p>Getter for the field <code>k</code>.</p>
     *
     * @return a int
     */
    @Override
    public int k() {
        return this.k;
    }

    /**
     * <p>Getter for the field <code>l</code>.</p>
     *
     * @return a int
     */
    @Override
    public int l() {
        return this.l;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of equality with another Tetrad2 instance.
     */
    public boolean equals(Object o) {
        if (!(o instanceof Tetrad2(int i1, int j1, int k1, int l1))) return false;

        boolean leftEquals = (this.i == i1 && this.j == j1) ||
                             (this.i == j1 && this.j == i1);

        boolean rightEquals = (this.k == k1 && this.l == l1) ||
                              (this.k == l1 && this.l == k1);

        return leftEquals && rightEquals;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link String} object
     */
    public String toString() {
        return "<" + this.i + ", " + this.j + ", " + this.k + "; " + this.l;
    }

    /**
     * Returns the list of nodes.
     *
     * @return This list.
     */
    public List<Integer> getNodes() {
        List<Integer> nodes = new ArrayList<>();
        nodes.add(this.i);
        nodes.add(this.j);
        nodes.add(this.k);
        nodes.add(this.l);
        return nodes;
    }

    private void testDistinctness(int i, int j, int k, int l) {
        if (i == j || i == k || i == l) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (j == k || j == l) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (k == l) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }
    }
}
