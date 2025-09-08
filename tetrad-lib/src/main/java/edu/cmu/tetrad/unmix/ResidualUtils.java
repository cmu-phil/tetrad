package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.stream.Collectors;

public final class ResidualUtils {

    /** Build an n x p residual matrix for all measured nodes in graph using given regressor. */
    public static double[][] residualMatrix(DataSet data, Graph g, ResidualRegressor reg) {
        List<Node> vars = data.getVariables();
        int n = data.getNumRows(), p = vars.size();
        double[][] R = new double[n][p];

        // parents from graph (fallback: empty)
        Map<Node, List<Node>> pa = new HashMap<>();
        for (Node v : vars) {
            List<Node> parents = g.getParents(v).stream()
                    .filter(vars::contains)
                    .collect(Collectors.toList());
            pa.put(v, parents);
        }

        for (int j = 0; j < p; j++) {
            Node v = vars.get(j);
            List<Node> parents = pa.get(v);
            if (parents == null) parents = Collections.emptyList();
            reg.fit(data, v, parents);
            double[] r = reg.residuals(data, v, parents);
            for (int i = 0; i < n; i++) R[i][j] = r[i];
        }
        return R;
    }

    /** Divide each column by robust scale (MAD/1.4826). Avoid zero-div by adding tiny epsilon. */
    public static void robustStandardizeInPlace(double[][] R) {
        int n = R.length, p = n == 0 ? 0 : R[0].length;
        for (int j = 0; j < p; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = R[i][j];
            double med = median(col);
            for (int i = 0; i < n; i++) col[i] = Math.abs(col[i] - med);
            double mad = median(col) / 0.67448975; // ~Phi^-1(0.75)
            double s = Math.max(mad, 1e-8);
            for (int i = 0; i < n; i++) R[i][j] /= s;
        }
    }

    private static double median(double[] a) {
        double[] b = a.clone();
        Arrays.sort(b);
        int m = b.length >>> 1;
        return (b.length % 2 == 0) ? 0.5 * (b[m-1] + b[m]) : b[m];
    }
}