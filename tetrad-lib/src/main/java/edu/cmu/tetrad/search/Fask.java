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
 *
 * Exposes both boolean LR decisions (for backward compatibility) and a new
 * signed "difference/score" API so upstream algorithms (e.g., FCI-FASK) can
 * choose thresholds or compare competing rules:
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
 *
 * All existing public behavior is preserved.
 *
 * @author Joseph Ramsey
 */
public final class Fask {
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
    private double delta = -0.1;
    private Fask.LeftRight leftRight = Fask.LeftRight.FASK1;

    // --- 2-cycle tuning knobs (conservative defaults) ---
    private int twoCycleMinHits = 1;            // require at least K subset votes
    private double twoCycleMinPartitionFrac = 0.1; // min fraction in X>0 or Y>0
    private boolean twoCycleBonferroni = false;  // Bonferroni over subset tests
    private double twoCycleRidgeLambda = 1e-6;  // ridge for partial corr

    // ------------ Ctor ------------
    public Fask(DataSet dataSet, Score score) {
        this.dataSet = dataSet;
        this.score = score;
        this.data = dataSet.getDoubleData().transpose().toArray();
    }

    // ------------ Public static utilities (unchanged signatures) ------------

    /** E[x y | condition > 0]. */
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

    /** corrExp(x,y|z) = E(xy|z>0) / sqrt(E(x^2|z>0) E(y^2|z>0)). */
    public static double corrExp(double[] x, double[] y, double[] z) {
        return E(x, y, z) / sqrt(E(x, x, z) * E(y, y, z));
    }

    /** E(xy | z>0). */
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

    /** Static compatibility: previous boolean FASK2. */
    public static boolean leftRightV2(double[] x, double[] y) {
        return leftRightDiff(x, y, 2) > 0.0;
    }

    // ------------ NEW public static API: expose signed difference ------------

