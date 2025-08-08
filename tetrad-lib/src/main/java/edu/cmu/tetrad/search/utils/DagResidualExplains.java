package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

public class DagResidualExplains {

    public static boolean isExplainedByDag(Graph dag, CovarianceMatrix covMatrix, double rankToleranceFactor) {
        List<Node> vars = covMatrix.getVariables();
        SimpleMatrix S = covMatrix.getMatrix().getSimpleMatrix();
        int p = vars.size();
        Map<Node, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < p; i++) indexMap.put(vars.get(i), i);

        SimpleMatrix residualCov = new SimpleMatrix(p, p);

        for (int i = 0; i < p; i++) {
            Node y = vars.get(i);
            List<Node> parents = dag.getParents(y);

            if (parents.isEmpty()) {
                // No predictors — just take variance
                residualCov.set(i, i, covMatrix.getValue(i, i));
                continue;
            }

            int[] parentIndices = parents.stream().mapToInt(indexMap::get).toArray();

            SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(S, parentIndices, parentIndices);
            SimpleMatrix Sigma_XY = StatUtils.extractSubMatrix(S, parentIndices, new int[]{i});
            SimpleMatrix Sigma_YX = Sigma_XY.transpose();
            double Sigma_YY = covMatrix.getValue(i, i);

            // Var(Y | X) = Var(Y) - Cov(Y,X) Cov(X,X)^-1 Cov(X,Y)
            SimpleMatrix beta = Sigma_YX.mult(Sigma_XX.pseudoInverse());
            SimpleMatrix projection = beta.mult(Sigma_XY);
            double residualVar = Sigma_YY - projection.get(0, 0);

            // Sanity
            if (residualVar < 0) residualVar = 0;

            residualCov.set(i, i, residualVar);
        }

//        // RCCA rank test: is residualCov full rank?
//        int[] all = new int[p];
//        for (int i = 0; i < p; i++) all[i] = i;
//
//        double alpha = 0.001;
//        double regParam = 0.01;
//
//        double pValue = RankTests.getRccaPValueRankLE(residualCov, all, new int[0], covMatrix.getSampleSize(), p - 1, regParam);
//
//        System.out.println("Explained by data p-value = " + pValue);
//
//        return pValue <= alpha; // Reject H0: rank ≤ p - 1 → likely full rank → DAG OK

        System.out.println("residualCov: " + residualCov.diag());

        int rank = estimateRank(residualCov, rankToleranceFactor);

        return rank == p;
    }

    private static int estimateRank(SimpleMatrix matrix, double tolFactor) {
        SimpleSVD<SimpleMatrix> svd = matrix.svd();
        double[] singularValues = svd.getSingularValues();
        double tol = singularValues[0] * matrix.getNumCols() * tolFactor;

        int rank = 0;
        for (double s : singularValues) {
            if (s > tol) rank++;
        }
        return rank;
    }
}
