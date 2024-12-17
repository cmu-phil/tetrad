package edu.cmu.tetrad.search.test;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
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
    EigenDecomposition_F64<DMatrixRMaj> eigDecomp;

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
        DMatrixRMaj M = data.getMatrix();

        EigenDecomposition_F64<DMatrixRMaj> eigDecomp =
                DecompositionFactory_DDRM.eig(M.numRows, true);

        if (!eigDecomp.decompose(M)) {
            throw new RuntimeException("Eigenvalue decomposition failed");
        }

        // Make a new list of eigenvalues and eigenvectors based on the sorted indices
        List<Double> realEigen = new ArrayList<>();
        List<DMatrixRMaj> eigenVectors = new ArrayList<>();
        for (int i = 0; i < eigDecomp.getNumberOfEigenvalues(); i++) {
            if (eigDecomp.getEigenvalue(i).getReal() < threshold * eigDecomp.getEigenvalue(0).getReal()) {
                break;
            }

            realEigen.add(eigDecomp.getEigenvalue(i).getReal());
            eigenVectors.add(eigDecomp.getEigenVector(i));
        }

        return new EigResult(realEigen, eigenVectors);
    }

    /**
     * Performs eigendecomposition on a given matrix and optionally stores the top eigenvalues and (optionaly)
     * eigenvectors.
     *
     * @param storeV    a flag indicating whether to store the eigenvectors]
     * @param threshold the threshold for the eigenvalues
     * @return the Eigendecomposition object on which this method is invoked
     */
    public Kci.EigenReturn invoke(boolean storeV, double threshold) {

        // Create a SimpleMatrix from the array
        EigResult result = getTopEigen(k, threshold);

        SimpleMatrix D = null;
        SimpleMatrix V = null;
        List<Double> topEigenValues = result.realEigen();
        List<DMatrixRMaj> topEigenVectors = result.eigenVectors();

        if (storeV) {
            D = new SimpleMatrix(topEigenValues.size(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                D.set(i, i, sqrt(topEigenValues.get(i)));
            }

            V = new SimpleMatrix(topEigenVectors.getFirst().getNumRows(), topEigenValues.size());

            for (int i = 0; i < topEigenValues.size(); i++) {
                DMatrixRMaj ev = topEigenVectors.get(i);
                V.setColumn(i, new SimpleMatrix(ev).transpose());
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
    private record EigResult(List<Double> realEigen, List<DMatrixRMaj> eigenVectors) {
    }
}
