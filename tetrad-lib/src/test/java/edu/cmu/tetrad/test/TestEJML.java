package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TestEJML {
    public static void main(String[] args) {
        Graph graph = RandomGraph.randomGraph(10, 2, 10, 100, 100, 100, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet _data = im.simulateData(1000, false);
        CovarianceMatrix cov = new CovarianceMatrix(_data);
        double[][] data = cov.getMatrix().toArray();

        double threshold = 0.5;
        EigResult result = getTopEigen(data, threshold);

        // Get eigenvalues and eigenvectors
        System.out.println("Eigenvalues:");
        for (Double aDouble : result.realEigen2()) {
            System.out.println(aDouble); // Real part of the eigenvalue
        }

        System.out.println("Eigenvectors:");
        for (SimpleMatrix simpleMatrix : result.eigenVectors2()) {
            System.out.println(simpleMatrix); // Eigenvector as a SimpleMatrix
        }

        SingularValueDecomposition svd = new SingularValueDecomposition(new BlockRealMatrix(data));

        double[] _singularValues = svd.getSingularValues();

        double[] _eigenValues = new double[_singularValues.length];
        System.arraycopy(_singularValues, 0, _eigenValues, 0, _singularValues.length);

        System.out.println("Eigenvalues2:");
        for (double eigenValue : _eigenValues) {
            System.out.println(eigenValue);
        }

        RealMatrix V0 = svd.getV();

        System.out.println("Eigenvectors2:");
        for (int i = 0; i < V0.getColumnDimension(); i++) {
            System.out.println(V0.getColumnVector(i));
        }
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


