/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.SublistGenerator;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.signum;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements the FASK (Fast Adjacency Skewness) algorithm.
 * <p>
 * Exposes both boolean LR decisions (for backward compatibility) and a new signed "difference/score" API so upstream
 * algorithms (e.g., FCI-FASK) can choose thresholds or compare competing rules:
 *
 * <ul>
 *   <li><b>leftRightDiff(x, y, ruleIndex)</b> → double:
 *     <ul>
 *       <li>ruleIndex = 1: FASK1 score</li>
 *       <li>ruleIndex = 2: FASK2 score (corrExp(x,y|x) − corrExp(x,y|y))</li>
 *       <li>ruleIndex = 3: RSKEW (Hyvärinen–Smith robust skew) score</li>
 *       <li>ruleIndex = 4: SKEW (Hyvärinen–Smith skew) score</li>
 *       <li>ruleIndex = 5: TANH (Hyvärinen–Smith tanh) score</li>
 *     </ul>
 *     Positive ⇒ x→y, Negative ⇒ y→x.</li>
 * </ul>
 * <p>
 * All existing public behavior is preserved.
 *
 * @author Joseph Ramsey
 */
public final class Fask {
    private static double delta = -0.1;
    // ------------ Fields ------------
    private final Score score;
    private final double[][] data;
    private final DataSet dataSet;
    private Graph externalGraph = null;
    private int depth = -1;
    private double alpha = 1e-5;
    private Knowledge knowledge = new Knowledge();
    private double cutoff;
    private double extraEdgeThreshold = 0.3;
    private boolean useFasAdjacencies = true;
    private boolean useSkewAdjacencies = true;
    private Fask.LeftRight leftRight = LeftRight.RSKEW;

    /**
     * Constructs a new Fask instance with the specified data set and score.
     *
     * @param dataSet the data set used for the analysis
     * @param score   the scoring method utilized for evaluating the data
     */
    public Fask(DataSet dataSet, Score score) {
        this.dataSet = dataSet;
        this.score = score;
        this.data = dataSet.getDoubleData().transpose().toArray();
    }

    // ------------ Public static utilities (unchanged signatures) ------------

    /**
     * E[x y | condition > 0].
     *
     * @param x         the first array of data points
     * @param y         the second array of data points
     * @param condition the condition array
     * @return the expected value of the product of x and y given the condition
     */
    public static double cu(double[] x, double[] y, double[] condition) {
        double exy = 0.0;
        int n = 0;
        for (int k = 0; k < x.length; k++)
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        return exy / n;
    }

    /**
     * corrExp(x,y|z) = E(xy|z>0) / sqrt(E(x^2|z>0) E(y^2|z>0)).
     *
     * @param x the first array of data points
     * @param y the second array of data points
     * @param z the condition array
     * @return the correlation expectation of x and y given z
     */
    public static double corrExp(double[] x, double[] y, double[] z) {
        return E(x, y, z) / sqrt(E(x, x, z) * E(y, y, z));
    }

