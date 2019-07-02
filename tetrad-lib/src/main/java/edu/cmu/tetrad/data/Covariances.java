package edu.cmu.tetrad.data;

/**
 * Some comemon methods for the various covariance implementations.
 *
 * @author Joseph D. Ramsey
 */
public interface Covariances {
    static final long serialVersionUID = 23L;

    /**
     * Returns the covariance at (i, j).
     */
    double covariance(int i, int j);

    /**
     * Returns the dimensiom of the matrix.
     */
    int size();

    /**
     * Sets the covariance at (i, j) to a particular value. Not effective for implemetations that calculate
     * covariances from data on the fly.
     */
    void setCovariance(int i, int j, double v);

    /**
     * Returns the underlying covariance matrix.
     */
    double[][] getMatrix();

    /**
     * Returns a submatrix of the covariance matrix for the given rows and columns.
     */
    double[][] getSubMatrix(int[] rows, int[] cols);
}