    /**
     * Returns a signed left-right "difference/score" per rule.
     * Positive ⇒ x→y, Negative ⇒ y→x.
     *
     * @param x          standardized (recommended) series for X
     * @param y          standardized (recommended) series for Y
     * @param ruleIndex  1=FASK1, 2=FASK2, 3=RSKEW, 4=SKEW, 5=TANH
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

    /** FASK1: signed lr after skew/corr sign alignment and delta flip if r<delta. */
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
        if (r < -0.1) lr *= -1;
        return lr;
    }

    /** FASK2: corrExp(x,y|x) − corrExp(x,y|y). */
    private static double fask2Score(double[] x, double[] y) {
        return corrExp(x, y, x) - corrExp(x, y, y);
    }

    /** Hyvärinen–Smith robust skew: corr(x,y) * mean(g(x)*y − x*g(y)), with sign-corrected skew. */
    private static double rskewScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = g(x[i]) * y[i] - x[i] * g(y[i]);
        return correlation(x, y) * mean(lr);
    }

    /** Hyvärinen–Smith skew: corr(x,y) * mean(x^2*y − x*y^2), with sign-corrected skew. */
    private static double skewScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = x[i] * x[i] * y[i] - x[i] * y[i] * y[i];
        return correlation(x, y) * mean(lr);
    }

    /** Hyvärinen–Smith tanh: corr(x,y) * mean(x*tanh(y) − tanh(x)*y), with sign-corrected skew. */
    private static double tanhScore(double[] x, double[] y) {
        x = correctSkewness(x, skewness(x));
        y = correctSkewness(y, skewness(y));
        double[] lr = new double[x.length];
        for (int i = 0; i < x.length; i++) lr[i] = x[i] * FastMath.tanh(y[i]) - FastMath.tanh(x[i]) * y[i];
        return correlation(x, y) * mean(lr);
    }

    /** Helper for robustSkew. */
    private static double g(double x) {
        return log(cosh(FastMath.max(x, 0)));
    }

    /** Multiply by sign of skew so “positive skew” convention holds. */
    private static double[] correctSkewness(double[] data, double sk) {
        double s = signum(sk);
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) out[i] = data[i] * s;
        return out;
    }

    // ------------ Main search ------------

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

    // ------------ Config ------------
    public void setLeftRight(Fask.LeftRight leftRight) {
        this.leftRight = leftRight;
    }

    public void setCutoff(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Significance out of range: " + alpha);
        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public void setExtraEdgeThreshold(double extraEdgeThreshold) {
        this.extraEdgeThreshold = extraEdgeThreshold;
    }

    public void setUseFasAdjacencies(boolean useFasAdjacencies) {
        this.useFasAdjacencies = useFasAdjacencies;
    }

    public void setUseSkewAdjacencies(boolean useSkewAdjacencies) {
        this.useSkewAdjacencies = useSkewAdjacencies;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }

    public void setTwoCycleMinHits(int k) {
        this.twoCycleMinHits = Math.max(1, k);
    }

    public void setTwoCycleMinPartitionFrac(double f) {
        this.twoCycleMinPartitionFrac = Math.max(0.0, Math.min(0.49, f));
    }

    public void setTwoCycleBonferroni(boolean b) {
        this.twoCycleBonferroni = b;
    }

    public void setTwoCycleRidgeLambda(double lambda) {
        this.twoCycleRidgeLambda = Math.max(0.0, lambda);
    }

    // ------------ Internals ------------

    // --- 2-cycle vote thresholds ---
//    private int twoCycleMinHits = 2;        // require at least K subsets to flag possibleTwoCycle
    private double twoCycleMinRatio = 0.30; // or require at least this fraction of subsets to flag

//    private boolean bidirected(double[] x, double[] y, Graph G0, Node X, Node Y) {
//        Set<Node> adjSet = new HashSet<>(G0.getAdjacentNodes(X));
//        adjSet.addAll(G0.getAdjacentNodes(Y));
//        List<Node> adj = new ArrayList<>(adjSet);
//        adj.remove(X);
//        adj.remove(Y);
//
//        int numIsPossibleTwoCycles = 0;
//        int numTotal = 0;
//
//        double min = Double.POSITIVE_INFINITY;
//        boolean minChoice = true;
//
//        SublistGenerator gen = new SublistGenerator(adj.size(), Math.min(depth, adj.size()));
//        int[] choice;
//        while ((choice = gen.next()) != null) {
//            List<Node> _adj = GraphUtils.asList(choice, adj);
//
//
//            double[][] _Z = new double[_adj.size()][];
//            for (int f = 0; f < _adj.size(); f++) {
//                Node _z = _adj.get(f);
//                int column = dataSet.getColumn(_z);
//                _Z[f] = data[column];
//            }
//
//            double lambda = 0.0;
//
//            double pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY, +1, lambda);
//            double pc1 = partialCorrelation(x, y, _Z, x, 0, +1, lambda);
//            double pc2 = partialCorrelation(x, y, _Z, y, 0, +1, lambda);
//
//            int nc = StatUtils.getRows(x, x, Double.NEGATIVE_INFINITY, +1).size();
//            int nc1 = StatUtils.getRows(x, x, 0, +1).size();
//            int nc2 = StatUtils.getRows(y, y, 0, +1).size();
//
//            double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
//            double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
//            double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));
//
//            double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
//            double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));
//
//
//            boolean rejected1 = abs(zv1) > cutoff;
//            boolean rejected2 = abs(zv2) > cutoff;
//
//            boolean possibleTwoCycle = false;
//            if (zv1 < 0 && zv2 > 0 && rejected1) possibleTwoCycle = true;
//            else if (zv1 > 0 && zv2 < 0 && rejected2) possibleTwoCycle = true;
//            else if (rejected1 && rejected2) possibleTwoCycle = true;
//
//            double sum = abs(zv1) + abs(zv2);
//            if (sum < min) {
//                min = sum;
//                minChoice = possibleTwoCycle;
//            }
//
//            System.out.print("adj " + X + " *-* " + Y + " = " +_adj + " zv1 = " + z + " zv2 = " + zv2 + " sum = " + sum);
//
//            if (possibleTwoCycle) {
//                System.out.print(" possibleTwoCycle");
//                numIsPossibleTwoCycles++;
//            }
//
//            System.out.println();
//
//            numTotal++;
//        }
//
//        double ratio = numIsPossibleTwoCycles / (double) numTotal;
//        System.out.println(X + "*-* " + Y + " numIsPossibleTwoCycle = " + numIsPossibleTwoCycles + " numTotal " + numTotal
//                           + " ratio = " + ratio + " max = " + min + " minChoice = " + minChoice);
//
//        return minChoice;
//    }

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
            pc  = partialCorrelation(x, y, Z, x, Double.NEGATIVE_INFINITY, +1, ridge);
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
        double _pc  = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc));
        double _pc1 = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc1));
        double _pc2 = Math.max(-1.0 + clampEps, Math.min(1.0 - clampEps, pc2));

        // Fisher z
        double z  = 0.5 * (Math.log(1.0 + _pc ) - Math.log(1.0 - _pc ));
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
        if (rejected1 && rejected2)          return true;

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

