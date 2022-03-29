package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.List;

public class IntSextad implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final int i;
    private final int j;
    private final int k;
    private final int l;
    private final int m;
    private final int n;

    public IntSextad(final int i, final int j, final int k, final int l, final int m, final int n) {
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
     */
    public static IntSextad serializableInstance() {
        return new IntSextad(0, 1, 2, 3, 4, 5);
    }


    private void testDistinctness(final int i, final int j, final int k, final int l, final int m, final int n) {
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

    public int getI() {
        return this.i;
    }

    public int getJ() {
        return this.j;
    }

    public int getK() {
        return this.k;
    }

    public int getL() {
        return this.l;
    }

    public int getM() {
        return this.m;
    }

    public int getN() {
        return this.n;
    }

    public int hashCode() {
        int hash = this.i * this.j * this.k;
        hash += this.l * this.m * this.n;
        return hash;
    }

    public boolean equals(final Object o) {
        final IntSextad sextad = (IntSextad) o;

        final boolean leftEquals = this.i == sextad.i && this.j == sextad.j && this.k == sextad.k ||
                this.i == sextad.i && this.j == sextad.k && this.k == sextad.j ||
                this.i == sextad.j && this.j == sextad.i && this.k == sextad.k ||
                this.i == sextad.j && this.j == sextad.k && this.k == sextad.i ||
                this.i == sextad.k && this.j == sextad.i && this.k == sextad.j ||
                this.i == sextad.k && this.j == sextad.j && this.k == sextad.i;

        final boolean rightEquals = this.l == sextad.l && this.m == sextad.m && this.n == sextad.n ||
                this.l == sextad.l && this.m == sextad.n && this.n == sextad.m ||
                this.l == sextad.m && this.m == sextad.l && this.n == sextad.n ||
                this.l == sextad.m && this.m == sextad.n && this.n == sextad.l ||
                this.l == sextad.n && this.m == sextad.l && this.n == sextad.m ||
                this.l == sextad.n && this.m == sextad.m && this.n == sextad.l;

        return leftEquals && rightEquals;
    }

    public String toString() {
        return "<" + this.i + ", " + this.j + ", " + this.k + "; " + this.l + ", " + this.m + ", " + this.n + ">";
    }

    public List<Integer> getNodes() {
        final List<Integer> nodes = new ArrayList<>();
        nodes.add(this.i);
        nodes.add(this.j);
        nodes.add(this.k);
        nodes.add(this.l);
        nodes.add(this.m);
        nodes.add(this.n);
        return nodes;
    }
}