    /**
     * E(xy | z>0).
     *
     * @param x the first array of data points
     * @param y the second array of data points
     * @param z the condition array
     * @return the expected value of the product of x and y given z
     */
    public static double E(double[] x, double[] y, double[] z) {
        double exy = 0.0;
        int n = 0;
        for (int k = 0; k < x.length; k++)
            if (z[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        return exy / n;
    }

    /**
     * Returns a signed left-right "difference/score" per rule. Positive ⇒ x→y, Negative ⇒ y→x.
     *
     * @param x         standardized (recommended) series for X
     * @param y         standardized (recommended) series for Y
     * @param ruleIndex 1=FASK1, 2=FASK2, 3=RSKEW, 4=SKEW, 5=TANH
     * @return signed left-right score
     */
    public static double leftRightDiff(double[] x, double[] y, int ruleIndex) {
        return switch (ruleIndex) {
            case 1 -> fask1Score(x, y);
            case 2 -> fask2Score(x, y);
            case 3 -> rskewScore(x, y);
            case 4 -> skewScore(x, y);
            case 5 -> tanhScore(x, y);
            default -> throw new IllegalArgumentException("Unknown ruleIndex (1..5): " + ruleIndex);
        };
    }

    // ------------ Rule score implementations (double-signed) ------------

    /**
     * FASK1: signed lr after skew/corr sign alignment and delta flip if r<delta.
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double fask1Score(double[] x, double[] y) {
        double left = cu(x, y, x) / (sqrt(cu(x, x, x) * cu(y, y, x)));
        double right = cu(x, y, y) / (sqrt(cu(x, x, y) * cu(y, y, y)));
        double lr = left - right;

        double r = StatUtils.correlation(x, y);
        double sx = StatUtils.skewness(x);
        double sy = StatUtils.skewness(y);

        r *= signum(sx) * signum(sy);
        lr *= signum(r);

        // Use the same default delta as instance (−0.1) for static scoring.
        if (r < delta) lr *= -1;
        return lr;
    }

    /**
     * FASK2: corrExp(x,y|x) − corrExp(x,y|y).
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double fask2Score(double[] x, double[] y) {
        return corrExp(x, y, x) - corrExp(x, y, y);
    }

    /**
     * Hyvärinen–Smith robust skew: corr(x,y) * mean(g(x)*y − x*g(y)), with sign-corrected skew.
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double rskewScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = g(x[i]) * y[i] - x[i] * g(y[i]);
        return correlation(x, y) * mean(lr);
    }

    /**
     * Hyvärinen–Smith skew: corr(x,y) * mean(x^2*y − x*y^2), with sign-corrected skew.
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double skewScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = x[i] * x[i] * y[i] - x[i] * y[i] * y[i];
        return correlation(x, y) * mean(lr);
    }

    /**
     * Hyvärinen–Smith tanh: corr(x,y) * mean(x*tanh(y) − tanh(x)*y), with sign-corrected skew.
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double tanhScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = x[i] * FastMath.tanh(y[i]) - FastMath.tanh(x[i]) * y[i];
        return correlation(x, y) * mean(lr);
    }

    /**
     * Helper for robustSkew.
     */
    private static double g(double x) {
        return log(cosh(FastMath.max(x, 0)));
    }

    /**
     * Multiply by sign of skew so “positive skew” convention holds.
     */
    private static double[] correctSkewness(double[] data, double sk) {
        double s = signum(sk);
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) out[i] = data[i] * s;
        return out;
    }

    // ------------ Main search ------------

    /**
     * Set the delta parameter for FASK search.
     *
     * @param _delta The new delta value to be set for the FASK algorithm.
     */
    public static void setDelta(double _delta) {
        delta = _delta;
    }

    /**
     * Executes the FASK (Fast Adjacency Skewness) algorithm to search for a causal graph based on the provided dataset,
     * knowledge, and configurations.
     * <p>
     * The method first standardizes the dataset and initializes a preliminary graph structure with either an external
     * graph or through a Fast Adjacency Search (FAS) with a scoring method. It then iteratively examines pairs of
     * variables to determine potential causal edges based on various scoring rules, adjacency conditions, and provided
     * prior knowledge. The final graph includes directed and potentially bidirected edges based on the algorithm's
     * logic.
     *
     * @return A causal graph inferred by the FASK algorithm, where nodes represent variables and edges denote the
     * presence and direction of inferred causal relationships.
     * @throws InterruptedException if the execution is interrupted during the search process.
     */
    public Graph search() throws InterruptedException {
        setCutoff(alpha);

        DataSet dataSet = DataTransforms.standardizeData(this.dataSet);
        List<Node> variables = dataSet.getVariables();
        double[][] colData = dataSet.getDoubleData().transpose().toArray();
        Graph G0;

        if (externalGraph != null) {
            Graph g1 = new EdgeListGraph(externalGraph.getNodes());
            for (Edge edge : externalGraph.getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();
                if (!g1.isAdjacentTo(x, y)) g1.addUndirectedEdge(x, y);
            }
            g1 = GraphUtils.replaceNodes(g1, dataSet.getVariables());
            G0 = g1;
        } else {
            IndependenceTest test = new ScoreIndTest(score, dataSet);
            Fas fas = new Fas(test);
            fas.setStable(true);
            fas.setDepth(depth);
            fas.setVerbose(false);
            fas.setKnowledge(knowledge);
            G0 = fas.search();
        }

        GraphSearchUtils.pcOrientbk(knowledge, G0, G0.getNodes(), false);

        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                Node X = variables.get(i);
                Node Y = variables.get(j);

                final double[] x = colData[i];
                final double[] y = colData[j];

                double c1 = StatUtils.cov(x, y, x, 0, +1)[1];
                double c2 = StatUtils.cov(x, y, y, 0, +1)[1];

                if ((useFasAdjacencies && G0.isAdjacentTo(X, Y)) ||
                    (useSkewAdjacencies && Math.abs(c1 - c2) > extraEdgeThreshold)) {

                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (bidirected(x, y, G0, X, Y)) {
                        graph.addEdge(Edges.directedEdge(X, Y));
                        graph.addEdge(Edges.directedEdge(Y, X));
                    } else {
                        // Use the enum-selected rule but via score>0
                        double score = switch (leftRight) {
                            case FASK1 -> fask1Score(x, y);
                            case FASK2 -> fask2Score(x, y);
                            case RSKEW -> rskewScore(x, y);
                            case SKEW -> skewScore(x, y);
                            case TANH -> tanhScore(x, y);
                        };
                        if (score > 0) graph.addDirectedEdge(X, Y);
                        else graph.addDirectedEdge(Y, X);
                    }
                }
            }
        }

