package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * <p>Maps a parameter to the matrix element where its value is stored in the
 * model.
 *
 * @author Frank Wimberly
 * @author Joe Ramsey
 * @version $Id: $Id
 */
public class Mapping implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The SemIm for which this is a mapping.
     *
     * @serial Can't be null.
     */
    private final ISemIm semIm;

    /**
     * The parameter this mapping maps.
     *
     * @serial Can't be null.
     */
    private final Parameter parameter;

    /**
     * The 2D double array whose element at (i, j) to be manipulated.
     *
     * @serial Can't be null.
     */
    private final Matrix a;

    /**
     * The left-hand coordinate of a[i][j].
     *
     * @serial Any value.
     */
    private final int i;

    /**
     * The right-hand coordinate of a[i][j].
     *
     * @serial Any value.
     */
    private final int j;

    /**
     * Constructs matrix new mapping using the given freeParameters.
     *
     * @param parameter The parameter that this maps.
     * @param matrix    The array containing matrix[i][j], the element to be manipulated.
     * @param i         Left coordinates of matrix[i][j].
     * @param j         Right coordinate of matrix[i][j].
     * @param semIm     a {@link edu.cmu.tetrad.sem.ISemIm} object
     */
    public Mapping(ISemIm semIm, Parameter parameter, Matrix matrix,
                   int i, int j) {
        if (semIm == null) {
            throw new NullPointerException("SemIm must not be null.");
        }

        if (parameter == null) {
            throw new NullPointerException("Parameter must not be null.");
        }

        if (matrix == null) {
            throw new NullPointerException("Supplied array must not be null.");
        }

        if (i < 0 || j < 0) {
            throw new IllegalArgumentException("Indices must be non-negative");
        }

        this.semIm = semIm;
        this.parameter = parameter;
        this.a = matrix;
        this.i = i;
        this.j = j;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.Mapping} object
     */
    public static Mapping serializableInstance() {
        return new Mapping(SemIm.serializableInstance(),
                Parameter.serializableInstance(), new Matrix(0, 0),
                1, 1);
    }

    /**
     * <p>getValue.</p>
     *
     * @return the value of the array element at (i, j).
     */
    public double getValue() {
        return this.a.get(this.i, this.j);
    }

    /**
     * Sets the value of the array element at the stored coordinates (i, j). If the array is symmetric sets two
     * elements.
     *
     * @param x a double
     */
    public void setValue(double x) {
        if (this.semIm.isParameterBoundsEnforced() &&
            getParameter().getType() == ParamType.VAR && x < 0.0) {
            throw new IllegalArgumentException(
                    "Variances cannot " + "have values <= 0.0: " + x);
        }

        this.a.set(this.i, this.j, x);

        if (getParameter().getType() == ParamType.VAR ||
            getParameter().getType() == ParamType.COVAR) {
            this.a.set(this.j, this.i, x);
            this.a.set(this.i, this.j, x);
        }
    }

    /**
     * <p>Getter for the field <code>parameter</code>.</p>
     *
     * @return the paramter that this mapping maps.
     */
    public Parameter getParameter() {
        return this.parameter;
    }

    /**
     * <p>toString.</p>
     *
     * @return a String containing information (array name and values of subscripts) about the array element associated
     * with this mapping.
     */
    public String toString() {
        return "<" + getParameter().getName() + " " + getParameter().getType() +
               "[" + this.i + "][" + this.j + "]>";
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





