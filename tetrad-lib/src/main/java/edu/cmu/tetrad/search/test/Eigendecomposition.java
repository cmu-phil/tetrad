package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * The Eigendecomposition class represents the decomposition of a square matrix into its eigenvalues and eigenvectors.
 * It provides methods to retrieve the eigenvalues, eigenvectors, and the top eigenvalues.
 */
public class Eigendecomposition {
    private final Matrix k;

    /**
     * Construct a new Eigendecomposition object with the given matrix.
     *
     * @param k the matrix to be decomposed
     * @throws IllegalArgumentException if the matrix is empty
     */
    public Eigendecomposition(Matrix k) {
        if (k.getNumRows() == 0 || k.getNumColumns() == 0) {
            throw new IllegalArgumentException("Empty matrix to decompose. Please don't do that to me.");
        }

        this.k = k;
    }

    /**
     * Performs eigendecomposition on a given matrix and optionally stores the eigenvectors.
     *
     * @param storeV a flag indicating whether to store the eigenvectors
     * @return the Eigendecomposition object on which this method is invoked
     */
    public Kci.EigenReturn invoke(boolean storeV, double threshold) {
        List<Double> topEigenValues;
        Matrix D = null;
        Matrix V = null;
        List<Double> topEigenvalues = new ArrayList<>();

        SingularValueDecomposition svd = new SingularValueDecomposition(k.getApacheData());

        double[] _singularValues = svd.getSingularValues();

        // Convert to list, taking only the singular values greater than the threshold.
        topEigenValues = new ArrayList<>();
        for (double _singularValue : _singularValues) {
            if (_singularValue > threshold * _singularValues[0]) {
                topEigenValues.add(sqrt(_singularValue));
            }
        }

        if (storeV) {
            D = new Matrix(topEigenValues.size(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                D.set(i, i, topEigenValues.get(i));
            }

            RealMatrix V0 = svd.getV();

            V = new Matrix(V0.getRowDimension(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                double[] t = V0.getColumn(i);
                V.assignColumn(i, new Vector(t));
            }
        }

        return new Kci.EigenReturn(D, V, topEigenvalues);
    }
}
