package edu.cmu.tetrad.search.test;

import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * The class is used to find the top eigenvalues and eigenvectors of a given matrix. The top eigenvalues are those that
 * are greater than a given threshold times the largest eigenvalue.
 *
 * @author josephramsey
 */
public class TopEigenvalues {
    private final SimpleMatrix k;

    /**
     * Construct a new object with the given matrix.
     *
     * @param k the matrix to be decomposed
     * @throws IllegalArgumentException if the matrix is empty
     */
    public TopEigenvalues(SimpleMatrix k) {
        if (k.getNumRows() == 0 || k.getNumCols() == 0) {
            throw new IllegalArgumentException("Empty matrix to decompose. Please don't do that to me.");
        }

        this.k = k;
    }

    /**
     * Perform eigenvalue decomposition on the given matrix and return the top eigenvalues and eigenvectors.
     *
     * @param data      the matrix to be decomposed
     * @param threshold the threshold for the eigenvalues
     * @return the top eigenvalues and eigenvectors
     */
    private static @NotNull EigResult getTopEigen(SimpleMatrix data, double threshold) {
        // Perform eigenvalue decomposition
        SimpleMatrix matrix = new SimpleMatrix(data);
        SimpleEVD<SimpleMatrix> evd = matrix.eig();
        List<Double> realEigen = new ArrayList<>();

        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            realEigen.add(evd.getEigenvalue(i).getReal());
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            if (evd.getEigenvalue(i).getReal() > threshold * evd.getEigenvalue(0).getReal()) {
                indices.add(i);
            }
        }

        // Make a new list of eigenvalues and eigenvectors based on the sorted indices
        List<Double> realEigen2 = new ArrayList<>();
        List<SimpleMatrix> eigenVectors2 = new ArrayList<>();
        for (Integer index : indices) {
            realEigen2.add(realEigen.get(index));
            eigenVectors2.add(evd.getEigenVector(index));
        }

        return new EigResult(realEigen2, eigenVectors2);
    }

    /**
     * Performs eigendecomposition on a given matrix and optionally stores the top eigenvalues and (optionaly)
     * eigenvectors.
     *
     * @param storeV a flag indicating whether to store the eigenvectors
     * @return the Eigendecomposition object on which this method is invoked
     */
    public Kci.EigenReturn invoke(boolean storeV, double threshold) {

        // Create a SimpleMatrix from the array
        EigResult result = getTopEigen(k, threshold);

        SimpleMatrix D = null;
        SimpleMatrix V = null;
        List<Double> topEigenValues = result.realEigen();
        List<SimpleMatrix> topEigenVectors = result.eigenVectors();

        if (storeV) {
            D = new SimpleMatrix(topEigenValues.size(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                D.set(i, i, sqrt(topEigenValues.get(i)));
            }

            V = new SimpleMatrix(topEigenVectors.getFirst().getNumRows(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                SimpleMatrix ev = topEigenVectors.get(i);
                V.setColumn(i, ev);
            }
        }

        return new Kci.EigenReturn(D, V, topEigenValues);
    }

    /**
     * The class is used to store the top eigenvalues and eigenvectors.
     *
     * @param realEigen    the top eigenvalues
     * @param eigenVectors the top eigenvectors
     */
    private record EigResult(List<Double> realEigen, List<SimpleMatrix> eigenVectors) {
    }
}
