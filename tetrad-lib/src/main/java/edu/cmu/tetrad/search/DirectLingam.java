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
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
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
 * <p>Several data sets over the same variables (for instance, the completed data sets of a multiple imputation, or
 * several samples believed to share one structure) can be given in place of one. The ordering is then found by the
 * same procedure with the pairwise objective summed over the data sets, each residualized on its own and each
 * weighted by its share of the total sample size (as in the multi-group DirectLiNGAM of the lingam Python package);
 * the score supplied for parent selection should then be one pooled over the same data sets, such as the IMaGES
 * score.</p>
 *
 * <p>Background knowledge is honored (see {@link #setKnowledge(Knowledge)}). Tiers and required edges constrain
 * the ordering: a variable is not placed while a variable from an earlier tier, or one of its required parents,
 * is still waiting. Forbidden and required edges, including those a tier's "forbidden within" setting implies,
 * constrain parent selection.</p>
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
 * <p>Missing values are not accepted: the constructor throws if any data set contains one. The
 * ordering phase residualizes whole columns and carries the residuals forward, so there is no
 * pairwise or test-wise deletion that does not collapse to list-wise deletion as the ordering grows.
 * Impute (several times, pooling with the list-of-data-sets constructor) or delete rows beforehand.
 * Previously a single NaN anywhere made every candidate's objective NaN, and the ordering silently
 * fell back to the column order of the data set.</p>
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class DirectLingam {

    /** Input data set. */
    private final List<DataSet> datasets;

    /** Variables in data-set order. */
    private final List<Node> variables;

    /**
     * Grow-shrink trees used after the ordering step to choose parent sets
     * among variables that have already been placed earlier in the ordering.
     */
    private final Map<Node, GrowShrinkTree> gsts;

    /**
     * For each variable, the tier the knowledge puts it in; variables in no tier are absent.
     */
    private final Map<Node, Integer> tiers = new HashMap<>();

    /**
     * For each variable, the variables the knowledge requires as its parents; variables with none are absent.
     */
    private final Map<Node, List<Node>> requiredParents = new HashMap<>();

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
        this(List.of(dataset), score);
    }

    /**
     * Constructs a DirectLiNGAM search object from several data sets over the same variables and a score. The
     * causal ordering is found from all the data sets together (see the class comment); the score, which should be
     * pooled over the same data sets, selects the parents.
     *
     * @param datasets the data sets; at least one, all with the same variables by name, in the same order, and
     *                 none with missing values
     * @param score    the score used to initialize the grow-shrink trees
     * @throws IllegalArgumentException if the data sets disagree on variables or any contains a missing value
     */
    public DirectLingam(List<DataSet> datasets, Score score) {
        if (datasets == null || datasets.isEmpty()) throw new IllegalArgumentException("At least one data set.");

        List<String> names = datasets.getFirst().getVariableNames();

        for (DataSet dataset : datasets) {
            if (!dataset.getVariableNames().equals(names)) {
                throw new IllegalArgumentException("The data sets must have the same variables in the same order.");
            }

            if (dataset.existsMissingValue()) {
                throw new IllegalArgumentException("DirectLiNGAM: the data contain missing values, which the ordering "
                        + "phase cannot handle (it residualizes whole columns). Impute first, e.g. multiple "
                        + "imputation with pooling, or delete incomplete rows. "
                        + MissingDataUtils.briefSummary(dataset));
            }
        }

        this.datasets = new ArrayList<>(datasets);
        this.variables = datasets.getFirst().getVariables();
        this.gsts = new HashMap<>();

        int i = 0;
        Map<Node, Integer> index = new HashMap<>();

        for (Node node : this.variables) {
            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
        }
    }

    /**
     * Sets background knowledge. All of it is honored, in the two places it can act.
     *
     * <ul>
     *   <li><i>The ordering.</i> A variable is a candidate for the next place only if no variable still
     *   waiting is in an earlier tier and none is one of its required parents. Variables in no tier are
     *   unconstrained by tiers. The pairwise objective itself is unchanged and still compares each candidate
     *   with every waiting variable.</li>
     *   <li><i>Parent selection.</i> Forbidden parents are never chosen and required parents always are; this
     *   covers explicit forbidden and required edges and whatever the tiers' "forbidden within" and "can cause
     *   only next tier" settings forbid.</li>
     * </ul>
     *
     * <p>If the knowledge is true of the generating model, restricting the candidates loses nothing: among the
     * waiting variables at least one that is exogenous always remains a candidate. If it is false, the search
     * follows it anyway. Knowledge about variables not in the data is ignored. Knowledge with no possible
     * ordering (required edges forming a cycle, or running against the tiers) makes {@link #search()} throw.</p>
     *
     * @param knowledge the knowledge; null for none
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) knowledge = new Knowledge();

        this.tiers.clear();
        this.requiredParents.clear();

        for (Node node : this.variables) {
            int tier = knowledge.isInWhichTier(node);
            if (tier >= 0) this.tiers.put(node, tier);

            List<Node> required = new ArrayList<>();
            List<Node> forbidden = new ArrayList<>();

            for (Node parent : this.variables) {
                if (parent == node) continue;
                if (knowledge.isRequired(parent.getName(), node.getName())) required.add(parent);
                if (knowledge.isForbidden(parent.getName(), node.getName())) forbidden.add(parent);
            }

            if (!required.isEmpty()) this.requiredParents.put(node, required);
            this.gsts.get(node).setKnowledge(required, forbidden);
        }
    }

    /**
     * The variables among those not yet ordered that the knowledge allows to be placed next: those with no
     * waiting variable in an earlier tier and no waiting required parent. All of them when there is no knowledge.
     */
    private List<Node> candidates(List<Node> remaining) {
        if (this.tiers.isEmpty() && this.requiredParents.isEmpty()) return remaining;

        int earliest = Integer.MAX_VALUE;

        for (Node node : remaining) {
            Integer tier = this.tiers.get(node);
            if (tier != null && tier < earliest) earliest = tier;
        }

        List<Node> candidates = new ArrayList<>();

        for (Node node : remaining) {
            Integer tier = this.tiers.get(node);
            if (tier != null && tier > earliest) continue;

            boolean parentWaiting = false;

            for (Node parent : this.requiredParents.getOrDefault(node, List.of())) {
                if (remaining.contains(parent)) {
                    parentWaiting = true;
                    break;
                }
            }

            if (!parentWaiting) candidates.add(node);
        }

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("DirectLiNGAM: the knowledge allows no causal order. Each of these "
                    + "variables must come after another of them, by a required edge or by the tiers: " + remaining);
        }

        return candidates;
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
            // G1(u) = log cosh u (Hyvarinen 1998). Do not replace with u^2/2: on
            // standardized data its mean is the constant (n-1)/(2n), which would
            // erase the even (kurtosis-like) half of the negentropy approximation.
            b1 += log(cosh(value));
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

        // One residual system per data set, by variable position, so that the data sets need only agree on names.
        List<Map<Node, double[]>> residualMaps = new ArrayList<>();

        for (DataSet dataset : this.datasets) {
            Map<Node, double[]> residualMap = new HashMap<>();
            double[][] dataColumns = dataset.getDoubleData().transpose().toArray();

            for (int i = 0; i < dataColumns.length; i++) {
                standardize(dataColumns[i]);
                residualMap.put(this.variables.get(i), dataColumns[i]);
            }

            residualMaps.add(residualMap);
        }

        Set<Node> ordered = new HashSet<>();
        Graph graph = new EdgeListGraph(this.variables);

        while (!remaining.isEmpty()) {
            Node next = getNext(remaining, residualMaps);
            remaining.remove(next);

            for (Map<Node, double[]> residualMap : residualMaps) {
                for (Node node : remaining) {
                    residualMap.put(node, residuals(residualMap.get(node), residualMap.get(next)));
                }
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
     * <p>Among the variables not yet ordered that the knowledge allows next (all of them
     * when there is none; see {@link #setKnowledge(Knowledge)}), this method selects the variable that
     * minimizes the DirectLiNGAM pairwise objective computed from the current residual
     * system, summed over the data sets with each weighted by its share of the total
     * sample size. Smaller values indicate a variable that appears more nearly
     * exogenous. With one data set the weight is exactly 1.</p>
     *
     * @param remaining    the variables not yet ordered
     * @param residualMaps the current residualized data vectors for those variables, one map per data set
     * @return the next variable to place in the causal ordering
     */
    private Node getNext(List<Node> remaining, List<Map<Node, double[]>> residualMaps) {
        List<Node> candidates = candidates(remaining);
        if (candidates.size() == 1) return candidates.getFirst();

        Node bestNode = candidates.getFirst();
        double bestScore = Double.POSITIVE_INFINITY;

        double totalRows = 0.0;

        for (DataSet dataset : this.datasets) {
            totalRows += dataset.getNumRows();
        }

        for (Node x : candidates) {
            double currentScore = 0.0;

            for (int k = 0; k < residualMaps.size(); k++) {
                Map<Node, double[]> residualMap = residualMaps.get(k);
                double weight = this.datasets.get(k).getNumRows() / totalRows;
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
                    currentScore += weight * clipped * clipped;
                }
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