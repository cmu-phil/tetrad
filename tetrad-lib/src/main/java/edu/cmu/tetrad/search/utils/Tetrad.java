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
public class Tetrad implements TetradSerializable {
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
     * Constructor.
     *
     * @param i a int
     * @param j a int
     * @param k a int
     * @param l a int
     */
    public Tetrad(int i, int j, int k, int l) {
        testDistinctness(i, j, k, l);
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link Tetrad} object
     */
    public static Tetrad serializableInstance() {
        return new Tetrad(0, 1, 2, 3);
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
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hash = this.i * this.j;
        hash += this.k * this.l;
        return hash;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of equality with another Sextad instance.
     */
    public boolean equals(Object o) {
        if (!(o instanceof Tetrad sextad)) return false;

        boolean leftEquals = (this.i == sextad.i && this.j == sextad.j)
                             || (this.i == sextad.j && this.j == sextad.i);

        boolean rightEquals = (this.k == sextad.k && this.l == sextad.k)
                             || (this.k == sextad.l && this.l == sextad.j);

        return leftEquals && rightEquals;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link String} object
     */
    public String toString() {
        return "<" + this.i + ", " + this.j + ", " + this.k + "; " + this.l + ">";
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

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}
