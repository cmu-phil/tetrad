package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utilities for computing residual matrices given a regressor and either (a) a graph with parent sets, or (b) an
 * explicit parent map. Also includes robust column scaling.
 */
public final class ResidualUtils {

    private ResidualUtils() {
    }

    /**
     * Build an n × p residual matrix for all measured nodes in the dataset, using parents from the given graph.
     */
    public static double[][] residualMatrix(DataSet data,
                                            Graph g,
                                            ResidualRegressor reg) {
        List<Node> vars = data.getVariables();
        int n = data.getNumRows(), p = vars.size();
        double[][] R = new double[n][p];

        for (int j = 0; j < p; j++) {
            Node v = vars.get(j);
            List<Node> parents = g.getParents(v);
            reg.fit(data, v, parents);
            double[] r = reg.residuals(data, v, parents);
            for (int i = 0; i < n; i++) R[i][j] = r[i];
        }
        return R;
    }

    /**
     * Build an n × p residual matrix using a custom parent map (e.g., from ParentSupersetBuilder).
     */
    public static double[][] residualMatrix(DataSet data,
                                            Map<Node, List<Node>> parentsMap,
                                            ResidualRegressor reg) {
        List<Node> vars = data.getVariables();
        int n = data.getNumRows(), p = vars.size();
        double[][] R = new double[n][p];

        for (int j = 0; j < p; j++) {
            Node v = vars.get(j);
            List<Node> pa = parentsMap.getOrDefault(v, Collections.emptyList());
            reg.fit(data, v, pa);
            double[] r = reg.residuals(data, v, pa);
            for (int i = 0; i < n; i++) R[i][j] = r[i];
        }
        return R;
    }

    /**
     * Divide each column by robust scale (MAD/0.6745). Avoids zero-divide by adding epsilon.
     */
    public static void robustStandardizeInPlace(double[][] R) {
        if (R.length == 0) return;
        int n = R.length, p = R[0].length;
        for (int j = 0; j < p; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = R[i][j];
            double med = median(col);
            for (int i = 0; i < n; i++) col[i] = Math.abs(col[i] - med);
            double mad = median(col) / 0.67448975; // Phi^-1(0.75)
            double s = Math.max(mad, 1e-8);
            for (int i = 0; i < n; i++) R[i][j] /= s;
        }
    }

    private static double median(double[] a) {
        double[] b = a.clone();
        Arrays.sort(b);
        int m = b.length >>> 1;
        return (b.length % 2 == 0) ? 0.5 * (b[m - 1] + b[m]) : b[m];
    }
}