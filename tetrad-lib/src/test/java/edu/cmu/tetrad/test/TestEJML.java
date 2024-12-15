package edu.cmu.tetrad.test;

import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TestEJML {
    public static void main(String[] args) {
    }

    private static @NotNull TestEJML.EigResult getTopEigen(double[][] data, double threshold) {
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

    private record EigResult(List<Double> realEigen2, List<SimpleMatrix> eigenVectors2) {
    }


}


