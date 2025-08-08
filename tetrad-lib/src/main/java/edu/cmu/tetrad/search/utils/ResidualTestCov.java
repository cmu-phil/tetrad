package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

public class ResidualTestCov {

    public static boolean dagExplainsCluster(SimpleMatrix covMatrix, Set<Integer> cluster, double rankToleranceFactor) {
        int[] indices = new int[cluster.size()];
        List<Integer> clusterIndices = new ArrayList<>(cluster);
        for (int i = 0; i < cluster.size(); i++) {
            indices[i] = clusterIndices.get(i);
        }

        covMatrix = StatUtils.extractSubMatrix(covMatrix, indices, indices);
        covMatrix = covMatrix.plus(SimpleMatrix.identity(covMatrix.getNumRows()).scale(0.1));
        int p = covMatrix.getNumRows();
        List<int[]> orderings = allOrderings(p);

        for (int[] ordering : orderings) {
            SimpleMatrix residualCov = new SimpleMatrix(p, p); // initialize to zero

            for (int i = 0; i < p; i++) {
                int y = ordering[i];

                if (i == 0) {
                    // No predictors, variance is Var(Y)
                    residualCov.set(y, y, covMatrix.get(y, y));
                    continue;
                }

                int[] predictors = Arrays.copyOf(ordering, i);

                // Extract submatrices
                SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(covMatrix, predictors, predictors);
                SimpleMatrix Sigma_XY = StatUtils.extractSubMatrix(covMatrix, predictors, new int[]{y});
                SimpleMatrix Sigma_YX = Sigma_XY.transpose();
                double Sigma_YY = covMatrix.get(y, y);

                // Residual variance: Var(Y | X) = Var(Y) - Cov(Y,X) Cov(X,X)^{-1} Cov(X,Y)
                SimpleMatrix beta = Sigma_YX.mult(Sigma_XX.pseudoInverse());
                SimpleMatrix projection = beta.mult(Sigma_XY);

                double residualVar = Sigma_YY - projection.get(0, 0);

                // Sanity check
                if (residualVar < 0) residualVar = 0;

                residualCov.set(y, y, residualVar);
            }

            // Check rank
            int rank = estimateRank(residualCov, rankToleranceFactor);
            if (rank == p) {
                return true; // residuals are full rank; no latent needed
            }
        }

        return false; // all residual matrices deficient → likely latent structure
    }

    private static int estimateRank(SimpleMatrix matrix, double rankToleranceFactor) {
        SimpleSVD<SimpleMatrix> svd = matrix.svd();
        double[] singularValues = svd.getSingularValues();

        double tol = singularValues[0] * matrix.getNumCols() * rankToleranceFactor;
        int rank = 0;
        for (double s : singularValues) {
            if (s > tol) rank++;
        }

        return rank;
    }

    private static List<int[]> allOrderings(int p) {
        List<int[]> orderings = new ArrayList<>();
        permute(orderings, new int[p], new boolean[p], 0);
        return orderings;
    }

    private static void permute(List<int[]> result, int[] current, boolean[] used, int depth) {
        int p = current.length;
        if (depth == p) {
            result.add(Arrays.copyOf(current, p));
            return;
        }

        for (int i = 0; i < p; i++) {
            if (!used[i]) {
                used[i] = true;
                current[depth] = i;
                permute(result, current, used, depth + 1);
                used[i] = false;
            }
        }
    }
}
