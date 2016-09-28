package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.ArrayList;
import java.util.List;

public class IntSextad implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private int i;
    private int j;
    private int k;
    private int l;
    private int m;
    private int n;

    public IntSextad(int i, int j, int k, int l, int m, int n) {
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

    public int getI() {
        return i;
    }

    public int getJ() {
        return j;
    }

    public int getK() {
        return k;
    }

    public int getL() { return l; }

    public int getM() {
        return m;
    }

    public int getN() {
        return n;
    }

    public int hashCode() {
        int hash = i * j * k;
        hash += l * m * n;
        return hash;
    }

    public boolean equals(Object o) {
        IntSextad sextad = (IntSextad) o;

        boolean leftEquals = i == sextad.i && j == sextad.j && k == sextad.k ||
                i == sextad.i && j == sextad.k && k == sextad.j ||
                i == sextad.j && j == sextad.i && k == sextad.k ||
                i == sextad.j && j == sextad.k && k == sextad.i ||
                i == sextad.k && j == sextad.i && k == sextad.j ||
                i == sextad.k && j == sextad.j && k == sextad.i;

        boolean rightEquals = l == sextad.l && m == sextad.m && n == sextad.n ||
                l == sextad.l && m == sextad.n && n == sextad.m ||
                l == sextad.m && m == sextad.l && n == sextad.n ||
                l == sextad.m && m == sextad.n && n == sextad.l ||
                l == sextad.n && m == sextad.l && n == sextad.m ||
                l == sextad.n && m == sextad.m && n == sextad.l;

        return leftEquals && rightEquals;
    }

    public String toString() {
        return "<" + i + ", " + j + ", " + k + "; " + l + ", " + m + ", " + n + ">";
    }

    public List<Integer> getNodes() {
        List<Integer> nodes = new ArrayList<>();
        nodes.add(i);
        nodes.add(j);
        nodes.add(k);
        nodes.add(l);
        nodes.add(m);
        nodes.add(n);
        return nodes;
    }
}
