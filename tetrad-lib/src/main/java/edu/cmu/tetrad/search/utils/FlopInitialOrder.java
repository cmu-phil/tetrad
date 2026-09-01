/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the principled initial variable order proposed in Section 4.1 of Wienobst, Henckel, and Weichwald (2026),
 * "Embracing Discrete Search: A Reasonable Approach to Causal Structure Learning" (the FLOP paper), for use with
 * order-based permutation searches such as BOSS or FLOP.
 * <p>
 * The order is constructed so that strongly correlated variables are placed adjacent to one another: it begins with
 * the two most correlated variables and then repeatedly appends the unplaced variable with the smallest residual
 * variance when regressed on the variables already placed. This is computed via a pivoted Cholesky decomposition of
 * the correlation matrix, requiring O(p^3) time and O(p^2) memory in the worst case.
 * <p>
 * The motivation is that order-based searches using grow-shrink parent selection can fail on far-apart
 * ancestor-descendant pairs whose marginal dependence is too weak to detect in finite samples (path graphs are the
 * canonical hard case). Placing strongly correlated variables adjacently in the initial order avoids asking
 * grow-shrink to detect such weak long-range dependencies.
 * <p>
 * The correlation matrix (rather than the covariance matrix) is used throughout, which makes the resulting order
 * invariant to the scaling of the individual variables.
 * <p>
 * Typical usage with BOSS:
 * <pre>
 *     PermutationSearch search = new PermutationSearch(new Boss(score));
 *     search.setOrder(FlopInitialOrder.initialOrder(dataSet));
 *     Graph cpdag = search.search();
 * </pre>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.search.PermutationSearch
 * @see edu.cmu.tetrad.search.Boss
 */
public final class FlopInitialOrder {

    /**
     * Prevents instantiation.
     */
    private FlopInitialOrder() {
    }

    /**
     * Computes the FLOP initial order for the variables in the given data set.
     *
     * @param dataSet A continuous data set.
     * @return The variables of the data set, in the FLOP initial order.
     */
    public static List<Node> initialOrder(DataSet dataSet) {
        return initialOrder(new CorrelationMatrix(dataSet));
    }

    /**
     * Computes the FLOP initial order for the variables of the given covariance matrix. The matrix is first converted
     * to a correlation matrix, so the result is scale-invariant.
     *
     * @param covariances A covariance (or correlation) matrix.
     * @return The variables of the covariance matrix, in the FLOP initial order.
     */
    public static List<Node> initialOrder(ICovarianceMatrix covariances) {
        CorrelationMatrix corr = (covariances instanceof CorrelationMatrix c)
                ? c : new CorrelationMatrix(covariances);
        int p = corr.getDimension();
        List<Node> variables = corr.getVariables();

        if (p < 3) return new ArrayList<>(variables);

        double[][] r = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                r[i][j] = corr.getValue(i, j);
            }
        }

        int[] order = pivotedCholeskyOrder(r);

        List<Node> result = new ArrayList<>(p);
        for (int i = 0; i < p; i++) {
            result.add(variables.get(order[i]));
        }

        return result;
    }

    /**
     * Computes the order via a pivoted Cholesky decomposition of the given correlation matrix. The first two
     * positions are given to the most correlated pair; thereafter the pivot is the unplaced variable with the
     * smallest residual variance given the placed variables.
     *
     * @param r A correlation matrix, as a p x p array. This array is not modified.
     * @return A permutation of {0, ..., p - 1}.
     */
    private static int[] pivotedCholeskyOrder(double[][] r) {
        int p = r.length;
        double[][] L = new double[p][p]; // Partial Cholesky columns; row i = variable i.
        double[] d = new double[p];      // Current residual variances.
        boolean[] placed = new boolean[p];
        int[] order = new int[p];

        for (int i = 0; i < p; i++) d[i] = r[i][i];

        // Find the most correlated pair (a, b).
        int a = 0;
        int b = 1;
        double best = -1.0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double abs = Math.abs(r[i][j]);
                if (abs > best) {
                    best = abs;
                    a = i;
                    b = j;
                }
            }
        }

        int next = a;

        for (int t = 0; t < p; t++) {
            int j = next;
            placed[j] = true;
            order[t] = j;

            // Append the Cholesky column for variable j and downdate the residual
            // variances of the unplaced variables.
            double ljj = Math.sqrt(Math.max(d[j], 1e-12));
            L[j][t] = ljj;

            for (int i = 0; i < p; i++) {
                if (placed[i]) continue;
                double s = r[i][j];
                for (int k = 0; k < t; k++) s -= L[i][k] * L[j][k];
                L[i][t] = s / ljj;
                d[i] -= L[i][t] * L[i][t];
            }

            if (t + 1 == p) break;

            if (t == 0) {
                // The second position goes to the partner of the most correlated pair.
                next = b;
            } else {
                // Otherwise pick the unplaced variable with the smallest residual variance.
                next = -1;
                double min = Double.POSITIVE_INFINITY;

                for (int i = 0; i < p; i++) {
                    if (!placed[i] && d[i] < min) {
                        min = d[i];
                        next = i;
                    }
                }
            }
        }

        return order;
    }
}