//    private boolean bidirected(double[] x, double[] y, Graph G0, Node X, Node Y) {
//        // Local conditioning pool
//        Set<Node> adjSet = new HashSet<>(G0.getAdjacentNodes(X));
//        adjSet.addAll(G0.getAdjacentNodes(Y));
//        List<Node> adj = new ArrayList<>(adjSet);
//        adj.remove(X);
//        adj.remove(Y);
//
//        // Depth handling: depth < 0 means "all sizes"
//        int maxSize = (depth < 0) ? adj.size() : Math.min(depth, adj.size());
//        if (maxSize < 0) maxSize = adj.size(); // extra guard
//
//        // No candidates? still test Z = empty set
//        if (adj.isEmpty() && maxSize == 0) maxSize = 0;
//
//        SublistGenerator gen = new SublistGenerator(adj.size(), maxSize);
//
//        final int n = x.length;
//        final int minPart = (int) Math.ceil(twoCycleMinPartitionFrac * n);
//
//        // Collect viable subset results to determine adjusted thresholds
//        class SubTest {
//            final double zv1, zv2; // standardized z-diffs (z - z1)/se1, (z - z2)/se2
//            final double s1, s2;   // signs of (z - z1), (z - z2)
//
//            SubTest(double zv1, double zv2, double s1, double s2) {
//                this.zv1 = zv1;
//                this.zv2 = zv2;
//                this.s1 = s1;
//                this.s2 = s2;
//            }
//        }
//        List<SubTest> results = new ArrayList<>();
//        int viable = 0;
//
//        int[] choice;
//        while ((choice = gen.next()) != null) {
//            List<Node> _adj = GraphUtils.asList(choice, adj);
//            double[][] Z = new double[_adj.size()][];
//            for (int f = 0; f < _adj.size(); f++) {
//                Node _z = _adj.get(f);
//                int col = dataSet.getColumn(_z);
//                Z[f] = data[col];
//            }
//
//            // Partition sizes
//            int ncAll = n;
//            int nc1 = StatUtils.getRows(x, x, 0, +1).size();
//            int nc2 = StatUtils.getRows(y, y, 0, +1).size();
//
////            // Require enough rows in both partitions
////            if (nc1 < minPart || nc2 < minPart || ncAll <= 3 || nc1 <= 3 || nc2 <= 3) {
////                continue;
////            }
//
//            double lambda = twoCycleRidgeLambda;
//
//            double pc = partialCorrelation(x, y, Z, x, Double.NEGATIVE_INFINITY, +1, lambda);
//            double pc1 = partialCorrelation(x, y, Z, x, 0, +1, lambda);
//            double pc2 = partialCorrelation(x, y, Z, y, 0, +1, lambda);
//
////            // Clamp to avoid infinities
////            pc = Math.max(-0.999999, Math.min(0.999999, pc));
////            pc1 = Math.max(-0.999999, Math.min(0.999999, pc1));
////            pc2 = Math.max(-0.999999, Math.min(0.999999, pc2));
//
//            double z = 0.5 * (Math.log1p(pc) - Math.log1p(-pc));
//            double z1 = 0.5 * (Math.log1p(pc1) - Math.log1p(-pc1));
//            double z2 = 0.5 * (Math.log1p(pc2) - Math.log1p(-pc2));
//
//            double se1 = Math.sqrt(1.0 / (ncAll - 3.0) + 1.0 / (nc1 - 3.0));
//            double se2 = Math.sqrt(1.0 / (ncAll - 3.0) + 1.0 / (nc2 - 3.0));
//
//            double zv1 = (z - z1) / se1;
//            double zv2 = (z - z2) / se2;
//
//            double s1 = Math.signum(z - z1);
//            double s2 = Math.signum(z - z2);
//
//            results.add(new SubTest(zv1, zv2, s1, s2));
//            viable++;
//        }
//
//        if (viable == 0) {
//            // As a last resort, test Z = empty set if we didn't already
//            double[][] Z = new double[0][];
//            int ncAll = n, nc1 = StatUtils.getRows(x, x, 0, +1).size(), nc2 = StatUtils.getRows(y, y, 0, +1).size();
//            if (nc1 >= Math.max(4, minPart) && nc2 >= Math.max(4, minPart)) {
//                double lambda = twoCycleRidgeLambda;
//                double pc = partialCorrelation(x, y, Z, x, Double.NEGATIVE_INFINITY, +1, lambda);
//                double pc1 = partialCorrelation(x, y, Z, x, 0, +1, lambda);
//                double pc2 = partialCorrelation(x, y, Z, y, 0, +1, lambda);

    ////                pc = Math.max(-0.999999, Math.min(0.999999, pc));
    ////                pc1 = Math.max(-0.999999, Math.min(0.999999, pc1));
    ////                pc2 = Math.max(-0.999999, Math.min(0.999999, pc2));
