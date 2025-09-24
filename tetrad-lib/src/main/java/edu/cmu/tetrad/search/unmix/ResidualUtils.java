///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
     * Constructs a residual matrix where each column corresponds to a variable in the dataset and contains
     * the residuals obtained after regressing that variable on its parents as specified by the given graph.
     *
     * @param data the dataset containing the variables and their observed values
     * @param g the graph that specifies the parent-child relationships among the variables
     * @param reg the regressor used to compute the residuals for each variable
     * @return a 2D array representing the residual matrix, where each row corresponds to a data point
     *         and each column corresponds to the residuals for one variable
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
     * Constructs a residual matrix where each column corresponds to a variable in the dataset
     * and contains the residuals obtained after regressing that variable on its parents
     * as specified by the given parent map.
     *
     * @param data the dataset containing the variables and their observed values
     * @param parentsMap a map where each key is a variable and the value is the list of its parent variables
     * @param reg the regressor used to compute the residuals for each variable
     * @return a 2D array representing the residual matrix, where each row corresponds to a data point
     *         and each column corresponds to the residuals for one variable
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
     * Standardizes the input residual matrix in place using a robust method based on the
     * median and median absolute deviation (MAD). This method scales each column of the
     * matrix by the MAD, ensuring that the scaling is less sensitive to outliers.
     *
     * @param R a 2D array representing the residual matrix. Each row corresponds to a data point,
     *          and each column corresponds to residuals for a specific variable. The input array
     *          is modified in place, with each column being standardized according to robust statistics.
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
