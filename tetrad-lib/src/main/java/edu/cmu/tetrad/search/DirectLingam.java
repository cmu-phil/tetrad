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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.cmu.tetrad.util.TMath.*;

/**
 * Implements the DirectLiNGAM algorithm for learning a linear non-Gaussian acyclic model.
 *
 * <p>DirectLiNGAM estimates a causal ordering by repeatedly selecting a variable that appears
 * most exogenous among the variables not yet ordered. After choosing such a variable, the
 * remaining variables are residualized with respect to it, and the process repeats on the
 * residual system. Once an ordering has been obtained, parent sets are selected from earlier
 * variables using grow-shrink trees built from the supplied score.</p>
 *
 * <p>This implementation follows the general strategy of the following references:</p>
 *
 * <ul>
 *   <li>
 *     Shimizu, S., Inazumi, T., Sogawa, Y., Hyvärinen, A., Kawahara, Y., Washio, T.,
 *     Hoyer, P. O., and Bollen, K. (2011). DirectLiNGAM: A direct method for learning
 *     a linear non-Gaussian structural equation model. Journal of Machine Learning Research,
 *     12, 1225–1248.
 *   </li>
 *   <li>
 *     Hyvärinen, A. and Smith, S. M. (2013). Pairwise likelihood ratios for estimation of
 *     non-Gaussian structural equation models. Journal of Machine Learning Research, 14, 111–152.
 *   </li>
 * </ul>
 *
 * <p>The pairwise criterion used here is based on an entropy-style approximation. Variables are
 * standardized before the ordering phase, and residuals are computed by simple least-squares
 * projection.</p>
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class DirectLingam {

    /** Input data set. */
    private final DataSet dataset;

    /** Variables in data-set order. */
    private final List<Node> variables;

    /**
     * Grow-shrink trees used after the ordering step to choose parent sets
     * among variables that have already been placed earlier in the ordering.
     */
    private final Map<Node, GrowShrinkTree> gsts;

    /**
     * Constructs a DirectLiNGAM search object from a data set and a score.
     *
     * <p>The supplied score is used only in the parent-selection phase after the
     * causal ordering has been estimated.</p>
     *
     * @param dataset the input data set
     * @param score the score used to initialize the grow-shrink trees
     */
    public DirectLingam(DataSet dataset, Score score) {
        this.dataset = dataset;
        this.variables = dataset.getVariables();
        this.gsts = new HashMap<>();

        int i = 0;
        Map<Node, Integer> index = new HashMap<>();

        for (Node node : this.variables) {
            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
        }
    }

    /**
     * Returns an entropy-style approximation for the supplied data vector.
     *
     * <p>The input is first standardized. The returned quantity is based on a
     * maximum-entropy / negentropy approximation of the type commonly used in
     * ICA- and LiNGAM-related methods. In this implementation it serves as a
     * scoring ingredient for the pairwise comparison step.</p>
     *
     * @param x the data vector
     * @return the entropy-style approximation used by the pairwise criterion
     */
    private static double maxEntApprox(double[] x) {
        x = StatUtils.standardizeData(x);

        final double k1 = 79.047;
        final double k2 = 36.0 / (8.0 * sqrt(3.0) - 9.0);
        final double gamma = 0.37457;
        final double gaussianEntropy = (log(2.0 * PI) / 2.0) + 0.5;

        double b1 = 0.0;

        for (double value : x) {
            // First term in the log-cosh style approximation.
            b1 += value * value / 2.0;
        }

        b1 /= x.length;

        double b2 = 0.0;

        for (double value : x) {
            b2 += value * exp(-(value * value) / 2.0);
        }

        b2 /= x.length;

        double d = b1 - gamma;
        double negentropy = k1 * (d * d) + k2 * (b2 * b2);

        return gaussianEntropy - negentropy;
    }

    /**
     * Executes DirectLiNGAM and returns the learned graph.
     *
     * <p>The algorithm proceeds in two phases:</p>
     *
     * <ol>
     *   <li>Estimate a causal ordering by repeatedly selecting the next
     *       approximately exogenous variable and residualizing the remaining variables.</li>
     *   <li>For each variable in the resulting order, select parents from among the
     *       earlier variables using the corresponding grow-shrink tree.</li>
     * </ol>
     *
     * @return the learned graph
     */
    public Graph search() {
        List<Node> remaining = new ArrayList<>(this.variables);
        Map<Node, double[]> residualMap = new HashMap<>();

        double[][] dataColumns = this.dataset.getDoubleData().transpose().toArray();

        for (int i = 0; i < dataColumns.length; i++) {
            standardize(dataColumns[i]);
            residualMap.put(this.variables.get(i), dataColumns[i]);
        }

        Set<Node> ordered = new HashSet<>();
        Graph graph = new EdgeListGraph(this.variables);

        while (!remaining.isEmpty()) {
            Node next = getNext(remaining, residualMap);
            remaining.remove(next);

            for (Node node : remaining) {
                residualMap.put(node, residuals(residualMap.get(node), residualMap.get(next)));
            }

            ordered.add(next);

            Set<Node> parents = new HashSet<>();
            this.gsts.get(next).trace(ordered, ordered, parents);

            for (Node parent : parents) {
                graph.addDirectedEdge(parent, next);
            }
        }

        return graph;
    }

    /**
     * Returns the next variable to place in the causal ordering.
     *
     * <p>Among the variables not yet ordered, this method selects the variable that
     * minimizes the DirectLiNGAM pairwise objective computed from the current residual
     * system. Smaller values indicate a variable that appears more nearly exogenous.</p>
     *
     * @param remaining the variables not yet ordered
     * @param residualMap the current residualized data vectors for those variables
     * @return the next variable to place in the causal ordering
     */
    private Node getNext(List<Node> remaining, Map<Node, double[]> residualMap) {
        Node bestNode = remaining.getFirst();
        double bestScore = Double.POSITIVE_INFINITY;

        for (Node x : remaining) {
            double currentScore = 0.0;
            double entropyX = maxEntApprox(residualMap.get(x));

            for (Node y : remaining) {
                if (x == y) {
                    continue;
                }

                double[] rxy = residuals(residualMap.get(x), residualMap.get(y));
                double[] ryx = residuals(residualMap.get(y), residualMap.get(x));

                double lr = maxEntApprox(residualMap.get(y)) - entropyX;
                lr += maxEntApprox(rxy) - maxEntApprox(ryx);

                double clipped = min(0.0, lr);
                currentScore += clipped * clipped;
            }

            if (currentScore < bestScore) {
                bestScore = currentScore;
                bestNode = x;
            }
        }

        return bestNode;
    }

    /**
     * Standardizes the supplied array in place to mean 0 and standard deviation 1.
     *
     * <p>If the standard deviation is numerically too small, the array is left
     * unchanged.</p>
     *
     * @param x the array to standardize
     */
    private void standardize(double[] x) {
        int n = x.length;
        double mean = 0.0;
        double sumSquares = 0.0;

        for (double value : x) {
            mean += value;
            sumSquares += value * value;
        }

        mean /= n;

        double variance = sumSquares / n - mean * mean;
        double std = sqrt(max(variance, 0.0));

        if (std < 1e-12) {
            return;
        }

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mean) / std;
        }
    }

    /**
     * Returns the least-squares residuals of {@code x} after regressing on {@code y}.
     *
     * <p>If the variance of {@code y} is numerically too small, a copy of {@code x}
     * is returned unchanged.</p>
     *
     * @param x the response variable
     * @param y the predictor variable
     * @return the residual vector {@code x - b y}, where {@code b = cov(x, y) / var(y)}
     */
    private double[] residuals(double[] x, double[] y) {
        int n = x.length;
        double cov = 0.0;
        double var = 0.0;

        for (int i = 0; i < n; i++) {
            cov += x[i] * y[i];
            var += y[i] * y[i];
        }

        if (var < 1e-12) {
            return x.clone();
        }

        double b = cov / var;

        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            residuals[i] = x[i] - b * y[i];
        }

        return residuals;
    }
}