//                double z = 0.5 * (Math.log1p(pc) - Math.log1p(-pc));
//                double z1 = 0.5 * (Math.log1p(pc1) - Math.log1p(-pc1));
//                double z2 = 0.5 * (Math.log1p(pc2) - Math.log1p(-pc2));
//                double se1 = Math.sqrt(1.0 / (ncAll - 3.0) + 1.0 / (nc1 - 3.0));
//                double se2 = Math.sqrt(1.0 / (ncAll - 3.0) + 1.0 / (nc2 - 3.0));
//                results.add(new SubTest((z - z1) / se1, (z - z2) / se2,
//                        Math.signum(z - z1), Math.signum(z - z2)));
//                viable = 1;
//            }
//        }
//
//        if (viable == 0) return false;
//
//        // Compute thresholds
//        double alphaLocalStrict = alpha;
//        if (twoCycleBonferroni) alphaLocalStrict = Math.min(1.0, alpha / viable);
//        double zcritStrict = StatUtils.getZForAlpha(alphaLocalStrict);
//
//        // Relaxed threshold (softer than strict)
//        double alphaLocalRelax = Math.min(1.0, 3.0 * alpha); // e.g., 3× alpha (no Bonferroni)
//        double zcritRelax = StatUtils.getZForAlpha(alphaLocalRelax);
//
//        // Tally hits
//        int hitsStrict = 0, hitsRelax = 0;
//        for (SubTest r : results) {
//            boolean opp = (r.s1 * r.s2 < 0.0);
//
//            boolean bothSigStrict =
//                    (Math.abs(r.zv1) > zcritStrict) && (Math.abs(r.zv2) > zcritStrict);
//
//            boolean oneStrictOneRelax =
//                    (Math.abs(r.zv1) > zcritStrict && Math.abs(r.zv2) > zcritRelax) ||
//                    (Math.abs(r.zv2) > zcritStrict && Math.abs(r.zv1) > zcritRelax);
//
//            if (opp && bothSigStrict) hitsStrict++;
//            else if (opp && oneStrictOneRelax) hitsRelax++;
//        }
//
//        if (hitsStrict >= twoCycleMinHits) return true;
//        // fallback: allow relaxed evidence if strict didn’t reach K
//        return hitsRelax >= twoCycleMinHits;
//    }
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

    // ------------ Enum ------------
    public enum LeftRight {
        FASK1, FASK2, RSKEW, SKEW, TANH
    }
}