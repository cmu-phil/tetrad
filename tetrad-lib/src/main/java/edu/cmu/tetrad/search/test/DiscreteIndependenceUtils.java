package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.*;

/**
 * Utility methods for discrete conditional independence testing.
 */
public class DiscreteIndependenceUtils {

    private DiscreteIndependenceUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Returns true if x, y, and all nodes in z are discrete variables.
     */
    public static boolean isAllDiscrete(Node x, Node y, Set<Node> z) {
        if (!(x instanceof DiscreteVariable)) return false;
        if (!(y instanceof DiscreteVariable)) return false;
        if (z != null) {
            for (Node zn : z) {
                if (!(zn instanceof DiscreteVariable)) return false;
            }
        }
        return true;
    }

    /**
     * Conditional chi-square test for X _||_ Y | Z, stratifying on Z value tuples.
     * Iterates over rows once, grouping into contingency tables by Z stratum.
     * Respects an optional row subset via the rows parameter (null = all rows).
     */
    public static IndependenceResult conditionalChiSquare(
            DataSet dataSet,
            List<Node> variables,
            List<Integer> rows,
            Node x, Node y, List<Node> z,
            IndependenceFact fact,
            double alpha) {

        int xi = variables.indexOf(x);
        int yi = variables.indexOf(y);

        int kx = ((DiscreteVariable) x).getNumCategories();
        int ky = ((DiscreteVariable) y).getNumCategories();

        Map<List<Integer>, int[][]> tables = new HashMap<>();

        int numRows = (rows == null) ? dataSet.getNumRows() : rows.size();

        for (int r = 0; r < numRows; r++) {
            int row = (rows == null) ? r : rows.get(r);

            List<Integer> key = new ArrayList<>(z.size());
            boolean missing = false;

            for (Node zn : z) {
                int val = dataSet.getInt(row, variables.indexOf(zn));
                if (val == DiscreteVariable.MISSING_VALUE) { missing = true; break; }
                key.add(val);
            }

            int xval = dataSet.getInt(row, xi);
            int yval = dataSet.getInt(row, yi);

            if (missing
                    || xval == DiscreteVariable.MISSING_VALUE
                    || yval == DiscreteVariable.MISSING_VALUE) continue;

            tables.computeIfAbsent(key, k -> new int[kx][ky])[xval][yval]++;
        }

        double chiSq = 0.0;
        double df = 0.0;

        for (int[][] table : tables.values()) {
            int[] rowSums = new int[kx];
            int[] colSums = new int[ky];
            int total = 0;

            for (int a = 0; a < kx; a++)
                for (int b = 0; b < ky; b++) {
                    rowSums[a] += table[a][b];
                    colSums[b] += table[a][b];
                    total += table[a][b];
                }

            if (total == 0) continue;

            for (int a = 0; a < kx; a++) {
                for (int b = 0; b < ky; b++) {
                    double expected = (double) rowSums[a] * colSums[b] / total;
                    if (expected > 0) {
                        double diff = table[a][b] - expected;
                        chiSq += diff * diff / expected;
                    }
                }
            }

            long nonzeroRows = Arrays.stream(rowSums).filter(s -> s > 0).count();
            long nonzeroCols = Arrays.stream(colSums).filter(s -> s > 0).count();
            df += (nonzeroRows - 1) * (nonzeroCols - 1);
        }

        if (df < 1) {
            return new IndependenceResult(fact, true, Double.NaN, Double.NaN);
        }

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p = 1.0 - chi2.cumulativeProbability(chiSq);
        p = TMath.max(0.0, TMath.min(1.0, p));

        boolean indep = p > alpha;
        return new IndependenceResult(fact, indep, p, alpha - p);
    }
}