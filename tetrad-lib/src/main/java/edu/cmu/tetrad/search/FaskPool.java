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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orients a common adjacency graph over multiple datasets by pooling the FASK left-right
 * statistic across the datasets, rather than voting on per-dataset orientations as
 * {@link FaskVote} does.
 *
 * <p>The procedure has two stages:</p>
 *
 * <ol>
 *   <li>Obtain a common adjacency graph: by combining external graphs supplied by the
 *   caller, one per dataset, or from a {@link PooledAdjacencySearch} (IMaGES, pooled
 *   FAS, or one of the moral-graph-based methods designed to remain correct when the
 *   true graph is cyclic; see {@link PooledAdjacencySearch.Method}). Where that stage
 *   compels a direction, it is retained as orientation evidence; see below.</li>
 *   <li>For each adjacency X&mdash;Y, compute the signed FASK left-right statistic
 *   lr_k separately within each dataset k (on standardized columns, with FASK's
 *   skew-sign correction applied within each dataset, as
 *   {@link Fask#leftRightDiff(double[], double[], int)} does internally), and combine
 *   them as a weighted average, lr = &Sigma; w_k lr_k / &Sigma; w_k. The edge is
 *   oriented X&rarr;Y when lr &gt; 0 and Y&rarr;X when lr &lt; 0.</li>
 * </ol>
 *
 * <p>This is the meta-analytic fixed-effect analog of FASK-Vote: where a vote discards
 * the magnitude of each dataset's evidence, the pooled statistic retains it, so a
 * dataset in which the left-right statistic is decisive counts for more than one in
 * which it is a near coin flip. It is to FASK-Vote what the IMaGES averaged BIC score
 * is to voting over per-dataset graphs.</p>
 *
 * <p>Two weighting schemes are provided:</p>
 *
 * <ul>
 *   <li>{@link Weighting#N} (default): w_k = n_k, the sample size of dataset k. This is
 *   exact when the datasets are homogeneous in everything but sample size and requires
 *   no variance estimation.</li>
 *   <li>{@link Weighting#INVERSE_VARIANCE}: w_k = 1 / Var(lr_k), where Var(lr_k) is
 *   estimated by a within-dataset pairwise bootstrap. This is the standard fixed-effect
 *   meta-analysis estimator and downweights noisy datasets even when they are large.</li>
 * </ul>
 *
 * <p>Optionally, when the orientation alpha is set above zero, a bootstrap standard
 * error of the pooled statistic is computed and the edge is left undirected (an
 * abstention) unless the pooled statistic differs significantly from zero at that
 * level. With the default alpha of zero, every adjacency is oriented by the sign of
 * the pooled statistic, matching single-dataset FASK's behavior. A Cochran Q
 * heterogeneity statistic is recorded per edge whenever bootstrap variances are
 * available, so edges on which the datasets genuinely disagree in direction can be
 * inspected via {@link #getEdgeStats()}.</p>
 *
 * <p>Important: the datasets are never concatenated. Between-dataset differences in
 * location, scale, or skew would make the concatenated sample a mixture, and mixtures
 * can manufacture exactly the kind of asymmetric conditional-correlation structure the
 * left-right rules read as causal signal. Only the finished, within-dataset,
 * sign-corrected statistics are combined.</p>
 *
 * <p><b>Orientation evidence.</b> The graph-based stage does not only deliver
 * adjacencies: where it compels a direction, that direction is evidence, and the pooled
 * left-right statistic must beat it rather than simply replace it. Evidence comes from
 * one of two sources, never both:</p>
 *
 * <ul>
 *   <li><i>The adjacency stage.</i> With {@link PooledAdjacencySearch.Method#IMAGES}
 *   and {@link #setUseAdjacencyOrientations(boolean)} left at its default of true, the
 *   compelled edges of the IMaGES CPDAG&mdash;BOSS over the score averaged across the
 *   datasets&mdash;are kept as default orientations. Because IMaGES pools the score
 *   over all the datasets, its CPDAG is already a combination of them, and it counts as
 *   one unanimous evidence graph. The other adjacency methods return undirected graphs
 *   and so contribute no orientation evidence.</li>
 *   <li><i>External graphs.</i> {@link #setExternalGraphs(List)} takes one graph per
 *   dataset&mdash;each dataset's own search result&mdash;and combines them, which is
 *   the point: a single external graph fitted to one dataset, or to the datasets
 *   pooled, would impose one skeleton and one set of orientations on all of them and
 *   defeat the cross-dataset construction. The composite skeleton keeps a pair when it
 *   is adjacent in more than {@link #setExternalAdjacencyFraction(double)} of the
 *   graphs (default: a strict majority), and the compelled orientations are tallied
 *   per pair: the majority direction, if there is one, becomes the default, and a tie
 *   leaves the pair to the pooled statistic. When the adjacency stage is thereby
 *   skipped, {@link #setUseExternalOrientations(boolean)} set to false makes the graphs
 *   contribute adjacencies only.</li>
 * </ul>
 *
 * <p>Only compelled orientations act: a graph that is a legal DAG (e.g., BOSS run with
 * CPDAG output off, or a graph drawn by hand) is converted to its CPDAG first, so that
 * reversible edges do not masquerade as compelled, and reversible edges are oriented
 * from the pooled statistic exactly as if no evidence graph had been supplied.</p>
 *
 * <p>What it takes to overturn a default scales with the strength of the evidence for
 * it. In every case the pooled statistic must oppose the default and, when the
 * orientation alpha is above zero, be significant at that level; beyond that, if the
 * evidence is unanimous (no evidence graph compels the opposite direction), every
 * dataset's statistic must individually oppose the default as well (strict
 * cross-dataset sign consensus), whereas evidence that is itself divided yields to the
 * pooled statistic alone. This is a lexicographic override, not additive pooling: the
 * score-based orientation is the default, and the higher-moment statistic overrules it
 * only on a counter-signal at least as strong as the evidence it is displacing. Note
 * that with a single dataset the sign consensus is vacuous, so a positive orientation
 * alpha should be set to give the override a meaningful gate. The provenance of each
 * orientation, and the evidence counts behind it, are recorded in
 * {@link #getEdgeStats()}.</p>
 *
 * <p>Background knowledge is respected: a required or forbidden edge orients the pair
 * regardless of the pooled statistic. Two-cycle detection is off by default, following
 * FASK-Vote. When the two-cycle alpha is set above zero, the single-dataset FASK
 * two-cycle test is run separately within each dataset, and the pair is output as a
 * two-cycle only when EVERY dataset passes the test (unanimity). Caution: in a
 * resimulation-null calibration on real data, the two-cycle channel of the
 * closely-related single-dataset override procedure reproduced its detections under an
 * acyclic null, so detections from this channel should not be trusted without a
 * calibration of that kind on the data at hand.</p>
 *
 * @author josephramsey
 * @see FaskVote
 * @see Fask
 */
public class FaskPool {

    /**
     * The score wrapper used to construct the IMaGES score for the adjacency stage.
     */
    private final ScoreWrapper score;

    /**
     * The datasets over which the composite graph is constructed.
     */
    private final List<DataSet> dataSets;

    /**
     * Background knowledge containing forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional external graphs, one per dataset (a single graph is accepted and applies
     * to all datasets). When nonempty, the adjacency stage is skipped: the graphs are
     * combined into a composite skeleton by the adjacency fraction rule, and their
     * compelled orientations are tallied into orientation evidence.
     */
    private List<Graph> externalGraphs = new ArrayList<>();

    /**
     * Whether the external graphs' compelled orientations act as defaults that the
     * pooled statistic can overturn only on strong opposing evidence. Only consulted
     * when external graphs have been set. Default: true.
     */
    private boolean useExternalOrientations = true;

    /**
     * Whether the adjacency stage's compelled orientations (IMaGES only; the other
     * adjacency methods produce undirected graphs) act as defaults in the same way.
     * Only consulted when no external graphs have been set. Default: true.
     */
    private boolean useAdjacencyOrientations = true;

    /**
     * The fraction of the external graphs in which a pair must be adjacent for it to be
     * an adjacency of the composite skeleton: a pair is kept when its count exceeds
     * this fraction of the number of graphs. Zero takes the union, 0.5 (the default) a
     * strict majority, and 1.0 the intersection. Ignored when a single external graph,
     * or none, is supplied.
     */
    private double externalAdjacencyFraction = 0.5;

    /**
     * Alpha for the per-dataset two-cycle test. Zero (the default) disables two-cycle
     * detection; above zero, a pair is output as a two-cycle only when every dataset
     * passes the single-dataset FASK two-cycle test at this level.
     */
    private double twoCycleAlpha = 0.0;

    /**
     * The left-right rule used within each dataset.
     */
    private Fask.LeftRight leftRight = Fask.LeftRight.FASK2;

    /**
     * The weighting scheme for pooling the per-dataset statistics.
     */
    private Weighting weighting = Weighting.N;

    /**
     * The adjacency-stage configuration; see {@link PooledAdjacencySearch}.
     */
    private final PooledAdjacencySearch adjacencySearch;

    /**
     * Significance level for the orientation decision. Zero (the default) means every
     * adjacency is oriented by the sign of the pooled statistic; above zero, an edge is
     * left undirected unless the pooled statistic differs significantly from zero.
     */
    private double orientationAlpha = 0.0;

    /**
     * Number of bootstrap resamples per dataset used to estimate Var(lr_k). Only used
     * when the weighting is INVERSE_VARIANCE or the orientation alpha is above zero.
     */
    private int numBootstraps = 200;

    /**
     * Per-edge pooled statistics recorded during the last search.
     */
    private final Map<Edge, EdgeStat> edgeStats = new HashMap<>();

    /**
     * Constructs a pooled-FASK search from the supplied datasets and score wrapper.
     *
     * @param dataSets the datasets to search over
     * @param score    the score wrapper for the IMaGES adjacency stage
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the dataset list is empty
     */
    public FaskPool(List<DataSet> dataSets, ScoreWrapper score) {
        if (dataSets == null) {
            throw new NullPointerException("Dataset list must not be null.");
        }

        if (score == null) {
            throw new NullPointerException("Score wrapper must not be null.");
        }

        if (dataSets.isEmpty()) {
            throw new IllegalArgumentException("At least one dataset is required.");
        }

        this.dataSets = dataSets;
        this.score = score;
        this.adjacencySearch = new PooledAdjacencySearch(score);
    }

    /**
     * Runs the search and returns the composite graph.
     *
     * @param parameters the search parameters (passed to the IMaGES stage)
     * @return the composite graph
     * @throws InterruptedException if the underlying IMaGES search is interrupted
     */
    public Graph search(Parameters parameters) throws InterruptedException {
        this.edgeStats.clear();

        // Standardize each dataset once; both the IMaGES stage and the pooled
        // orientation stage use the standardized columns.
        List<DataSet> standardized = new ArrayList<>();
        for (DataSet dataSet : this.dataSets) {
            standardized.add(DataTransforms.standardizeData(dataSet));
        }

        // Column arrays per dataset, keyed by variable name so datasets need only agree
        // on names, not on Node identity or column order.
        List<Map<String, double[]>> columns = new ArrayList<>();
        for (DataSet dataSet : standardized) {
            Map<String, double[]> map = new HashMap<>();
            double[][] cols = dataSet.getDoubleData().transpose().toArray();
            List<Node> vars = dataSet.getVariables();
            for (int j = 0; j < vars.size(); j++) {
                map.put(vars.get(j).getName(), cols[j]);
            }
            columns.add(map);
        }

        Graph adjacency;

        // Orientation evidence, keyed by unordered variable-name pair: entry[0] counts
        // the evidence graphs compelling first->second (in lexicographic name order),
        // entry[1] those compelling second->first.
        Map<NamePair, int[]> evidence = new HashMap<>();
        int numEvidenceGraphs = 0;

        if (!this.externalGraphs.isEmpty()) {
            // Each dataset's own graph is reduced to its compelled orientations and the
            // graphs are then combined; no single dataset's graph is imposed on the rest.
            List<Graph> compelledGraphs = new ArrayList<>();
            for (Graph g : this.externalGraphs) compelledGraphs.add(compelledGraph(g));

            adjacency = combineAdjacencies(compelledGraphs);

            if (this.useExternalOrientations) {
                numEvidenceGraphs = compelledGraphs.size();
                tallyOrientations(compelledGraphs, evidence);
            }
        } else {
            this.adjacencySearch.setKnowledge(this.knowledge);
            Graph stage = this.adjacencySearch.searchWithOrientations(standardized, parameters);
            adjacency = GraphUtils.undirectedGraph(stage);

            if (this.useAdjacencyOrientations) {
                // IMaGES pools the score across the datasets, so its CPDAG is already a
                // combination of all of them and counts as one unanimous evidence graph.
                // The other adjacency methods return undirected graphs, which contribute
                // no orientation evidence.
                numEvidenceGraphs = 1;
                tallyOrientations(java.util.Collections.singletonList(compelledGraph(stage)), evidence);
            }
        }

        Graph result = new EdgeListGraph(this.dataSets.get(0).getVariables());

        int ruleIndex = this.leftRight.ordinal() + 1;
        boolean needVariances = this.weighting == Weighting.INVERSE_VARIANCE || this.orientationAlpha > 0;
        double zCutoff = this.orientationAlpha > 0
                ? edu.cmu.tetrad.util.StatUtils.getZForAlpha(this.orientationAlpha) : 0.0;
        double twoCycleCutoff = this.twoCycleAlpha > 0
                ? edu.cmu.tetrad.util.StatUtils.getZForAlpha(this.twoCycleAlpha) : Double.POSITIVE_INFINITY;

        for (Edge edge : adjacency.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node rx = result.getNode(x.getName());
            Node ry = result.getNode(y.getName());

            if (knowledgeOrients(rx, ry)) {
                result.addDirectedEdge(rx, ry);
                continue;
            } else if (knowledgeOrients(ry, rx)) {
                result.addDirectedEdge(ry, rx);
                continue;
            }

            int m = this.dataSets.size();
            double[] lr = new double[m];
            double[] var = new double[m];
            double[] w = new double[m];

            for (int k = 0; k < m; k++) {
                double[] xk = columns.get(k).get(x.getName());
                double[] yk = columns.get(k).get(y.getName());

                if (xk == null || yk == null) {
                    throw new IllegalArgumentException("Variable missing from dataset "
                            + (k + 1) + ": " + (xk == null ? x.getName() : y.getName()));
                }

                // Skew-sign correction is applied inside leftRightDiff, per dataset,
                // which is essential: the correction is a per-dataset gauge fix and
                // must precede pooling.
                lr[k] = Fask.leftRightDiff(xk, yk, ruleIndex);

                if (needVariances) {
                    var[k] = bootstrapVariance(xk, yk, ruleIndex);
                }
            }

            double sumW = 0.0;
            for (int k = 0; k < m; k++) {
                if (this.weighting == Weighting.INVERSE_VARIANCE) {
                    // Guard against degenerate bootstrap variances.
                    w[k] = 1.0 / Math.max(var[k], 1e-12);
                } else {
                    w[k] = columns.get(k).get(x.getName()).length;
                }
                sumW += w[k];
            }

            double pooled = 0.0;
            for (int k = 0; k < m; k++) pooled += w[k] * lr[k];
            pooled /= sumW;

            // Standard error of the pooled statistic and Cochran Q, when variances exist.
            double se = Double.NaN;
            double z = Double.NaN;
            double q = Double.NaN;

            if (needVariances) {
                double varPooled = 0.0;
                for (int k = 0; k < m; k++) varPooled += w[k] * w[k] * var[k];
                varPooled /= (sumW * sumW);
                se = Math.sqrt(varPooled);
                z = pooled / se;

                q = 0.0;
                for (int k = 0; k < m; k++) {
                    double d = lr[k] - pooled;
                    q += d * d / Math.max(var[k], 1e-12);
                }
            }

            // Combined orientation evidence for this pair: how many evidence graphs
            // compel x->y, and how many compel y->x. The majority direction, if there
            // is one, is the default orientation; a tie (including none at all, or a
            // pair reversible in every CPDAG) leaves the pair to the pooled statistic.
            int forDefault = 0;
            int againstDefault = 0;
            int compelled = 0;

            if (numEvidenceGraphs > 0) {
                int[] tally = evidence.get(NamePair.of(x.getName(), y.getName()));

                if (tally != null) {
                    boolean xFirst = x.getName().compareTo(y.getName()) < 0;
                    int forward = xFirst ? tally[0] : tally[1];
                    int backward = xFirst ? tally[1] : tally[0];

                    if (forward > backward) {
                        compelled = +1;
                        forDefault = forward;
                        againstDefault = backward;
                    } else if (backward > forward) {
                        compelled = -1;
                        forDefault = backward;
                        againstDefault = forward;
                    }
                }
            }

            // Two-cycle stage: only when enabled, and only on unanimity across datasets.
            if (this.twoCycleAlpha > 0 && isPooledTwoCycle(adjacency, columns, x, y, twoCycleCutoff)) {
                Edge e1 = Edges.directedEdge(rx, ry);
                Edge e2 = Edges.directedEdge(ry, rx);
                result.addEdge(e1);
                result.addEdge(e2);
                EdgeStat stat = new EdgeStat(pooled, z, q, m, forDefault, againstDefault,
                        Origin.TWO_CYCLE);
                this.edgeStats.put(e1, stat);
                this.edgeStats.put(e2, stat);
                continue;
            }

            Edge added;
            Origin origin;

            if (compelled != 0) {
                boolean opposes = compelled > 0 ? pooled < 0 : pooled > 0;

                // Strict cross-dataset sign consensus: every dataset's statistic must
                // individually oppose the compelled orientation.
                boolean consensus = true;
                for (int k = 0; k < m; k++) {
                    if (compelled > 0 ? lr[k] >= 0 : lr[k] <= 0) {
                        consensus = false;
                        break;
                    }
                }

                boolean significant = this.orientationAlpha == 0 || Math.abs(z) >= zCutoff;

                // The counter-signal required scales with the strength of the evidence.
                // Unanimous evidence (no evidence graph compels the other direction)
                // yields only to unanimous per-dataset opposition; evidence that is
                // itself divided yields to the pooled statistic alone.
                boolean unanimousEvidence = againstDefault == 0;
                boolean overturned = opposes && significant && (!unanimousEvidence || consensus);

                if (overturned) {
                    if (compelled > 0) result.addDirectedEdge(ry, rx);
                    else result.addDirectedEdge(rx, ry);
                    origin = Origin.OVERRIDE;
                } else {
                    if (compelled > 0) result.addDirectedEdge(rx, ry);
                    else result.addDirectedEdge(ry, rx);
                    origin = Origin.EVIDENCE_DEFAULT;
                }

                added = result.getEdge(rx, ry);
            } else if (this.orientationAlpha > 0 && Math.abs(z) < zCutoff) {
                result.addUndirectedEdge(rx, ry);
                added = result.getEdge(rx, ry);
                origin = Origin.POOLED;
            } else if (pooled > 0) {
                result.addDirectedEdge(rx, ry);
                added = result.getEdge(rx, ry);
                origin = Origin.POOLED;
            } else {
                result.addDirectedEdge(ry, rx);
                added = result.getEdge(rx, ry);
                origin = Origin.POOLED;
            }

            this.edgeStats.put(added, new EdgeStat(pooled, z, q, m, forDefault, againstDefault, origin));
        }

        return result;
    }

    /**
     * Reduces a graph to the orientations it actually compels. A legal DAG (e.g., BOSS
     * run with CPDAG output off, or a graph drawn by hand) is converted to its CPDAG
     * first, so that reversible edges do not masquerade as compelled; anything else,
     * including a CPDAG or a cyclic graph, is returned as is.
     *
     * @param graph the graph to reduce
     * @return the graph whose directed edges are the compelled orientations
     */
    private Graph compelledGraph(Graph graph) {
        return graph.paths().isLegalDag() ? GraphTransforms.dagToCpdag(graph) : graph;
    }

    /**
     * Combines the skeletons of the per-dataset graphs into one composite skeleton: a
     * pair is an adjacency when the number of graphs in which it is adjacent exceeds
     * {@link #setExternalAdjacencyFraction(double)} times the number of graphs (or, at
     * a fraction of 1.0, when it is adjacent in all of them). Pairs involving variables
     * absent from the datasets are dropped.
     *
     * @param graphs the per-dataset graphs
     * @return an undirected graph over the dataset variables
     */
    private Graph combineAdjacencies(List<Graph> graphs) {
        List<Node> vars = this.dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(vars);

        Map<NamePair, Integer> counts = new HashMap<>();

        for (Graph graph : graphs) {
            Set<NamePair> seen = new HashSet<>();

            for (Edge edge : graph.getEdges()) {
                // A two-cycle contributes two edges between the same pair; count the
                // pair once per graph.
                NamePair pair = NamePair.of(edge.getNode1().getName(), edge.getNode2().getName());
                if (seen.add(pair)) counts.merge(pair, 1, Integer::sum);
            }
        }

        int m = graphs.size();

        for (Map.Entry<NamePair, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();

            boolean keep = this.externalAdjacencyFraction >= 1.0
                    ? count == m : count > this.externalAdjacencyFraction * m;

            if (!keep) continue;

            Node a = out.getNode(entry.getKey().first());
            Node b = out.getNode(entry.getKey().second());

            if (a != null && b != null) out.addUndirectedEdge(a, b);
        }

        return out;
    }

    /**
     * Tallies the compelled orientations of the given evidence graphs by unordered
     * variable-name pair. Entry [0] counts the graphs compelling first&rarr;second in
     * lexicographic name order, entry [1] those compelling second&rarr;first; a pair
     * output as a two-cycle in a graph contributes to both, and so cancels.
     *
     * @param graphs   the evidence graphs, already reduced to compelled orientations
     * @param evidence the tally to add to
     */
    private void tallyOrientations(List<Graph> graphs, Map<NamePair, int[]> evidence) {
        for (Graph graph : graphs) {
            for (Edge edge : graph.getEdges()) {
                if (!Edges.isDirectedEdge(edge)) continue;

                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();
                Node from = edge.pointsTowards(node2) ? node1 : node2;

                NamePair pair = NamePair.of(node1.getName(), node2.getName());
                int[] tally = evidence.computeIfAbsent(pair, k -> new int[2]);

                if (from.getName().equals(pair.first())) tally[0]++;
                else tally[1]++;
            }
        }
    }

    /**
     * Estimates the sampling variance of the left-right statistic within one dataset by
     * a pairwise bootstrap: rows of (x, y) are resampled jointly, each resample is
     * re-standardized, and the statistic is recomputed.
     *
     * @param x         standardized series for X
     * @param y         standardized series for Y
     * @param ruleIndex the left-right rule index (1..5)
     * @return the bootstrap variance of the statistic
     */
    private double bootstrapVariance(double[] x, double[] y, int ruleIndex) {
        int n = x.length;
        RandomUtil random = RandomUtil.getInstance();

        double[] stats = new double[this.numBootstraps];
        double[] bx = new double[n];
        double[] by = new double[n];

        for (int b = 0; b < this.numBootstraps; b++) {
            for (int i = 0; i < n; i++) {
                int r = random.nextInt(n);
                bx[i] = x[r];
                by[i] = y[r];
            }

            double[] sx = bx.clone();
            double[] sy = by.clone();
            Fask.standardize(sx);
            Fask.standardize(sy);

            stats[b] = Fask.leftRightDiff(sx, sy, ruleIndex);
        }

        double mean = 0.0;
        for (double s : stats) mean += s;
        mean /= this.numBootstraps;

        double v = 0.0;
        for (double s : stats) {
            double d = s - mean;
            v += d * d;
        }

        return v / (this.numBootstraps - 1);
    }

    /**
     * Runs the single-dataset FASK two-cycle test separately within each dataset and
     * returns true only when every dataset passes it (unanimity). Conditioning
     * candidates are the adjacents of X and Y in the common adjacency graph, other
     * than X and Y themselves.
     */
    private boolean isPooledTwoCycle(Graph adjacency, List<Map<String, double[]>> columns,
                                     Node x, Node y, double cutoff) {
        Set<Node> pool = new HashSet<>(adjacency.getAdjacentNodes(x));
        pool.addAll(adjacency.getAdjacentNodes(y));
        pool.remove(x);
        pool.remove(y);

        if (pool.isEmpty()) return false;

        List<String> candNames = new ArrayList<>();
        for (Node node : pool) candNames.add(node.getName());

        for (Map<String, double[]> cols : columns) {
            double[] xk = cols.get(x.getName());
            double[] yk = cols.get(y.getName());

            double[][] candCols = new double[candNames.size()][];
            for (int i = 0; i < candNames.size(); i++) {
                double[] c = cols.get(candNames.get(i));

                if (c == null) {
                    throw new IllegalArgumentException("Variable missing from a dataset: "
                            + candNames.get(i));
                }

                candCols[i] = c;
            }

            if (!Fask.twoCycleTest(xk, yk, candCols, cutoff)) {
                return false;
            }
        }

        return true;
    }

    private boolean knowledgeOrients(Node left, Node right) {
        return this.knowledge.isForbidden(right.getName(), left.getName())
                || this.knowledge.isRequired(left.getName(), right.getName());
    }

    /**
     * Sets background knowledge for the search. A defensive copy is stored.
     *
     * @param knowledge knowledge containing forbidden and required edges
     * @throws NullPointerException if the supplied knowledge is null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets a single external graph, applied to every dataset. Equivalent to
     * {@link #setExternalGraphs(List)} with a one-element list; since one graph cannot
     * disagree with itself, its compelled orientations count as unanimous evidence, so
     * a positive orientation alpha should be set to give the override a meaningful
     * gate. Prefer supplying one graph per dataset.
     *
     * @param externalGraph the external graph, or null to use the adjacency search
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraphs = externalGraph == null
                ? new ArrayList<>() : new ArrayList<>(java.util.Collections.singletonList(externalGraph));
    }

    /**
     * Sets the external graphs, one per dataset, in the order of the datasets. When
     * nonempty, the adjacency stage is skipped and these graphs are combined: a pair is
     * an adjacency of the composite skeleton when it is adjacent in more than
     * {@link #setExternalAdjacencyFraction(double)} of them, and their compelled
     * orientations are tallied into orientation evidence, with the majority direction
     * (if any) becoming the default orientation of that pair.
     *
     * <p>The list need not have one entry per dataset&mdash;any number of graphs over
     * the same variables is combined the same way&mdash;but supplying each dataset's
     * own graph is the point: it is what makes the evidence cross-dataset, so that
     * unanimity among the graphs means something.</p>
     *
     * @param externalGraphs the external graphs, or null or empty to use the adjacency
     *                       search
     */
    public void setExternalGraphs(List<Graph> externalGraphs) {
        this.externalGraphs = externalGraphs == null ? new ArrayList<>() : new ArrayList<>(externalGraphs);

        for (Graph graph : this.externalGraphs) {
            if (graph == null) {
                throw new NullPointerException("External graph list contains a null graph.");
            }
        }
    }

    /**
     * Sets whether the external graphs' compelled orientations act as default
     * orientations that the pooled statistic can overturn only on strong opposing
     * evidence (see the class Javadoc). When false, the external graphs contribute
     * adjacencies only. Only consulted when external graphs have been set.
     * Default: true.
     *
     * @param useExternalOrientations true to use the external orientations as defaults
     */
    public void setUseExternalOrientations(boolean useExternalOrientations) {
        this.useExternalOrientations = useExternalOrientations;
    }

    /**
     * Sets whether the adjacency stage's compelled orientations act as default
     * orientations in the same way. Only {@link PooledAdjacencySearch.Method#IMAGES}
     * produces any: the CPDAG of a BOSS search over the score averaged across the
     * datasets, whose compelled orientations are score-based, cross-dataset evidence
     * about direction. When false, only the adjacencies of that CPDAG are used and
     * every edge is oriented from the pooled statistic. Only consulted when no external
     * graphs have been set. Default: true.
     *
     * @param useAdjacencyOrientations true to use the adjacency stage's orientations as
     *                                 defaults
     */
    public void setUseAdjacencyOrientations(boolean useAdjacencyOrientations) {
        this.useAdjacencyOrientations = useAdjacencyOrientations;
    }

    /**
     * Sets the fraction of the external graphs in which a pair must be adjacent for it
     * to be an adjacency of the composite skeleton: the pair is kept when its count
     * exceeds this fraction of the number of graphs. Zero takes the union of the
     * skeletons, 0.5 (the default) a strict majority, and 1.0 the intersection.
     * Ignored unless more than one external graph is supplied.
     *
     * @param externalAdjacencyFraction the fraction, in [0, 1]
     */
    public void setExternalAdjacencyFraction(double externalAdjacencyFraction) {
        if (externalAdjacencyFraction < 0.0 || externalAdjacencyFraction > 1.0) {
            throw new IllegalArgumentException("Fraction out of range: " + externalAdjacencyFraction);
        }
        this.externalAdjacencyFraction = externalAdjacencyFraction;
    }

    /**
     * Sets the alpha for the per-dataset two-cycle test. Zero (the default) disables
     * two-cycle detection; above zero, a pair is output as a two-cycle only when every
     * dataset passes the single-dataset FASK two-cycle test at this level. See the
     * class Javadoc for a caution about this channel.
     *
     * @param twoCycleAlpha the significance level, in [0, 1]
     */
    public void setTwoCycleAlpha(double twoCycleAlpha) {
        if (twoCycleAlpha < 0.0 || twoCycleAlpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + twoCycleAlpha);
        }
        this.twoCycleAlpha = twoCycleAlpha;
    }

    /**
     * Sets the left-right rule used within each dataset. Default: FASK2.
     *
     * @param leftRight the left-right rule
     */
    public void setLeftRight(Fask.LeftRight leftRight) {
        this.leftRight = leftRight;
    }

    /**
     * Sets the weighting scheme for pooling. Default: N (sample-size weights).
     *
     * @param weighting the weighting scheme
     */
    public void setWeighting(Weighting weighting) {
        this.weighting = weighting;
    }

    /**
     * Sets how the common adjacency structure is obtained when no external graph is
     * set. Default: IMAGES. Ignored when an external graph has been set.
     *
     * @param method the adjacency method
     */
    public void setAdjacencyMethod(PooledAdjacencySearch.Method method) {
        this.adjacencySearch.setMethod(method);
    }

    /**
     * Sets the alpha level for the FAS-style adjacency tests. Default: 0.05.
     *
     * @param fasAlpha the alpha level, in (0, 1)
     */
    public void setFasAlpha(double fasAlpha) {
        this.adjacencySearch.setFasAlpha(fasAlpha);
    }

    /**
     * Sets the depth of the FAS-style adjacency searches (-1 for unlimited; capped at
     * 4 in MG_FAS). Default: -1.
     *
     * @param fasDepth the depth
     */
    public void setFasDepth(int fasDepth) {
        this.adjacencySearch.setFasDepth(fasDepth);
    }

    /**
     * Sets the threshold on the pooled absolute LiNG B-hat entries above which a moral
     * pair is kept as an adjacency. Default: 0.1.
     *
     * @param lingThreshold the threshold, nonnegative
     */
    public void setLingThreshold(double lingThreshold) {
        this.adjacencySearch.setLingThreshold(lingThreshold);
    }

    /**
     * Sets the FastICA maximum iterations for the LiNG adjacency stage. Default: 2000.
     *
     * @param fastIcaMaxIter maximum iterations, positive
     */
    public void setFastIcaMaxIter(int fastIcaMaxIter) {
        this.adjacencySearch.setFastIcaMaxIter(fastIcaMaxIter);
    }

    /**
     * Sets the FastICA convergence tolerance for the LiNG adjacency stage. Default:
     * 1e-6.
     *
     * @param fastIcaTolerance the tolerance, positive
     */
    public void setFastIcaTolerance(double fastIcaTolerance) {
        this.adjacencySearch.setFastIcaTolerance(fastIcaTolerance);
    }

    /**
     * Sets the FastICA tanh nonlinearity parameter for the LiNG adjacency stage.
     * Default: 1.1.
     *
     * @param fastIcaA the parameter
     */
    public void setFastIcaA(double fastIcaA) {
        this.adjacencySearch.setFastIcaA(fastIcaA);
    }

    /**
     * Sets the significance level for the orientation decision. Zero (the default)
     * orients every adjacency by the sign of the pooled statistic; above zero, an edge
     * is left undirected unless the pooled statistic differs significantly from zero at
     * this level (two-sided, bootstrap standard error).
     *
     * @param orientationAlpha the significance level, in [0, 1]
     */
    public void setOrientationAlpha(double orientationAlpha) {
        if (orientationAlpha < 0.0 || orientationAlpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + orientationAlpha);
        }
        this.orientationAlpha = orientationAlpha;
    }

    /**
     * Sets the number of bootstrap resamples per dataset used to estimate Var(lr_k).
     * Default: 200.
     *
     * @param numBootstraps the number of resamples, at least 2
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 2) {
            throw new IllegalArgumentException("At least 2 bootstraps required: " + numBootstraps);
        }
        this.numBootstraps = numBootstraps;
    }

    /**
     * Returns the per-edge pooled statistics recorded during the last search, keyed by
     * the edge added to the result graph. The z and Q entries are NaN when bootstrap
     * variances were not computed (n-weighting with orientation alpha zero).
     *
     * @return an unmodifiable view of the per-edge statistics
     */
    public Map<Edge, EdgeStat> getEdgeStats() {
        return java.util.Collections.unmodifiableMap(this.edgeStats);
    }

    /**
     * An unordered pair of variable names, in lexicographic order, used to key the
     * orientation evidence so that graphs need only agree on variable names.
     *
     * @param first  the lexicographically earlier name
     * @param second the lexicographically later name
     */
    private record NamePair(String first, String second) {
        private static NamePair of(String name1, String name2) {
            return name1.compareTo(name2) < 0 ? new NamePair(name1, name2) : new NamePair(name2, name1);
        }
    }

    /**
     * The weighting scheme for pooling per-dataset left-right statistics.
     */
    public enum Weighting {
        /**
         * Sample-size weights, w_k = n_k. Exact when datasets differ only in size.
         */
        N,
        /**
         * Inverse bootstrap-variance weights, w_k = 1 / Var(lr_k). The fixed-effect
         * meta-analysis estimator; downweights noisy datasets whatever their size.
         */
        INVERSE_VARIANCE
    }

    /**
     * The provenance of an orientation decision, recorded per edge in
     * {@link #getEdgeStats()}.
     */
    public enum Origin {
        /**
         * Oriented (or left undirected by abstention) from the pooled left-right
         * statistic alone.
         */
        POOLED,
        /**
         * The majority compelled orientation of the evidence graphs was kept as the
         * default.
         */
        EVIDENCE_DEFAULT,
        /**
         * The default orientation from the evidence graphs was overturned by the pooled
         * left-right statistic.
         */
        OVERRIDE,
        /**
         * Output as a two-cycle on unanimous per-dataset two-cycle tests.
         */
        TWO_CYCLE
    }

    /**
     * Pooled statistics for one edge.
     *
     * @param pooledLr the pooled left-right statistic (positive favored node1 to node2
     *                 at decision time)
     * @param z        the pooled statistic divided by its bootstrap standard error, or
     *                 NaN if variances were not computed
     * @param q        the Cochran Q heterogeneity statistic across datasets, or NaN if
     *                 variances were not computed; large values flag edges on which the
     *                 datasets genuinely disagree
     * @param numDataSets the number of datasets pooled
     * @param evidenceFor     the number of evidence graphs compelling the default
     *                        orientation of this pair, or zero if there was none
     * @param evidenceAgainst the number of evidence graphs compelling the opposite
     *                        orientation; zero means the evidence was unanimous, and
     *                        the override then required unanimous per-dataset
     *                        opposition
     * @param origin   the provenance of the orientation decision for this edge
     */
    public record EdgeStat(double pooledLr, double z, double q, int numDataSets, int evidenceFor,
                           int evidenceAgainst, Origin origin) {
    }
}
