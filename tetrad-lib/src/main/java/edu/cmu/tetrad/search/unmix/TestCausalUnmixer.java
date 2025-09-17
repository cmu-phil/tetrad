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

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Better-targeted unmixing tests with ground-truth labels + ARI. - params-only test (pooled initializer, no residual
 * scaling, Laplace reassignment) - small topology difference test (parent-superset initializer, residual scaling) -
 * EM-on-residuals baseline (parent-superset, diagonal covariance), with ARI
 */
public class TestCausalUnmixer {

    /**
     * Default constructor for the TestCausalUnmixer class.
     *
     * This constructor initializes an instance of the TestCausalUnmixer, a class designed
     * to test the performance of a causal unmixing algorithm in semi-synthetic scenarios.
     * The class provides methods for generating datasets with structural differences, running
     * the causal unmixing algorithm, and evaluating the results using various performance metrics.
     */
    public TestCausalUnmixer() {}

    // ---------- utilities ----------

    /**
     * Quick ARI for diagnostics.
     */
    private static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        int maxA = Arrays.stream(a).max().orElse(0);
        int maxB = Arrays.stream(b).max().orElse(0);
        int[][] M = new int[maxA + 1][maxB + 1];
        int[] row = new int[maxA + 1], col = new int[maxB + 1];
        for (int i = 0; i < n; i++) {
            M[a[i]][b[i]]++;
            row[a[i]]++;
            col[b[i]]++;
        }
        double sumComb = 0, rowComb = 0, colComb = 0;
        for (int i = 0; i <= maxA; i++) for (int j = 0; j <= maxB; j++) sumComb += comb2(M[i][j]);
        for (int i = 0; i <= maxA; i++) rowComb += comb2(row[i]);
        for (int j = 0; j <= maxB; j++) colComb += comb2(col[j]);
        double totalComb = comb2(n);
        double exp = rowComb * colComb / totalComb;
        double max = 0.5 * (rowComb + colComb);
        return (sumComb - exp) / (max - exp + 1e-12);
    }

    private static double comb2(int m) {
        return m < 2 ? 0 : m * (m - 1) / 2.0;
    }

    /**
     * Make a two-regime mixture with small topology differences and non-Gaussian errors.
     */
    private static @NotNull TestCausalUnmixer.LabeledData getMixOutTopoDiff() {
        int p = 12, n1 = 800, n2 = 800;
        long seed = 7;

        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph gA = RandomGraph.randomGraph(vars, 0, 14, 100, 100, 100, false);
        Graph gB = copyWithFlippedDirections(gA, 4, new Random(seed)); // flip ~4 edges

        // Non-Gaussian errors (e.g., Laplace): SIMULATION_ERROR_TYPE = 3
        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imA = new SemIm(new SemPm(gA), params);
        SemIm imB = new SemIm(new SemPm(gB), params);

        DataSet dA = imA.simulateData(n1, false);
        DataSet dB = imB.simulateData(n2, false);

        DataSet concat = DataTransforms.concatenate(dA, dB);
        int[] labels = new int[n1 + n2];
        Arrays.fill(labels, 0, n1, 0);
        Arrays.fill(labels, n1, n1 + n2, 1);

        return shuffleWithLabels(concat, labels, seed);
    }

    /**
     * Correctly copy g and flip a few directions while preserving skeleton.
     */
    private static Graph copyWithFlippedDirections(Graph g, int flips, Random rnd) {
        Graph h = new EdgeListGraph(g); // copy nodes + edges (or: new EdgeListGraph(g))
        List<Edge> dir = h.getEdges().stream().filter(Edge::isDirected).collect(Collectors.toList());
        if (dir.isEmpty()) return h;
        Collections.shuffle(dir, rnd);
        int done = 0;
        for (Edge e : dir) {
            if (done >= flips) break;
            Node a = e.getNode1(), b = e.getNode2();
            if (!h.isAdjacentTo(a, b)) continue;
            h.removeEdge(e);
            Edge rev = Edges.directedEdge(b, a);
            if (!h.isAdjacentTo(b, a)) {
                h.addEdge(rev);
                done++;
            } else {
                h.addEdge(e);
            }
        }
        return h;
    }

    private static int structuralHammingDistance(Graph A, Graph B) {
        Set<String> EA = directedEdgeSet(A), EB = directedEdgeSet(B);
        Set<String> UA = undirectedEdgeSet(A), UB = undirectedEdgeSet(B);
        // skeleton difference
        Set<String> SA = new HashSet<>(UA);
        SA.addAll(stripDirections(EA));
        Set<String> SB = new HashSet<>(UB);
        SB.addAll(stripDirections(EB));
        Set<String> sym = new HashSet<>(SA);
        sym.removeAll(SB);
        Set<String> sym2 = new HashSet<>(SB);
        sym2.removeAll(SA);
        int skelDiff = sym.size() + sym2.size();
        // orientation differences on common skeleton
        Set<String> inter = new HashSet<>(SA);
        inter.retainAll(SB);
        int orientDiff = 0;
        for (String s : inter) {
            String[] ab = s.split("--");
            String a = ab[0], b = ab[1];
            Edge ea = A.getEdge(A.getNode(a), A.getNode(b));
            Edge eb = B.getEdge(B.getNode(a), B.getNode(b));
            boolean da = ea != null && ea.isDirected();
            boolean db = eb != null && eb.isDirected();
            if (da != db) orientDiff++;
            else if (da) {
                if (!(ea.getNode1().getName().equals(eb.getNode1().getName()) && ea.getNode2().getName().equals(eb.getNode2().getName()))) {
                    orientDiff++;
                }
            }
        }
        return skelDiff + orientDiff;
    }

    private static Set<String> directedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (Edge e : G.getEdges()) if (e.isDirected()) s.add(e.getNode1().getName() + ">" + e.getNode2().getName());
        return s;
    }

    private static Set<String> undirectedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (Edge e : G.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            String key = a.compareTo(b) < 0 ? a + "--" + b : b + "--" + a;
            s.add(key);
        }
        return s;
    }

    private static Set<String> stripDirections(Set<String> dir) {
        Set<String> s = new HashSet<>();
        for (String e : dir) {
            String[] ab = e.split(">");
            String key = ab[0].compareTo(ab[1]) < 0 ? ab[0] + "--" + ab[1] : ab[1] + "--" + ab[0];
            s.add(key);
        }
        return s;
    }

    /**
     * Compare two graphs on (i) adjacency F1; (ii) orientation F1 over the shared skeleton; (iii) SHD.
     */
    private static Metrics compareGraphs(Graph Gt, Graph Gh) {
        if (Gt == null || Gh == null) return new Metrics(0, 0, Integer.MAX_VALUE / 4);

        // Evaluate in equivalence-class space: CPDAGs capture only compelled orientations.
        // Assumes DAG inputs; if an estimator may output non-DAGs, add a DAG check/repair.
        Gt = GraphTransforms.dagToCpdag(Gt);
        Gh = GraphTransforms.dagToCpdag(Gh);

        // --- Adjacency (skeleton) F1
        Set<String> skelT = undirectedEdgeSet(Gt);
        Set<String> skelH = undirectedEdgeSet(Gh);

        Set<String> inter = new HashSet<>(skelT);
        inter.retainAll(skelH);

        int tp = inter.size();
        int fp = Math.max(skelH.size() - tp, 0);
        int fn = Math.max(skelT.size() - tp, 0);

        double precA = tp == 0 ? 0 : (double) tp / (tp + fp);
        double recA = tp == 0 ? 0 : (double) tp / (tp + fn);
        double adjF1 = (precA + recA == 0) ? 0 : 2 * precA * recA / (precA + recA);

        // --- Orientation F1 over shared skeleton
        // Treat a directed edge as an ordered pair "A>B".
        Set<String> dirT = directedEdgeSet(Gt);
        Set<String> dirH = directedEdgeSet(Gh);

        int tpO = 0, fpO = 0, fnO = 0;
        for (String e : inter) {
            String[] ab = e.split("--");
            String a = ab[0], b = ab[1];
            String abDir = a + ">" + b, baDir = b + ">" + a;

            boolean t_ab = dirT.contains(abDir), t_ba = dirT.contains(baDir);
            boolean h_ab = dirH.contains(abDir), h_ba = dirH.contains(baDir);

            // Count TP when both are directed the same way.
            if (t_ab && h_ab) tpO++;
            if (t_ba && h_ba) tpO++;

            // FN: true is directed but hypothesized is either undirected or opposite.
            if (t_ab && !h_ab) fnO++;
            if (t_ba && !h_ba) fnO++;

            // FP: hypothesized is directed but true is either undirected or opposite.
            if (h_ab && !t_ab) fpO++;
            if (h_ba && !t_ba) fpO++;
        }

        double precO = (tpO + fpO) == 0 ? 0 : (double) tpO / (tpO + fpO);
        double recO = (tpO + fnO) == 0 ? 0 : (double) tpO / (tpO + fnO);
        double arrowF1 = (precO + recO == 0) ? 0 : 2 * precO * recO / (precO + recO);

        int shd = structuralHammingDistance(Gt, Gh);
        return new Metrics(adjF1, arrowF1, shd);
    }

    /**
     * Compare discovered cluster graphs to truths with a robust matching that tolerates size mismatches (e.g., only 1
     * cluster found). Greedy matching by best adj-F1.
     */
    private static GraphMetrics graphMetrics(Graph[] truth, List<Graph> found) {
        if (truth == null || truth.length == 0 || found == null || found.isEmpty()) {
            return new GraphMetrics(0.0, 0.0, Integer.MAX_VALUE / 4);
        }

        int F = found.size();
        boolean[] used = new boolean[F];

        double adjSum = 0.0, arrowSum = 0.0;
        int shdSum = 0, matches = 0;

        for (Graph Gt : truth) {
            double bestAdj = -1.0, bestArrow = 0.0;
            int bestShd = Integer.MAX_VALUE, bestF = -1;

            for (int fi = 0; fi < F; fi++) {
                if (used[fi]) continue;
                Graph Gf = found.get(fi);
                if (Gf == null) continue;

                Metrics m = compareGraphs(Gt, Gf);
                if (m.adjF1 > bestAdj || (m.adjF1 == bestAdj && m.shd < bestShd)) {
                    bestAdj = m.adjF1;
                    bestArrow = m.arrowF1;
                    bestShd = m.shd;
                    bestF = fi;
                }
            }

            if (bestF >= 0) {
                used[bestF] = true;
                adjSum += bestAdj;
                arrowSum += bestArrow;
                shdSum += bestShd;
                matches++;
            } else {
                // Nothing to match: penalize by SHD to an empty graph on same nodes.
                shdSum += structuralHammingDistance(Gt, new EdgeListGraph(Gt.getNodes()));
            }
        }

        if (matches == 0) return new GraphMetrics(0.0, 0.0, shdSum);
        return new GraphMetrics(adjSum / matches, arrowSum / matches, shdSum);
    }

    // Shuffle helper identical to your earlier version
    private static LabeledData shuffleWithLabels(DataSet concat, int[] labels, long seed) {
        int n = concat.getNumRows();
        List<Integer> perm = new ArrayList<>(n);
        for (int i = 0; i < n; i++) perm.add(i);
        Collections.shuffle(perm, new Random(seed));
        DataSet shuffled = concat.subsetRows(perm);
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = labels[perm.get(i)];

        LabeledData out = new LabeledData();
        out.data = shuffled;
        out.labels = y;
        return out;
    }

    /**
     * This test evaluates the performance of a causal unmixing algorithm in a semi-synthetic setting. The process
     * involves generating data from two regimes that share a common backbone structure with intentional structural
     * changes, simulating realistic marginal distributions, and testing the algorithm's ability to distinguish and
     * recover the regimes.
     *
     * <p>The test includes the following steps:</p>
     * <ol>
     *   <li>Generate a random directed acyclic graph (DAG) as the backbone structure,
     *       and simulate data with Laplace-distributed errors for heavy-tailed characteristics.</li>
     *   <li>Create two regimes:
     *     <ul>
     *       <li><b>Regime A:</b> Uses the original backbone DAG.</li>
     *       <li><b>Regime B:</b> Modifies the backbone by flipping the direction of some edges and
     *           scaling regression coefficients and noise variances.</li>
     *     </ul>
     *   </li>
     *   <li>Simulate data for both regimes and combine them into a single dataset.</li>
     *   <li>Shuffle the data and assign labels to indicate the regime of origin.</li>
     *   <li>Run the causal unmixing algorithm using an expectation-maximization (EM)-based approach.
     *       Evaluate its performance via metrics such as Adjusted Rand Index (ARI), adjacency F1,
     *       arrow direction F1, and structural Hamming distance (SHD).</li>
     *   <li>Perform a baseline test with K=1 (single cluster assumption) for diagnostics
     *       and compute the difference in Bayesian Information Criterion (BIC) values
     *       between K=2 and K=1 to determine cluster separation quality.</li>
     * </ol>
     */
    @Test
    public void phase3_semisynthetic() {
        // Use a backbone covariance from a single SEM sample, then inject shifts.
        int p = 15, n1 = 900, n2 = 900, flips = 10;
        long seed = 33;

        // Backbone DAG & sample (Laplace errors for heavier tails)
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph gBackbone = RandomGraph.randomGraph(vars, 0, 20, 100, 100, 100, false);

        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imBack = new SemIm(new SemPm(gBackbone), params);
        DataSet Dreal = imBack.simulateData(n1 + n2, false); // ârealisticâ marginal structure

        // Two regimes: (A) keep backbone; (B) flip edges & scale some parameters
        Graph gA = gBackbone.copy();
        Graph gB = copyWithFlippedDirections(gBackbone, flips, new Random(seed));
        SemIm imA = new SemIm(new SemPm(gA), params);
        SemIm imB = new SemIm(new SemPm(gB), params);

        double coefScale = 1.6, noiseScale = 1.8;
        for (Edge e : gB.getEdges()) {
            try {
                double b = imB.getEdgeCoef(e);
                imB.setEdgeCoef(e.getNode1(), e.getNode2(), coefScale * b);
            } catch (Exception ignore) {
            }
        }
        for (Node v : vars) imB.setErrVar(v, noiseScale * imB.getErrVar(v));

        DataSet dA = imA.simulateData(n1, false);
        DataSet dB = imB.simulateData(n2, false);
        DataSet concat = DataTransforms.concatenate(dA, dB);

        // Ground-truth labels before shuffle
        int[] lab = new int[n1 + n2];
        Arrays.fill(lab, 0, n1, 0);
        Arrays.fill(lab, n1, n1 + n2, 1);

        // Shuffle rows + labels together (local helper)
        LabeledData mixed = shuffleWithLabels(concat, lab, seed);

        // === Run the causal unmixer (EM-based) ===
        UnmixResult rEM = CausalUnmixer.getUnmixedResult(mixed.data, CausalUnmixer.defaults());

        Graph[] truth = new Graph[]{gA, gB};
        GraphMetrics gmEM = graphMetrics(truth, rEM.clusterGraphs);

        System.out.printf("\n=== Phase3 (semi-synth) ===%n");
        if (mixed.labels == null) {
            System.out.println("Labels were not supplied.");
        } else {
            System.out.printf("EM baseline:  ARI=%.3f  AdjF1=%.3f  ArrowF1=%.3f  SHD=%d%n", adjustedRandIndex(mixed.labels, rEM.labels), gmEM.adjF1, gmEM.arrowF1, gmEM.shd);
        }

        // === Optional: K=1 baseline for diagnostics (ÎBIC etc.) ===
        // Build a Config aligned with your Unmix default (or copy what Unmix uses)
        EmUnmix.Config cfg = new EmUnmix.Config();
        cfg.K = 2;
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 12;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        cfg.robustScaleResiduals = true;
        cfg.covType = GaussianMixtureEM.CovarianceType.FULL;
        cfg.emMaxIters = 200;
        cfg.kmeansRestarts = 10;

        // Define a regressor + searches compatible with your Unmix pipe
        LinearQRRegressor reg = new LinearQRRegressor().setRidgeLambda(1e-3);

        // Rerun EM with K=1 (copy cfg and set K=1)
        EmUnmix.Config cfgK1 = cfg.copy();
        cfgK1.K = 1;

        UnmixResult rK1 = EmUnmix.run(mixed.data, cfgK1, reg);

        // Compute ÎBIC = BIC(K=2) - BIC(K=1); negative favors K=2
        GaussianMixtureEM.Model m2 = rEM.gmmModel;
        GaussianMixtureEM.Model m1 = rK1.gmmModel;
        int n = mixed.data.getNumRows();
        double bic2 = m2.bic(n);
        double bic1 = m1.bic(n);
        double deltaBic = bic2 - bic1;

        System.out.printf("ÎBIC (K=2 vs K=1): %.1f (negative favors K=2)%n", deltaBic);
    }

    private record GraphMetrics(double adjF1, double arrowF1, int shd) {
    }

    private record Metrics(double adjF1, double arrowF1, int shd) {
    }


    // ---------- helpers ----------

    /**
     * Represents labeled data consisting of a dataset and corresponding labels. This class is designed to encapsulate a
     * dataset and an array of integer labels, where each label corresponds to a data point in the dataset.
     */
    public static class LabeledData {

        DataSet data;
        int[] labels;
        /**
         * Constructs an empty instance of the LabeledData class.
         * <p>
         * This default constructor initializes a new LabeledData object without assigning any dataset or labels. After
         * instantiation, the dataset and labels can be manually assigned as needed.
         */
        public LabeledData() {

        }
    }
}
