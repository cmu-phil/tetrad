package edu.cmu.tetrad.data;

/**
 * Some comemon methods for the various covariance implementations.
 *
 * @author Joseph D. Ramsey
 * @version $Id: $Id
 */
public interface Covariances {
    /** Constant <code>serialVersionUID=23L</code> */
    long serialVersionUID = 23L;

    /**
     * Returns the covariance at (i, j).
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    double covariance(int i, int j);

    /**
     * Returns the dimensiom of the matrix.
     *
     * @return a int
     */
    int size();

    /**
     * Sets the covariance at (i, j) to a particular value. Not effective for implemetations that calculate covariances
     * from data on the fly.
     *
     * @param i a int
     * @param j a int
     * @param v a double
     */
    void setCovariance(int i, int j, double v);

    /**
     * Returns the underlying covariance matrix.
     *
     * @return an array of {@link double} objects
     */
    double[][] getMatrix();

    /**
     * Returns a submatrix of the covariance matrix for the given rows and columns.
     *
     * @param rows an array of {@link int} objects
     * @param cols an array of {@link int} objects
     * @return an array of {@link double} objects
     */
    double[][] getSubMatrix(int[] rows, int[] cols);
}
