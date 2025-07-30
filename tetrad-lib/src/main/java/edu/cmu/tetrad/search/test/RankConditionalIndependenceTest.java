package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RankConditionalIndependenceTest implements IndependenceTest {

    private final ICovarianceMatrix covMatrix;
    private final List<Node> variables;
    private double threshold = 0.001;
    private final SimpleMatrix S;
    private boolean verbose = false;

    public RankConditionalIndependenceTest(ICovarianceMatrix covMatrix, double threshold) {
        this.covMatrix = covMatrix;
        this.variables = covMatrix.getVariables();
        this.S = covMatrix.getMatrix().getDataCopy();
        this.threshold = threshold;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(x);
        nodes.add(y);
        nodes.addAll(z);

        int p = variables.indexOf(x);
        int q = variables.indexOf(y);
        int[] zIndices = z.stream().mapToInt(variables::indexOf).toArray();

        if (zIndices.length == 0) {
            return new IndependenceResult(new IndependenceFact(x, y, z),
                    S.get(p, q) < threshold, 1.0, threshold - 1.0);
        }

        int n = covMatrix.getSampleSize();
        int[] allIndices = nodes.stream().mapToInt(variables::indexOf).toArray();

        // Partition S into appropriate submatrices
        // Build Sxz (1 x |Z|)
        SimpleMatrix Sxz = new SimpleMatrix(1, zIndices.length);
        for (int i = 0; i < zIndices.length; i++) {
            Sxz.set(0, i, S.get(p, zIndices[i]));
        }

        // Build Syz (1 x |Z|)
        SimpleMatrix Syz = new SimpleMatrix(1, zIndices.length);
        for (int i = 0; i < zIndices.length; i++) {
            Syz.set(0, i, S.get(q, zIndices[i]));
        }

        // Build Szz (|Z| x |Z|)
        SimpleMatrix Szz = new SimpleMatrix(zIndices.length, zIndices.length);
        for (int i = 0; i < zIndices.length; i++) {
            for (int j = 0; j < zIndices.length; j++) {
                Szz.set(i, j, S.get(zIndices[i], zIndices[j]));
            }
        }

        SimpleMatrix Sxy = S.extractMatrix(p, p + 1, q, q + 1);

        if (Szz.getNumRows() != Szz.getNumCols()) {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, Double.NaN);
        }

        SimpleMatrix SzzInv;
        try {
            SzzInv = Szz.pseudoInverse();
        } catch (Exception e) {
            return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, Double.NaN);
        }

        // Conditional covariance
        SimpleMatrix SigmaXYgivenZ = Sxy.minus(Sxz.mult(SzzInv).mult(Syz.transpose()));

        // Test if this conditional covariance is (approximately) zero
        double stat = SigmaXYgivenZ.normF();
        double threshold = this.threshold; // tweakable
        boolean independent = stat < threshold;

        if (verbose) {
            System.out.println("Conditional covariance norm: " + stat);
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, 1.0, Double.NaN);
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataModel getData() {
        return covMatrix;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String toString() {
        return "Rank-based Conditional Independence Test (using EJML)";
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return covMatrix;
    }
}
