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
 * @author josephramsey
 * @version $Id: $Id
 */
public class Sextad implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The first node.
     */
    private final int i;

    /**
     * The second node.
     */
    private final int j;

    /**
     * The third node.
     */
    private final int k;

    /**
     * The fourth node.
     */
    private final int l;

    /**
     * The fifth node.
     */
    private final int m;

    /**
     * The sixth node.
     */
    private final int n;

    /**
     * Constructor.
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param l a int
     * @param m a int
     * @param n a int
     */
    public Sextad(int i, int j, int k, int l, int m, int n) {
        testDistinctness(i, j, k, l, m, n);
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.m = m;
        this.n = n;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.search.utils.Sextad} object
     */
    public static Sextad serializableInstance() {
        return new Sextad(0, 1, 2, 3, 4, 5);
    }

    /**
     * <p>Getter for the field <code>i</code>.</p>
     *
     * @return a int
     */
    public int getI() {
        return this.i;
    }

    /**
     * <p>Getter for the field <code>j</code>.</p>
     *
     * @return a int
     */
    public int getJ() {
        return this.j;
    }

    /**
     * <p>Getter for the field <code>k</code>.</p>
     *
     * @return a int
     */
    public int getK() {
        return this.k;
    }

    /**
     * <p>Getter for the field <code>l</code>.</p>
     *
     * @return a int
     */
    public int getL() {
        return this.l;
    }

    /**
     * <p>Getter for the field <code>m</code>.</p>
     *
     * @return a int
     */
    public int getM() {
        return this.m;
    }

    /**
     * <p>Getter for the field <code>n</code>.</p>
     *
     * @return a int
     */
    public int getN() {
        return this.n;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hash = this.i * this.j * this.k;
        hash += this.l * this.m * this.n;
        return hash;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of equality with another Sextad instance.
     */
    public boolean equals(Object o) {
        if (!(o instanceof Sextad sextad)) return false;

        boolean leftEquals = this.i == sextad.i && this.j == sextad.j && this.k == sextad.k ||
                             this.i == sextad.i && this.j == sextad.k && this.k == sextad.j ||
                             this.i == sextad.j && this.j == sextad.i && this.k == sextad.k ||
                             this.i == sextad.j && this.j == sextad.k && this.k == sextad.i ||
                             this.i == sextad.k && this.j == sextad.i && this.k == sextad.j ||
                             this.i == sextad.k && this.j == sextad.j && this.k == sextad.i;

        boolean rightEquals = this.l == sextad.l && this.m == sextad.m && this.n == sextad.n ||
                              this.l == sextad.l && this.m == sextad.n && this.n == sextad.m ||
                              this.l == sextad.m && this.m == sextad.l && this.n == sextad.n ||
                              this.l == sextad.m && this.m == sextad.n && this.n == sextad.l ||
                              this.l == sextad.n && this.m == sextad.l && this.n == sextad.m ||
                              this.l == sextad.n && this.m == sextad.m && this.n == sextad.l;

        return leftEquals && rightEquals;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "<" + this.i + ", " + this.j + ", " + this.k + "; " + this.l + ", " + this.m + ", " + this.n + ">";
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
        nodes.add(this.m);
        nodes.add(this.n);
        return nodes;
    }

    private void testDistinctness(int i, int j, int k, int l, int m, int n) {
        if (i == j || i == k || i == l || i == m || i == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (j == k || j == l || j == m || j == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (k == l || k == m || k == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (l == m || l == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }

        if (m == n) {
            throw new IllegalArgumentException("Nodes not distinct.");
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}
