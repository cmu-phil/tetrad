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
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.StatUtils.*;
import static edu.cmu.tetrad.util.TMath.*;

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
     * E[x * y | condition > 0]. This is a helper for calculating expectations over a sub-population defined by a
     * positive condition.
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
     * Calculates the correlation expectation: corrExp(x,y|z) = E(xy|z>0) / sqrt(E(x^2|z>0) E(y^2|z>0)).
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
     * E(xy | z>0). This is a duplicate of the `cu` method but specifically naming the condition as `z`.
     *
     * @param x the first array of data points
     * @param y the second array of data points
     * @param z the condition array
     * @return the expected value of the product of x and y given z
     */
    public static double E(double[] x, double[] y, double[] z) {
        return cu(x, y, z);
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

    /**
     * Returns a signed left-right score after first residualizing both variables on
     * a common covariate set intended to block alternative paths between them.
     *
     * <p>The covariate set is obtained using {@link #orientationCovariates(Graph, Node, Node, int)}.
     * If recursive blocking fails to produce a set, the fallback is
     * Adj(xi) ∪ Adj(xj) \ {xi, xj}. The selected left-right rule is then applied
     * to standardized residuals.</p>
     *
     * <p>This method is intended especially for cyclic settings, where applying the
     * left-right rule directly to the raw variables may be distorted by feedback paths.</p>
     */
    public static double leftRightDiffResidualized(int ruleIndex, Graph graph, Node xi, Node xj, List<Node> nodes,
                                                   double[][] data) {
        double[][] z = covariates(graph, xi, xj, nodes, data, -1);

        double[] x = data[nodes.indexOf(xi)];
        double[] y = data[nodes.indexOf(xj)];

        double[] rx = residualize(x, z);
        double[] ry = residualize(y, z);
//                        double score = leftRightDiff(x, y, ruleIndex);
        standardize(rx);
        standardize(ry);

        return switch (ruleIndex) {
            case 1 -> fask1Score(rx, ry);
            case 2 -> fask2Score(rx, ry);
            case 3 -> rskewScore(rx, ry);
            case 4 -> skewScore(rx, ry);
            case 5 -> tanhScore(rx, ry);
            default -> throw new IllegalArgumentException("Unknown ruleIndex (1..5): " + ruleIndex);
        };
    }

    /**
     * Returns a covariate set for residualizing xi and xj before applying an
     * orientation rule.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try {@code RecursiveBlocking} to obtain a covariate set that blocks
     *       alternative paths between xi and xj.</li>
     *   <li>If that fails ({@code null}), fall back heuristically to
     *       Adj(xi) ∪ Adj(xj) \ {xi, xj}.</li>
     *   <li>Optionally, if desired later, this can be extended with filtering.</li>
     * </ol>
     *
     * <p>This method never returns null; it returns an empty set if no covariates
     * are available.</p>
     */
    public static Set<Node> orientationCovariates(Graph graph,
                                                  Node xi,
                                                  Node xj,
                                                  int maxPathLength) {
        try {
            Set z = RecursiveBlocking.blockPathsRecursively(
                    graph, xi, xj, Set.of(), Set.of(), maxPathLength
            );

            if (z == null) {
                z = RecursiveBlocking.blockPathsRecursively(
                        graph, xj, xi, Set.of(), Set.of(), maxPathLength
                );
            }

            if (z == null) {
                z = new HashSet<>(graph.getAdjacentNodes(xi));
                z.addAll(graph.getAdjacentNodes(xj));
            } else {
                z = new HashSet<>(z);
            }

            z.remove(xi);
            z.remove(xj);

            return z;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
    }

    /**
     * Returns covariate columns in the format expected by residualize():
     * covariates[k] is the data column for the k-th covariate node.
     *
     * <p>The input data are assumed to be column-major:
     * data[nodeIndex][rowIndex].</p>
     */
    public static double[][] covariates(Graph graph,
                                        Node xi,
                                        Node xj,
                                        List<Node> nodes,
                                        double[][] data,
                                        int maxPathLength) {
        Set<Node> z = orientationCovariates(graph, xi, xj, maxPathLength);

        List<Node> covariateNodes = new ArrayList<>(z);
        double[][] covariates = new double[covariateNodes.size()][];

        for (int k = 0; k < covariateNodes.size(); k++) {
            int index = nodes.indexOf(covariateNodes.get(k));
            if (index < 0) {
                throw new IllegalArgumentException("Node not found in nodes list: " + covariateNodes.get(k));
            }
            covariates[k] = data[index];
        }

        return covariates;
    }

    // ------------ Rule score implementations (double-signed) ------------

    /**
     * FASK1 scoring method: signed left-right score after skewness and correlation sign alignment. If the correlation
     * is below a certain threshold (delta), the direction is flipped.
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double fask1Score(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double left = cu(x, y, x) / (sqrt(cu(x, x, x) * cu(y, y, x)));
        double right = cu(x, y, y) / (sqrt(cu(x, x, y) * cu(y, y, y)));
        double lr = left - right;

        double r = StatUtils.correlation(x, y);
//        double sx = StatUtils.skewness(x);
//        double sy = StatUtils.skewness(y);

//        r *= signum(sx) * signum(sy);
//        lr *=  signum(r);

        // Use the same default delta as instance (−0.1) for static scoring.
        if (r < 0.0) lr *= -1;
        return lr;
    }

    /**
     * FASK2 scoring method: corrExp(x,y|x) − corrExp(x,y|y).
     *
     * @param x standardized (recommended) series for X
     * @param y standardized (recommended) series for Y
     * @return signed left-right score
     */
    private static double fask2Score(double[] x, double[] y) {
        double lr = corrExp(x, y, x) - corrExp(x, y, y);
        double r = StatUtils.correlation(x, y);
        if (r < 0.0) lr *= -1;
        return lr;
    }

    /**
     * Standardizes the array in place to mean 0 and sample standard deviation 1.
     */
    public static void standardize(double[] x) {
        int n = x.length;

        double mean = 0.0;
        for (double v : x) mean += v;
        mean /= n;

        double var = 0.0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }

        var /= (n - 1);
        double sd = Math.sqrt(var);

        if (sd == 0) sd = 1.0;

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mean) / sd;
        }
    }

    /**
     * Returns linear least-squares residuals of x after regressing on the supplied covariates.
     *
     * <p>The covariates are interpreted column-wise:
     * covariates[j][i] is the value of the j-th covariate in row i.</p>
     *
     * <p>No intercept is added internally, so callers should standardize or center
     * variables beforehand if that is desired.</p>
     */
    public static double[] residualize(double[] x, double[][] covariates) {

        int n = x.length;

        if (covariates == null || covariates.length == 0) {
            return x.clone();
        }

        int p = covariates.length;

        // Build normal equation matrices
        double[][] xtx = new double[p][p];
        double[] xty = new double[p];

        for (int j = 0; j < p; j++) {
            for (int k = j; k < p; k++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    sum += covariates[j][i] * covariates[k][i];
                }
                xtx[j][k] = sum;
                xtx[k][j] = sum;
            }

            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += covariates[j][i] * x[i];
            }
            xty[j] = sum;
        }

        // Solve xtx * beta = xty
        double[] beta = solve(xtx, xty);

        // Compute residuals
        double[] r = new double[n];

        for (int i = 0; i < n; i++) {

            double pred = 0.0;

            for (int j = 0; j < p; j++) {
                pred += beta[j] * covariates[j][i];
            }

            r[i] = x[i] - pred;
        }

        return r;
    }

    private static double[] solve(double[][] A, double[] b) {

        int n = b.length;

        double[][] M = new double[n][n + 1];

        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }

        // Forward elimination
        for (int k = 0; k < n; k++) {

            int max = k;
            for (int i = k + 1; i < n; i++) {
                if (Math.abs(M[i][k]) > Math.abs(M[max][k])) {
                    max = i;
                }
            }

            double[] temp = M[k];
            M[k] = M[max];
            M[max] = temp;

            double pivot = M[k][k];

            if (Math.abs(pivot) < 1e-12) continue;

            for (int j = k; j <= n; j++) {
                M[k][j] /= pivot;
            }

            for (int i = 0; i < n; i++) {
                if (i == k) continue;

                double factor = M[i][k];

                for (int j = k; j <= n; j++) {
                    M[i][j] -= factor * M[k][j];
                }
            }
        }

        double[] x = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = M[i][n];
        }

        return x;
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
        for (int i = 0; i < x.length; i++) lr[i] = x[i] * TMath.tanh(y[i]) - TMath.tanh(x[i]) * y[i];
        return correlation(x, y) * mean(lr);
    }

    /**
     * Helper for robustSkew.
     */
    private static double g(double x) {
        return log(cosh(TMath.max(x, 0)));
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

        DataSet dataSet = DataTransforms.center(this.dataSet);
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

                double skewX = StatUtils.cov(x, y, x, 0, +1)[1];
                double skewY = StatUtils.cov(x, y, y, 0, +1)[1];

                if ((useFasAdjacencies && G0.isAdjacentTo(X, Y)) ||
                        (useSkewAdjacencies && TMath.abs(skewX - skewY) > extraEdgeThreshold)) {
                    if (knowledgeOrients(X, Y)) {
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        graph.addDirectedEdge(Y, X);
                    } else if (isBidirected(x, y, G0, X, Y)) {
                        graph.addEdge(Edges.directedEdge(X, Y));
                        graph.addEdge(Edges.directedEdge(Y, X));
                    } else {
                        int ruleIndex = leftRight.ordinal() + 1;
                        // Raw left-right score on x and y.
                        // The residualized cyclic version appears to work better in both cyclic and acyclic settings.
//                        double score = leftRightDiff(x, y, ruleIndex);
                        double score = leftRightDiffResidualized(ruleIndex, G0, X, Y, variables, data);
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

    private boolean isBidirected(double[] x, double[] y, Graph G0, Node X, Node Y) {
        // -------------------- Candidate Z pool: neighbors of X or Y (excluding X,Y) --------------------
        Set<Node> pool = new HashSet<>(G0.getAdjacentNodes(X));
        pool.addAll(G0.getAdjacentNodes(Y));
        List<Node> cand = new ArrayList<>(pool);
        cand.remove(X);
        cand.remove(Y);

        // -------------------- Housekeeping / guards --------------------
        final int n = x.length;
        final int minPart = (int) TMath.ceil(0.15 * n); // require at least 15% of samples in X>0 and Y>0
        final double ridge = 1e-6;                      // small ridge in partial-corr inversion
        final double clampEps = 1e-6;                   // Fisher z clamp
        final int maxSize = (depth < 0) ? cand.size() : TMath.min(depth, cand.size());

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

    /**
     * Checks if the (X,Y) pair exhibits the "cycle opposition pattern" under conditioning Z.
     * <p>
     * The cycle opposition pattern is characterized by significant shifts in partial correlations when conditioning on
     * X > 0 versus Y > 0, which suggests a feedback loop (X <-> Y).
     */
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
        double _pc = TMath.max(-1.0 + clampEps, TMath.min(1.0 - clampEps, pc));
        double _pc1 = TMath.max(-1.0 + clampEps, TMath.min(1.0 - clampEps, pc1));
        double _pc2 = TMath.max(-1.0 + clampEps, TMath.min(1.0 - clampEps, pc2));

        // Fisher z
        double z = 0.5 * (TMath.log(1.0 + _pc) - TMath.log(1.0 - _pc));
        double z1 = 0.5 * (TMath.log(1.0 + _pc1) - TMath.log(1.0 - _pc1));
        double z2 = 0.5 * (TMath.log(1.0 + _pc2) - TMath.log(1.0 - _pc2));

        // Standardized directional shifts
        int nAll = StatUtils.getRows(x, x, Double.NEGATIVE_INFINITY, +1).size();
        double zv1 = (z - z1) / TMath.sqrt((1.0 / ((double) nAll - 3)) + (1.0 / ((double) nxPos - 3)));
        double zv2 = (z - z2) / TMath.sqrt((1.0 / ((double) nAll - 3)) + (1.0 / ((double) nyPos - 3)));

        boolean rejected1 = TMath.abs(zv1) > cutoff;
        boolean rejected2 = TMath.abs(zv2) > cutoff;

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