        return graph;
    }

    /**
     * Sets the left-right scoring method used in the FASK algorithm.
     *
     * @param leftRight the left-right scoring method to be used, represented by the Fask.LeftRight enum
     */
    public void setLeftRight(Fask.LeftRight leftRight) {
        this.leftRight = leftRight;
    }

    /**
     * Sets the significance level for the FASK algorithm.
     *
     * @param alpha the significance level, must be between 0.0 and 1.0
     */
    public void setCutoff(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Significance out of range: " + alpha);
        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    /**
     * Sets the depth of the search in the FASK algorithm.
     *
     * @param depth the depth of the search, must be non-negative
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the significance level (alpha) for the FASK algorithm. This parameter determines
     * the threshold used in statistical tests within the algorithm, and must be a value
     * between 0.0 and 1.0.
     *
     * @param alpha the significance level, must be between 0.0 and 1.0
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets the prior knowledge for the FASK algorithm. This knowledge represents constraints
     * or background information that can guide or restrict the causal discovery process.
     *
     * @param knowledge the prior knowledge object to be used in the algorithm
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Sets the external graph for the FASK algorithm.
     * This graph can serve as an initial structure or provide constraints
     * for further causal discovery during the algorithm's execution.
     *
     * @param externalGraph the external graph to be used, represented as a Graph object
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Sets the threshold value for considering extra edges in the FASK algorithm.
     *
     * @param extraEdgeThreshold the threshold value to be set for extra edges, where a lower value
     *                           might result in more potential extra edges being considered in the analysis,
     *                           while a higher value might be more restrictive
     */
    public void setExtraEdgeThreshold(double extraEdgeThreshold) {
        this.extraEdgeThreshold = extraEdgeThreshold;
    }

    /**
     * Sets whether the FASK algorithm should use Fast Adjacency Search (FAS) for determining adjacencies.
     * This configuration influences how the initial graph structure is constructed during the algorithm's execution.
     *
     * @param useFasAdjacencies a boolean indicating whether to use FAS adjacencies. If true, FAS is used to
     *                          determine adjacencies during the graph search process; if false, an alternative
     *                          approach may be employed.
     */
    public void setUseFasAdjacencies(boolean useFasAdjacencies) {
        this.useFasAdjacencies = useFasAdjacencies;
    }

    /**
     * Configures whether the FASK algorithm should utilize skew adjacencies during its execution.
     * Skew adjacencies, if enabled, influence the process by considering relationships
     * determined through the skewness of data distributions.
     *
     * @param useSkewAdjacencies a boolean indicating whether to use skew adjacencies.
     *                           If true, skewness-based adjacencies are considered as part
     *                           of the graph construction process; if false, they are excluded.
     */
    public void setUseSkewAdjacencies(boolean useSkewAdjacencies) {
        this.useSkewAdjacencies = useSkewAdjacencies;
    }

    // ------------ Internals ------------

    private boolean bidirected(double[] x, double[] y, Graph G0, Node X, Node Y) {
        // -------------------- Candidate Z pool: neighbors of X or Y (excluding X,Y) --------------------
        Set<Node> pool = new HashSet<>(G0.getAdjacentNodes(X));
        pool.addAll(G0.getAdjacentNodes(Y));
        List<Node> cand = new ArrayList<>(pool);
        cand.remove(X);
        cand.remove(Y);

        // -------------------- Housekeeping / guards --------------------
        final int n = x.length;
        final int minPart = (int) Math.ceil(0.15 * n); // require at least 15% of samples in X>0 and Y>0
        final double ridge = 1e-6;                      // small ridge in partial-corr inversion
        final double clampEps = 1e-6;                   // Fisher z clamp
        final int maxSize = (depth < 0) ? cand.size() : Math.min(depth, cand.size());

        // -------------------- 1) Baseline: does the unconditioned pattern look cyclic? --------------------
        if (!showsCyclePattern(x, y, /*Z=*/null, minPart, ridge, clampEps)) {
            // If even without Z we don't see the cycle opposition pattern, it's not a 2-cycle.
            return false;
        }

        // -------------------- 2) Try to BREAK the cycle by conditioning --------------------
        // Evaluate Z = ∅ explicitly first (already done; it didn't break it), then scan subsets up to depth.
        SublistGenerator gen = new SublistGenerator(cand.size(), maxSize);
        int[] choice;
        while ((choice = gen.next()) != null) {
            List<Node> zNodes = GraphUtils.asList(choice, cand);
            if (breaksCyclePattern(x, y, zNodes, minPart, ridge, clampEps)) {
                // FOUND at least one Z that breaks the cycle opposition/ significance -> NOT a 2-cycle.
                return false;
            }
        }

        // If NO subset breaks the cycle pattern -> robustly unbreakable -> two-cycle.
        return true;
    }

    // === Returns true if the (X,Y) pair exhibits the "cycle opposition pattern" under conditioning Z ===
    private boolean showsCyclePattern(double[] x, double[] y, List<Node> zNodes,
                                      int minPart, double ridge, double clampEps) {

        double[][] Z = (zNodes == null) ? new double[0][] : buildZ(zNodes);

        // Partial correlations under three “slices”: all, X>0, Y>0
        final double pc, pc1, pc2;
        try {
            pc = partialCorrelation(x, y, Z, x, Double.NEGATIVE_INFINITY, +1, ridge);
            pc1 = partialCorrelation(x, y, Z, x, 0, +1, ridge);
            pc2 = partialCorrelation(x, y, Z, y, 0, +1, ridge);
        } catch (Exception e) {
            // Singular/unstable Z; treat as "cannot establish opposition pattern"
            return false;
        }

        // Partition sizes (guard tiny strata)
        int nxPos = StatUtils.getRows(x, x, 0, +1).size();
        int nyPos = StatUtils.getRows(y, y, 0, +1).size();
        if (nxPos < minPart || nyPos < minPart) return false;

        // Clamp correlations for Fisher z
        double _pc = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc));
        double _pc1 = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc1));
        double _pc2 = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc2));

        // Fisher z
        double z = 0.5 * (Math.log(1.0 + _pc) - Math.log(1.0 - _pc));
        double z1 = 0.5 * (Math.log(1.0 + _pc1) - Math.log(1.0 - _pc1));
        double z2 = 0.5 * (Math.log(1.0 + _pc2) - Math.log(1.0 - _pc2));

        // Standardized directional shifts
        int nAll = StatUtils.getRows(x, x, Double.NEGATIVE_INFINITY, +1).size();
        double zv1 = (z - z1) / Math.sqrt((1.0 / ((double) nAll - 3)) + (1.0 / ((double) nxPos - 3)));
        double zv2 = (z - z2) / Math.sqrt((1.0 / ((double) nAll - 3)) + (1.0 / ((double) nyPos - 3)));

        boolean rejected1 = Math.abs(zv1) > cutoff;
        boolean rejected2 = Math.abs(zv2) > cutoff;

        // "Cycle opposition pattern": opposite signs with at least one significant;
        // or both significant (even if not cleanly opposite)
        if (zv1 < 0 && zv2 > 0 && rejected1) return true;
        if (zv1 > 0 && zv2 < 0 && rejected2) return true;
        if (rejected1 && rejected2) return true;

        return false;
    }

    // === Returns true if conditioning on Z BREAKS the cycle opposition pattern (i.e., destroys it) ===
    private boolean breaksCyclePattern(double[] x, double[] y, List<Node> zNodes,
                                       int minPart, double ridge, double clampEps) {
        // We “break” if the cycle pattern is NOT present under Z.
        return !showsCyclePattern(x, y, zNodes, minPart, ridge, clampEps);
    }

    // === Utility to build Z matrix ===
    private double[][] buildZ(List<Node> zNodes) {
        double[][] Z = new double[zNodes.size()][];
        for (int i = 0; i < zNodes.size(); i++) {
            int col = dataSet.getColumn(zNodes.get(i));
            Z[i] = data[col];
        }
        return Z;
    }

    private double partialCorrelation(double[] x, double[] y, double[][] z, double[] condition,
                                      double threshold, double direction, double lambda)
            throws SingularMatrixException {
        double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, direction);
        Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m, lambda);
    }

    private boolean knowledgeOrients(Node left, Node right) {
        return knowledge.isForbidden(right.getName(), left.getName())
               || knowledge.isRequired(left.getName(), right.getName());
    }

    /**
     * An enumeration representing directional and functional types.
     * The constants in this enumeration could signify configurations or operations
     * that relate to left, right, or other mathematical transformations.
     */
    public enum LeftRight {
        /**
         * Use FASK1.
         */
        FASK1,
        /**
         * Use FASK2.
         */
        FASK2,
        /**
         * Use RSkew.
         */
        RSKEW,
        /**
         * Use Skew.
         */
        SKEW,
        /**
         * Use Tanh.
         */
        TANH
    }
}