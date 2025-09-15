package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Implements an ordered pair of objects (a, b) suitable for storing in HashSets.  The hashCode() method is overridden
 * so that the hashcode of (a1, b1) == the hashcode of (a2, b2) just in case a1 == a2 and b1 == b2.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ParameterPair implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    /**
     * The first element of the ordered pair.  Can be null.
     *
     * @serial May be null.
     */
    private Parameter a;

    /**
     * The second element of the ordered pair.  Can be null.
     *
     * @serial May be null.
     */
    private Parameter b;


    /**
     * Constructs a new (blank) ordered pair where a = null and b = null.
     */
    private ParameterPair() {
        this.a = null;
        this.b = null;
    }

    /**
     * Constructs a new ordered pair (a, b).
     *
     * @param a the first element of the ordered pair.
     * @param b the second element of the ordered pair.
     */
    public ParameterPair(Parameter a, Parameter b) {
        setPair(a, b);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.ParameterPair} object
     */
    public static ParameterPair serializableInstance() {
        return new ParameterPair();
    }

    /**
     * Method getNumObjects
     *
     * @return the first element of the ordered pair.
     */
    public Parameter getA() {
        return this.a;
    }

    /**
     * Method getNumObjects
     *
     * @return the second element of the ordered pair.
     */
    public Parameter getB() {
        return this.b;
    }

    /**
     * Checks if this ParameterPair object is equal to the specified object.
     *
     * @param object the object to compare to this ParameterPair
     * @return {@code true} if the specified object is equal to this ParameterPair, {@code false} otherwise
     */
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof ParameterPair pair)) {
            return false;
        }

        return this.a.equals(pair.a) && this.b.equals(pair.b);
    }

    /**
     * <p>hashCode.</p>
     *
     * @return this hashcode.
     */
    public int hashCode() {
        int hashCode = 31 + ((this.a == null) ? 0 : this.a.hashCode());

        return 31 * hashCode + ((this.b == null) ? 0 : this.b.hashCode());
    }

    /**
     * Sets the elements of this ordered pair to a new pair (a, b).
     *
     * @param a the new first element.
     * @param b the new seconde element.
     */
    private void setPair(Parameter a, Parameter b) {
        this.a = a;
        this.b = b;
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
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
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





