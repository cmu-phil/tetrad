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

package edu.cmu.tetrad.search.lcdnod;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Phase 2 of LCD-NOD (Dong, Dai, Ng, Spirtes, and Zhang, "Identifying Partially Observed Causal Models from
 * Heterogeneous/Nonstationary Data", ICML 2026): given a one-factor measurement model (disjoint pure clusters of
 * indicators, one latent per cluster, as found by FOFC run in stratified mode) and a domain column, determines which
 * latents and which indicators have causal mechanisms that change across domains, and returns the augmented graph with
 * the domain variable pointing into the changing ones.
 * <p>
 * The procedure, following the paper's Section 4.2, Theorem 8, and the implementation notes of its Section C.3:
 * <ol>
 * <li>Within each domain, fit the one-factor model by maximum likelihood ({@link OneFactorFit}), giving loadings,
 * residual variances, and the latent correlation matrix, with each latent scaled to unit variance.</li>
 * <li>An indicator changes if its residual variance changes across domains; the residual variances are identified
 * outright, and the paper's Assumption A2 ties a changed measurement to a changed residual variance.</li>
 * <li>The scale of each latent is arbitrary within a domain and is fixed by an anchor indicator assumed to have an
 * invariant loading (Assumption A1). By A1 and A2 together an indicator whose residual variance is invariant is an
 * unchanged measurement and so a valid anchor, so the anchor of a latent is taken among its indicators that were not
 * found to change (the most reliable of them, by the ratio of squared loading to residual variance); if none is
 * invariant every indicator is a candidate and the report says so. Multiplying a latent's conditional variance given
 * other latents by the anchor's squared loading, and a regression coefficient of latent i on latent j by the ratio
 * of the two anchors' loadings, puts these quantities in units that are the same in every domain.</li>
 * <li>The structure among the latents is learned by BOSS on the per-domain latent covariances with their SEM BIC
 * scores pooled IMaGES-style (shared structure, free parameters in each domain), giving a CPDAG.</li>
 * <li>Following Section C.3, each DAG in that CPDAG's equivalence class is considered in turn: in it, a latent
 * changes if its conditional distribution given its parents changes across domains (the calibrated conditional
 * variance, or any calibrated coefficient on a parent), and the DAGs with the fewest changing latents are the
 * minimal explanations of the data. A latent is reported as changing if it changes in any minimal explanation; when
 * the minimal explanations disagree, the report lists them, since second-order information cannot then tell which
 * latent changed. If the equivalence class is too large to enumerate (more than {@link #MAX_UNDIRECTED_EDGES}
 * undirected edges), or the latent structure is not learned, Theorem 8's rule is used instead: a latent changes if
 * its calibrated conditional distribution given every subset of the other latents changes.</li>
 * </ol>
 * The paper does not say how a finite-sample "change across domains" is decided. Two rules are offered. The default
 * is a bootstrap homogeneity test: rows are resampled within each domain and the model refit, giving a standard error
 * for each quantity in each domain (on the log scale for variances), and Cochran's Q statistic for equality of the
 * per-domain values is referred to a chi-square distribution with one degree of freedom fewer than the number of
 * domains. A conditional distribution with m coefficients is declared to change when any of its m + 1 components
 * has a p-value below alpha / (m + 1), and its reported p-value is the smallest Bonferroni-adjusted one. The
 * alternative, used when the number of bootstraps is zero, is a ratio threshold on the variances alone: a variance
 * changes when its largest per-domain value exceeds its smallest by more than the given factor; coefficients are
 * not examined under this rule.
 * <p>
 * Limitations. Phase 2 is specific to one-factor models (not RLCD's general latent structures). A change that leaves
 * every second-order conditional quantity the same cannot be seen, and a change in a latent's mechanism can be
 * explained equally well, by count, as a change in a neighbor's when the equivalence class leaves their edge
 * unoriented; the report shows such ties. A latent uncorrelated with all others has an unidentified scale, as the
 * paper notes. Domains with fewer rows than indicators plus two are dropped with a note, and at least two domains
 * must remain. Under the fallback rule the subsets of the other latents are enumerated exhaustively, so at most
 * {@link #MAX_LATENTS} latents are allowed. Mean shifts across domains are irrelevant, since each domain's
 * covariance is computed about its own mean.
 *
 * @author josephramsey
 */
public final class LcdNodPhase2 {

    /**
     * The largest number of latents for which the fallback rule's subsets are enumerated.
     */
    public static final int MAX_LATENTS = 16;

    /**
     * The largest number of undirected edges in the latent CPDAG for which its DAGs are enumerated.
     */
    public static final int MAX_UNDIRECTED_EDGES = 14;

    private final DataSet dataSet;
    private final String domainColumn;
    private final List<List<Integer>> blocks;
    private final List<Node> latents;

    private double alpha = 0.05;
    private int numBootstraps = 100;
    private double changeRatio = 1.5;
    private double penaltyDiscount = 2.0;
    private long seed = -1;
    private boolean verbose = false;
    private boolean learnLatentStructure = true;
    private Graph suppliedLatentStructure = null;

    private Result result;

    /**
     * Constructs the procedure.
     *
     * @param dataSet      the data set, holding the indicators and the domain column.
     * @param domainColumn the name of the domain column: a discrete column, or a continuous column whose values are
     *                     whole numbers.
     * @param blocks       for each latent, the column indices (into the data set) of its pure indicators.
     * @param latents      the latent node for each block, in the same order.
     */
    public LcdNodPhase2(DataSet dataSet, String domainColumn, List<List<Integer>> blocks, List<Node> latents) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.domainColumn = Objects.requireNonNull(domainColumn, "domainColumn");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.latents = Objects.requireNonNull(latents, "latents");
        if (blocks.size() != latents.size()) {
            throw new IllegalArgumentException("There are " + blocks.size() + " blocks but " + latents.size()
                                               + " latent nodes.");
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("No blocks were given.");
        }
        if (blocks.size() > MAX_LATENTS) {
            throw new IllegalArgumentException("LCD-NOD Phase 2 enumerates the subsets of the latents and is limited "
                                               + "to " + MAX_LATENTS + " latents; " + blocks.size() + " were given.");
        }
        Node domain = dataSet.getVariable(domainColumn);
        if (domain == null) {
            throw new IllegalArgumentException("No column named " + domainColumn + " in the data set.");
        }
        int dcol = dataSet.getVariables().indexOf(domain);
        Set<Integer> seen = new HashSet<>();
        for (List<Integer> block : blocks) {
            if (block.isEmpty()) throw new IllegalArgumentException("A block is empty.");
            for (int col : block) {
                if (col < 0 || col >= dataSet.getNumColumns()) {
                    throw new IllegalArgumentException("Block column index out of range: " + col);
                }
                if (col == dcol) {
                    throw new IllegalArgumentException("The domain column " + domainColumn + " is in a block.");
                }
                if (!seen.add(col)) {
                    throw new IllegalArgumentException("Column " + dataSet.getVariable(col).getName()
                                                       + " is in more than one block.");
                }
                if (dataSet.getVariable(col) instanceof DiscreteVariable) {
                    throw new IllegalArgumentException("Indicator " + dataSet.getVariable(col).getName()
                                                       + " is discrete; the indicators must be continuous.");
                }
            }
        }
    }

    /**
     * Sets the significance level for the bootstrap homogeneity test.
     *
     * @param alpha the level; default 0.05.
     */
    public void setAlpha(double alpha) {
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        this.alpha = alpha;
    }

    /**
     * Sets the number of within-domain bootstrap resamples; zero selects the ratio-threshold rule instead.
     *
     * @param numBootstraps the number; default 100.
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 0) throw new IllegalArgumentException("numBootstraps must be >= 0: " + numBootstraps);
        this.numBootstraps = numBootstraps;
    }

    /**
     * Sets the ratio threshold used when the number of bootstraps is zero: a variance changes when its largest
     * per-domain value exceeds its smallest by more than this factor.
     *
     * @param changeRatio the factor, greater than 1; default 1.5.
     */
    public void setChangeRatio(double changeRatio) {
        if (!(changeRatio > 1)) throw new IllegalArgumentException("changeRatio must be > 1: " + changeRatio);
        this.changeRatio = changeRatio;
    }

    /**
     * Sets the penalty discount of the SEM BIC scores used for the structure among the latents.
     *
     * @param penaltyDiscount the penalty discount; default 2.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the seed of the bootstrap resampling; a negative seed draws one from the clock.
     *
     * @param seed the seed; default -1.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets whether to log progress.
     *
     * @param verbose true to log.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether to learn the structure among the latents (BOSS on the per-domain latent covariances, pooled). When
     * false the latents are left unconnected in the output and the change decisions use the fallback rule over all
     * subsets of the other latents.
     *
     * @param learnLatentStructure true to learn; default true.
     */
    public void setLearnLatentStructure(boolean learnLatentStructure) {
        this.learnLatentStructure = learnLatentStructure;
    }

    /**
     * Supplies the structure among the latents (from RLCD, say) in place of learning it. Only the edges between the
     * latent nodes given to the constructor, matched by name, are used; a directed edge is taken as oriented and any
     * other edge as unoriented, so the DAGs enumerated are the orientations of the unoriented edges that are acyclic
     * and add no unshielded collider. Null (the default) restores learning.
     *
     * @param latentStructure a graph containing the latents, or null.
     */
    public void setLatentStructure(Graph latentStructure) {
        this.suppliedLatentStructure = latentStructure;
    }

    /**
     * The detailed result of the last search, or null before any search.
     *
     * @return the result.
     */
    public Result getResult() {
        return result;
    }

    /**
     * A plain-text report of the last search: per-domain sample sizes, per-domain estimates, and each decision with
     * its evidence.
     *
     * @return the report, or an empty string before any search.
     */
    public String getReport() {
        return result == null ? "" : result.report;
    }

    /**
     * Runs the procedure.
     *
     * @return the augmented graph: measurement edges from each latent to its indicators, the learned structure among
     * the latents, and directed edges from the domain variable to each changing latent and each changing indicator.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
        Node domainNode = dataSet.getVariable(domainColumn);
        int dcol = dataSet.getVariables().indexOf(domainNode);

        // Indicator columns in block order, and the blocks in fit index space.
        List<Integer> cols = new ArrayList<>();
        int[][] fitBlocks = new int[blocks.size()][];
        for (int i = 0; i < blocks.size(); i++) {
            fitBlocks[i] = new int[blocks.get(i).size()];
            for (int j = 0; j < blocks.get(i).size(); j++) {
                fitBlocks[i][j] = cols.size();
                cols.add(blocks.get(i).get(j));
            }
        }
        int p = cols.size();
        int k = blocks.size();
        int[] blockOf = new int[p];
        for (int i = 0; i < k; i++) for (int a : fitBlocks[i]) blockOf[a] = i;

        // Rows by domain value, in sorted order of the value.
        Map<Integer, List<Integer>> rowsByValue = groupRows(dcol);
        List<Integer> values = new ArrayList<>(rowsByValue.keySet());
        Collections.sort(values);

        List<String> labels = new ArrayList<>();
        List<double[][]> domainData = new ArrayList<>();
        List<String> droppedDomains = new ArrayList<>();
        for (int v : values) {
            List<Integer> rows = rowsByValue.get(v);
            String label = domainNode instanceof DiscreteVariable dv ? dv.getCategory(v) : Integer.toString(v);
            if (rows.size() < p + 2) {
                droppedDomains.add(label + " (" + rows.size() + " rows)");
                continue;
            }
            double[][] d = new double[rows.size()][p];
            for (int r = 0; r < rows.size(); r++) {
                int row = rows.get(r);
                for (int c = 0; c < p; c++) d[r][c] = dataSet.getDouble(row, cols.get(c));
            }
            labels.add(label);
            domainData.add(d);
        }
        int numDomains = labels.size();
        if (numDomains < 2) {
            throw new IllegalArgumentException("LCD-NOD Phase 2 needs at least two domains with more rows than "
                                               + "indicators; " + numDomains + " usable domain(s) found"
                                               + (droppedDomains.isEmpty() ? "." : "; dropped: " + droppedDomains));
        }
        if (!droppedDomains.isEmpty()) {
            TetradLogger.getInstance().log("LCD-NOD Phase 2: dropping domains with fewer than " + (p + 2)
                                           + " rows: " + droppedDomains);
        }

        // Per-domain fits, replicate 0 = the data, replicates 1..B = bootstrap resamples.
        int B = numBootstraps;
        double[][][] loadings = new double[B + 1][numDomains][];
        double[][][] uniques = new double[B + 1][numDomains][];
        double[][][][] latentCorr = new double[B + 1][numDomains][][];
        int[] sampleSizes = new int[numDomains];
        boolean[] converged = new boolean[numDomains];

        Random random = seed < 0 ? new Random() : new Random(seed);

        for (int t = 0; t < numDomains; t++) {
            double[][] d = domainData.get(t);
            sampleSizes[t] = d.length;
            SimpleMatrix cov = covariance(d, null);
            OneFactorFit fit = OneFactorFit.fit(cov, fitBlocks);
            converged[t] = fit.isConverged();
            store(fit, loadings[0], uniques[0], latentCorr[0], t, k);
            double[] warm = fit.getRawParams();

            if (verbose) {
                TetradLogger.getInstance().log("LCD-NOD Phase 2: domain " + labels.get(t) + ", n = " + d.length
                                               + ", fit " + (fit.isConverged() ? "converged" : "did not converge")
                                               + ", discrepancy " + fit.getDiscrepancy());
            }

            int[] idx = new int[d.length];
            for (int b = 1; b <= B; b++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                for (int r = 0; r < idx.length; r++) idx[r] = random.nextInt(idx.length);
                SimpleMatrix bcov = covariance(d, idx);
                OneFactorFit bfit = OneFactorFit.fit(bcov, fitBlocks, warm);
                store(bfit, loadings[b], uniques[b], latentCorr[b], t, k);
            }
        }

        Estimates est = new Estimates(loadings, uniques, latentCorr, numDomains, B);

        // Indicator decisions.
        Decision[] indicatorDecisions = new Decision[p];
        for (int a = 0; a < p; a++) {
            if (fitBlocks[blockOf[a]].length == 1) {
                indicatorDecisions[a] = Decision.notApplicable("sole indicator of its block; its residual variance "
                                                               + "is fixed, and any change is attributed to the "
                                                               + "block variable");
                continue;
            }
            double[][] q = new double[B + 1][numDomains];
            for (int b = 0; b <= B; b++) for (int t = 0; t < numDomains; t++) q[b][t] = uniques[b][t][a];
            indicatorDecisions[a] = decidePositive(q, 1);
        }

        // Anchors: among the indicators of the block not found to change (all of them if none), the most reliable.
        int[] anchor = new int[k];
        boolean[] anchorFallback = new boolean[k];
        for (int i = 0; i < k; i++) {
            List<Integer> cands = new ArrayList<>();
            for (int a : fitBlocks[i]) {
                if (!indicatorDecisions[a].applicable || !indicatorDecisions[a].changes) cands.add(a);
            }
            if (cands.isEmpty()) {
                anchorFallback[i] = true;
                for (int a : fitBlocks[i]) cands.add(a);
            }
            int bestA = -1;
            double bestRel = Double.NEGATIVE_INFINITY;
            for (int a : cands) {
                double rel = 0;
                for (int t = 0; t < numDomains; t++) {
                    rel += loadings[0][t][a] * loadings[0][t][a] / Math.max(1e-12, uniques[0][t][a]);
                }
                if (rel > bestRel) {
                    bestRel = rel;
                    bestA = a;
                }
            }
            anchor[i] = bestA;
        }

        // Structure among the latents.
        Graph latentCpdag = null;
        if (suppliedLatentStructure != null) {
            latentCpdag = new EdgeListGraph(new ArrayList<>(latents));
            for (Edge e : suppliedLatentStructure.getEdges()) {
                int a = indexOfLatent(e.getNode1().getName());
                int b = indexOfLatent(e.getNode2().getName());
                if (a < 0 || b < 0) continue;
                if (Edges.isDirectedEdge(e)) {
                    Node head = Edges.getDirectedEdgeHead(e);
                    Node tail = Edges.getDirectedEdgeTail(e);
                    latentCpdag.addDirectedEdge(latents.get(indexOfLatent(tail.getName())),
                            latents.get(indexOfLatent(head.getName())));
                } else {
                    latentCpdag.addUndirectedEdge(latents.get(a), latents.get(b));
                }
            }
        } else if (learnLatentStructure && k > 1) {
            List<Score> scores = new ArrayList<>();
            for (int t = 0; t < numDomains; t++) {
                Matrix m = new Matrix(latentCorr[0][t]);
                SemBicScore score = new SemBicScore(new CovarianceMatrix(latents, m, sampleSizes[t]));
                score.setPenaltyDiscount(penaltyDiscount);
                scores.add(score);
            }
            Boss boss = new Boss(new ImagesScore(scores));
            boss.setVerbose(false);
            latentCpdag = new PermutationSearch(boss).search();
        }

        // Latent decisions.
        LatentEvidence[] evidence = new LatentEvidence[k];
        boolean[] latentChanges = new boolean[k];
        List<List<Integer>> minimalExplanations = new ArrayList<>();
        String latentRule;

        int undirected = 0;
        if (latentCpdag != null) {
            for (Edge e : latentCpdag.getEdges()) if (Edges.isUndirectedEdge(e)) undirected++;
        }

        if (latentCpdag != null && undirected <= MAX_UNDIRECTED_EDGES) {
            latentRule = "each DAG in the latent equivalence class; a latent changes if its conditional distribution "
                         + "given its parents changes; the DAGs with the fewest changing latents decide";
            decideByDags(latentCpdag, est, anchor, evidence, latentChanges, minimalExplanations);
        } else {
            latentRule = "all subsets of the other latents (Theorem 8); a latent changes only if its conditional "
                         + "distribution given every subset changes"
                         + (latentCpdag == null ? "" : " (the latent equivalence class has " + undirected
                                                       + " undirected edges, too many to enumerate)");
            decideBySubsets(k, est, anchor, evidence, latentChanges);
        }

        // The augmented graph.
        Graph graph = new EdgeListGraph();
        for (Node latent : latents) graph.addNode(latent);
        for (int c : cols) graph.addNode(dataSet.getVariable(c));
        for (int i = 0; i < k; i++) {
            for (int a : fitBlocks[i]) graph.addDirectedEdge(latents.get(i), dataSet.getVariable(cols.get(a)));
        }
        if (latentCpdag != null) {
            for (Edge edge : latentCpdag.getEdges()) {
                Node n1 = graph.getNode(edge.getNode1().getName());
                Node n2 = graph.getNode(edge.getNode2().getName());
                graph.addEdge(new Edge(n1, n2, edge.getEndpoint1(), edge.getEndpoint2()));
            }
        }
        graph.addNode(domainNode);
        for (int i = 0; i < k; i++) {
            if (latentChanges[i]) graph.addDirectedEdge(domainNode, latents.get(i));
        }
        for (int a = 0; a < p; a++) {
            if (indicatorDecisions[a].applicable && indicatorDecisions[a].changes) {
                graph.addDirectedEdge(domainNode, dataSet.getVariable(cols.get(a)));
            }
        }

        Result res = new Result();
        res.graph = graph;
        res.domainLabels = labels;
        res.sampleSizes = sampleSizes;
        res.indicatorNames = new ArrayList<>();
        for (int c : cols) res.indicatorNames.add(dataSet.getVariable(c).getName());
        res.loadings = loadings[0];
        res.uniques = uniques[0];
        res.latentCorrelations = latentCorr[0];
        res.indicatorDecisions = indicatorDecisions;
        res.latentEvidence = evidence;
        res.latentChanges = latentChanges;
        res.anchors = anchor;
        res.anchorFallback = anchorFallback;
        res.minimalExplanations = minimalExplanations;
        res.latentRule = latentRule;
        res.latentCpdag = latentCpdag;
        res.converged = converged;
        res.droppedDomains = droppedDomains;
        res.report = buildReport(res, blockOf);
        this.result = res;

        if (verbose) TetradLogger.getInstance().log(res.report);

        return graph;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The per-domain estimates for replicate 0 (the data) and the bootstrap replicates.
     */
    private record Estimates(double[][][] loadings, double[][][] uniques, double[][][][] latentCorr,
                             int numDomains, int numBootstraps) {
    }

    /**
     * Section C.3: enumerate the DAGs of the latent CPDAG; in each, a latent changes if its conditional distribution
     * given its parents changes; the DAGs with the fewest changes decide, a latent being reported as changing if it
     * changes in any of them.
     */
    private void decideByDags(Graph cpdag, Estimates est, int[] anchor, LatentEvidence[] evidence,
                              boolean[] latentChanges, List<List<Integer>> minimalExplanations)
            throws InterruptedException {
        int k = latents.size();
        Map<Integer, Decision> cache = new HashMap<>(); // key: latent index and parent mask

        int bestCount = Integer.MAX_VALUE;
        List<boolean[]> bestPatterns = new ArrayList<>();
        List<Decision[]> bestDecisions = new ArrayList<>();
        List<int[][]> bestParents = new ArrayList<>();

        for (int[] parentMasks : enumerateDags(cpdag)) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            boolean[] pattern = new boolean[k];
            Decision[] decisions = new Decision[k];
            int[][] parents = new int[k][];
            int count = 0;
            for (int i = 0; i < k; i++) {
                int mask = parentMasks[i];
                parents[i] = indices(mask, k);
                int key = i * (1 << k) + mask;
                Decision d = cache.get(key);
                if (d == null) {
                    d = conditionalChange(i, parents[i], anchor, est);
                    cache.put(key, d);
                }
                decisions[i] = d;
                pattern[i] = d.applicable && d.changes;
                if (pattern[i]) count++;
            }

            if (count < bestCount) {
                bestCount = count;
                bestPatterns.clear();
                bestDecisions.clear();
                bestParents.clear();
            }
            if (count == bestCount) {
                boolean dup = false;
                for (boolean[] bp : bestPatterns) {
                    if (Arrays.equals(bp, pattern)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    bestPatterns.add(pattern);
                    bestDecisions.add(decisions);
                    bestParents.add(parents);
                }
            }
        }

        for (boolean[] pattern : bestPatterns) {
            List<Integer> changing = new ArrayList<>();
            for (int i = 0; i < k; i++) if (pattern[i]) changing.add(i);
            minimalExplanations.add(changing);
        }

        for (int i = 0; i < k; i++) {
            int changesIn = 0;
            int chosen = -1;
            for (int m = 0; m < bestPatterns.size(); m++) {
                if (bestPatterns.get(m)[i]) {
                    changesIn++;
                    if (chosen < 0) chosen = m;
                }
            }
            // Show the evidence from a minimal explanation in which the latent changes, if there is one; otherwise
            // from the first minimal explanation.
            if (chosen < 0) chosen = 0;
            latentChanges[i] = changesIn > 0;
            evidence[i] = new LatentEvidence(anchor[i], bestParents.get(chosen)[i], bestDecisions.get(chosen)[i],
                    changesIn, bestPatterns.size());
        }
    }

    /**
     * Theorem 8: a latent changes only if its conditional distribution given every subset of the other latents
     * changes; the subset (and the decision) showing the least change is reported.
     */
    private void decideBySubsets(int k, Estimates est, int[] anchor, LatentEvidence[] evidence,
                                 boolean[] latentChanges) throws InterruptedException {
        int numMasks = 1 << k;
        LatentEvidence[] best = new LatentEvidence[k];
        for (int mask = 0; mask < numMasks; mask++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int[] c = indices(mask, k);
            for (int i = 0; i < k; i++) {
                if ((mask & (1 << i)) != 0) continue;
                Decision d = conditionalChange(i, c, anchor, est);
                LatentEvidence e = new LatentEvidence(anchor[i], c, d, 0, 0);
                if (best[i] == null || e.weakerChange(best[i])) best[i] = e;
            }
        }
        for (int i = 0; i < k; i++) {
            evidence[i] = best[i];
            latentChanges[i] = best[i].decision.applicable && best[i].decision.changes;
        }
    }

    /**
     * The DAGs of the CPDAG over the latents, each as an array of parent bit masks indexed by latent: every
     * orientation of the undirected edges that is acyclic and creates no unshielded collider beyond those of the
     * CPDAG (Meek). The CPDAG is assumed to have at most {@link #MAX_UNDIRECTED_EDGES} undirected edges.
     */
    private List<int[]> enumerateDags(Graph cpdag) {
        int k = latents.size();
        int[] base = new int[k];
        List<int[]> undirected = new ArrayList<>();
        boolean[][] adjacent = new boolean[k][k];
        for (Edge e : cpdag.getEdges()) {
            int a = indexOfLatent(e.getNode1().getName());
            int b = indexOfLatent(e.getNode2().getName());
            if (a < 0 || b < 0) continue;
            adjacent[a][b] = true;
            adjacent[b][a] = true;
            if (Edges.isUndirectedEdge(e)) {
                undirected.add(new int[]{a, b});
            } else if (Edges.isDirectedEdge(e)) {
                Node head = Edges.getDirectedEdgeHead(e);
                int child = indexOfLatent(head.getName());
                int parent = child == a ? b : a;
                base[child] |= 1 << parent;
            }
        }
        Set<Long> baseColliders = unshieldedColliders(base, adjacent);

        List<int[]> dags = new ArrayList<>();
        int u = undirected.size();
        for (int orient = 0; orient < (1 << u); orient++) {
            int[] parents = base.clone();
            for (int e = 0; e < u; e++) {
                int a = undirected.get(e)[0], b = undirected.get(e)[1];
                if ((orient & (1 << e)) == 0) parents[b] |= 1 << a; // a -> b
                else parents[a] |= 1 << b;                           // b -> a
            }
            if (!acyclic(parents)) continue;
            if (!unshieldedColliders(parents, adjacent).equals(baseColliders)) continue;
            dags.add(parents);
        }
        return dags;
    }

    private static boolean acyclic(int[] parents) {
        int k = parents.length;
        int[] remaining = parents.clone();
        boolean[] removed = new boolean[k];
        for (int round = 0; round < k; round++) {
            int found = -1;
            for (int i = 0; i < k; i++) {
                if (!removed[i] && remaining[i] == 0) {
                    found = i;
                    break;
                }
            }
            if (found < 0) return false;
            removed[found] = true;
            for (int i = 0; i < k; i++) remaining[i] &= ~(1 << found);
        }
        return true;
    }

    /**
     * The unshielded colliders a -> c <- b (a, b not adjacent) as keys a * k * k + b * k + c with a < b.
     */
    private static Set<Long> unshieldedColliders(int[] parents, boolean[][] adjacent) {
        int k = parents.length;
        Set<Long> out = new HashSet<>();
        for (int c = 0; c < k; c++) {
            for (int a = 0; a < k; a++) {
                if ((parents[c] & (1 << a)) == 0) continue;
                for (int b = a + 1; b < k; b++) {
                    if ((parents[c] & (1 << b)) == 0) continue;
                    if (adjacent[a][b]) continue;
                    out.add((long) a * k * k + (long) b * k + c);
                }
            }
        }
        return out;
    }

    private int indexOfLatent(String name) {
        for (int i = 0; i < latents.size(); i++) if (latents.get(i).getName().equals(name)) return i;
        return -1;
    }

    /**
     * Whether the conditional distribution of latent i given the latents c changes across domains: the calibrated
     * conditional variance (anchor loading squared times the conditional variance from the latent correlation
     * matrix) and, under the bootstrap rule, each calibrated coefficient (ratio of anchor loadings times the
     * coefficient), Bonferroni-combined.
     */
    private Decision conditionalChange(int i, int[] c, int[] anchor, Estimates est) {
        int B = est.numBootstraps();
        int D = est.numDomains();
        int m = c.length;

        double[][] var = new double[B + 1][D];
        double[][][] coef = new double[m][B + 1][D];
        for (int b = 0; b <= B; b++) {
            for (int t = 0; t < D; t++) {
                double[] reg = OneFactorFit.regression(est.latentCorr()[b][t], i, c);
                double wi = est.loadings()[b][t][anchor[i]];
                if (reg == null) {
                    var[b][t] = Double.NaN;
                    for (int u = 0; u < m; u++) coef[u][b][t] = Double.NaN;
                    continue;
                }
                var[b][t] = wi * wi * reg[m];
                for (int u = 0; u < m; u++) {
                    double wj = est.loadings()[b][t][anchor[c[u]]];
                    coef[u][b][t] = wi / wj * reg[u];
                }
            }
        }

        int components = B == 0 ? 1 : 1 + m;
        Decision dv = decidePositive(var, components);
        if (B == 0 || m == 0) return dv;

        Decision best = dv;
        String bestComponent = "conditional variance";
        for (int u = 0; u < m; u++) {
            Decision dc = decideSigned(coef[u], components);
            if (dc.applicable && (!best.applicable || dc.pValue < best.pValue)) {
                best = dc;
                bestComponent = "coefficient on " + latents.get(c[u]).getName();
            }
        }
        if (!best.applicable) return dv;
        return new Decision(best.changes, best.pValue, dv.logRange, true,
                best.note + "; deciding component: " + bestComponent);
    }

    private Map<Integer, List<Integer>> groupRows(int dcol) {
        boolean discrete = dataSet.getVariable(dcol) instanceof DiscreteVariable;
        Map<Integer, List<Integer>> rowsByValue = new LinkedHashMap<>();
        for (int r = 0; r < dataSet.getNumRows(); r++) {
            int v;
            if (discrete) {
                v = dataSet.getInt(r, dcol);
                if (v < 0) continue; // missing
            } else {
                double x = dataSet.getDouble(r, dcol);
                if (Double.isNaN(x)) continue;
                if (x != Math.rint(x)) {
                    throw new IllegalArgumentException("Domain column " + domainColumn + " has non-integral value "
                                                       + x + " in row " + r + "; a domain index must be discrete.");
                }
                v = (int) Math.rint(x);
            }
            rowsByValue.computeIfAbsent(v, key -> new ArrayList<>()).add(r);
        }
        return rowsByValue;
    }

    private static SimpleMatrix covariance(double[][] d, int[] rows) {
        int n = rows == null ? d.length : rows.length;
        int p = d[0].length;
        double[] mean = new double[p];
        for (int r = 0; r < n; r++) {
            double[] row = d[rows == null ? r : rows[r]];
            for (int c = 0; c < p; c++) mean[c] += row[c];
        }
        for (int c = 0; c < p; c++) mean[c] /= n;
        double[][] s = new double[p][p];
        for (int r = 0; r < n; r++) {
            double[] row = d[rows == null ? r : rows[r]];
            for (int a = 0; a < p; a++) {
                double da = row[a] - mean[a];
                for (int b = a; b < p; b++) s[a][b] += da * (row[b] - mean[b]);
            }
        }
        SimpleMatrix cov = new SimpleMatrix(p, p);
        for (int a = 0; a < p; a++) {
            for (int b = a; b < p; b++) {
                double v = s[a][b] / (n - 1);
                cov.set(a, b, v);
                cov.set(b, a, v);
            }
        }
        return cov;
    }

    private static void store(OneFactorFit fit, double[][] loadings, double[][] uniques, double[][][] corr,
                              int t, int k) {
        loadings[t] = fit.getLoadings();
        uniques[t] = fit.getUniques();
        SimpleMatrix r = fit.getLatentCorrelation();
        corr[t] = new double[k][k];
        for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) corr[t][i][j] = r.get(i, j);
    }

    private static int[] indices(int mask, int k) {
        int count = Integer.bitCount(mask);
        int[] idx = new int[count];
        int t = 0;
        for (int i = 0; i < k; i++) if ((mask & (1 << i)) != 0) idx[t++] = i;
        return idx;
    }

    /**
     * Decides whether a positive quantity, estimated per domain (row 0) with bootstrap replicates (rows 1..B),
     * changes across domains; the test is on the log scale. The p-value is Bonferroni-adjusted for the given number
     * of components, and compared with alpha after adjustment.
     */
    private Decision decidePositive(double[][] q, int components) {
        int B = q.length - 1;
        int D = q[0].length;
        double[] est = q[0];

        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        int usable = 0;
        for (double v : est) {
            if (v > 0 && Double.isFinite(v)) {
                lo = Math.min(lo, Math.log(v));
                hi = Math.max(hi, Math.log(v));
                usable++;
            }
        }
        if (usable < 2) {
            return Decision.notApplicable("fewer than two domains with a usable estimate");
        }
        double logRange = hi - lo;

        if (B == 0) {
            return new Decision(logRange > Math.log(changeRatio), Double.NaN, logRange, false, "ratio rule");
        }

        double[][] logQ = new double[B + 1][D];
        for (int b = 0; b <= B; b++) {
            for (int t = 0; t < D; t++) {
                double v = q[b][t];
                logQ[b][t] = v > 0 && Double.isFinite(v) ? Math.log(v) : Double.NaN;
            }
        }
        double pValue = cochranQ(logQ);
        if (Double.isNaN(pValue)) {
            return new Decision(logRange > Math.log(changeRatio), Double.NaN, logRange, false,
                    "ratio rule (bootstrap standard errors unavailable)");
        }
        double adjusted = Math.min(1.0, pValue * components);
        return new Decision(adjusted < alpha, adjusted, logRange, true, "bootstrap Q test");
    }

    /**
     * Decides whether a signed quantity (a coefficient) changes across domains, on its own scale.
     */
    private Decision decideSigned(double[][] q, int components) {
        double pValue = cochranQ(q);
        if (Double.isNaN(pValue)) return Decision.notApplicable("bootstrap standard errors unavailable");
        double adjusted = Math.min(1.0, pValue * components);
        return new Decision(adjusted < alpha, adjusted, Double.NaN, true, "bootstrap Q test");
    }

    /**
     * Cochran's Q p-value for equality across domains of the row-0 estimates, with inverse-variance weights from the
     * bootstrap replicates in rows 1..B; NaN if fewer than two domains have usable estimates and standard errors.
     */
    private static double cochranQ(double[][] q) {
        int B = q.length - 1;
        int D = q[0].length;
        double[] y = new double[D];
        double[] w = new double[D];
        int used = 0;
        for (int t = 0; t < D; t++) {
            double e = q[0][t];
            if (!Double.isFinite(e)) continue;
            double sum = 0, sumSq = 0;
            int m = 0;
            for (int b = 1; b <= B; b++) {
                double v = q[b][t];
                if (Double.isFinite(v)) {
                    sum += v;
                    sumSq += v * v;
                    m++;
                }
            }
            if (m < 3) continue;
            double var = (sumSq - sum * sum / m) / (m - 1);
            if (!(var > 1e-14)) var = 1e-14;
            y[t] = e;
            w[t] = 1.0 / var;
            used++;
        }
        if (used < 2) return Double.NaN;
        double sw = 0, swy = 0;
        for (int t = 0; t < D; t++) {
            sw += w[t];
            swy += w[t] * y[t];
        }
        double ybar = swy / sw;
        double Q = 0;
        for (int t = 0; t < D; t++) Q += w[t] * (y[t] - ybar) * (y[t] - ybar);
        return 1.0 - new ChiSquaredDistribution(used - 1).cumulativeProbability(Q);
    }

    private String buildReport(Result res, int[] blockOf) {
        StringBuilder sb = new StringBuilder();
        sb.append("LCD-NOD Phase 2: changes across domains of ").append(domainColumn).append('\n');
        sb.append("Change rule: ").append(numBootstraps > 0
                ? "bootstrap homogeneity test, B = " + numBootstraps + ", alpha = " + alpha
                : "ratio threshold " + changeRatio + " on variances (coefficients not examined)").append('\n');
        sb.append("Latent rule: ").append(res.latentRule).append('\n');
        sb.append("Domains (n): ");
        for (int t = 0; t < res.domainLabels.size(); t++) {
            if (t > 0) sb.append(", ");
            sb.append(res.domainLabels.get(t)).append(" (").append(res.sampleSizes[t]).append(')');
        }
        sb.append('\n');
        if (!res.droppedDomains.isEmpty()) sb.append("Dropped (too few rows): ").append(res.droppedDomains).append('\n');
        for (int t = 0; t < res.converged.length; t++) {
            if (!res.converged[t]) sb.append("Note: the fit in domain ").append(res.domainLabels.get(t))
                    .append(" did not meet the convergence criterion.\n");
        }

        sb.append("\nIndicators (residual variance per domain; decision):\n");
        for (int a = 0; a < res.indicatorNames.size(); a++) {
            sb.append("  ").append(res.indicatorNames.get(a)).append(" <- ").append(latents.get(blockOf[a]).getName())
                    .append(": ");
            for (int t = 0; t < res.domainLabels.size(); t++) {
                if (t > 0) sb.append(", ");
                sb.append(String.format("%.3f", res.uniques[t][a]));
            }
            sb.append("; ").append(res.indicatorDecisions[a]).append('\n');
        }

        if (res.latentCpdag != null) {
            sb.append("\nLatent structure (CPDAG): ").append(res.latentCpdag.getEdges()).append('\n');
        }

        if (!res.minimalExplanations.isEmpty()) {
            sb.append("Minimal explanations (fewest changing latents, ").append(res.minimalExplanations.get(0).size())
                    .append("): ");
            for (int m = 0; m < res.minimalExplanations.size(); m++) {
                if (m > 0) sb.append(" | ");
                sb.append('{');
                List<Integer> ex = res.minimalExplanations.get(m);
                for (int j = 0; j < ex.size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(latents.get(ex.get(j)).getName());
                }
                sb.append('}');
            }
            sb.append('\n');
            if (res.minimalExplanations.size() > 1) {
                sb.append("The minimal explanations disagree: second-order information cannot tell which of these "
                          + "latents changed; all of them are marked as changing.\n");
            }
        }

        sb.append("\nLatents (anchor; conditioning set; decision):\n");
        for (int i = 0; i < latents.size(); i++) {
            LatentEvidence e = res.latentEvidence[i];
            sb.append("  ").append(latents.get(i).getName()).append(": anchor ")
                    .append(res.indicatorNames.get(e.anchor));
            if (res.anchorFallback[i]) sb.append(" (no indicator of this latent is invariant; the most reliable "
                                                 + "was used)");
            sb.append("; given {");
            for (int j = 0; j < e.subset.length; j++) {
                if (j > 0) sb.append(", ");
                sb.append(latents.get(e.subset[j]).getName());
            }
            sb.append("}; ").append(e.decision);
            if (e.numMinimal > 1) {
                sb.append(" [changes in ").append(e.changesInMinimal).append(" of ").append(e.numMinimal)
                        .append(" minimal explanations]");
            }
            sb.append('\n');
        }

        sb.append("\nLatent correlations per domain:\n");
        for (int t = 0; t < res.domainLabels.size(); t++) {
            sb.append("  ").append(res.domainLabels.get(t)).append(":\n");
            for (int i = 0; i < latents.size(); i++) {
                sb.append("    ");
                for (int j = 0; j < latents.size(); j++) {
                    sb.append(String.format("%7.3f", res.latentCorrelations[t][i][j]));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * The decision for one quantity or one conditional distribution.
     */
    public static final class Decision {
        /**
         * Whether a change across domains was found.
         */
        public final boolean changes;
        /**
         * The p-value of the homogeneity test (Bonferroni-adjusted over the components of a conditional
         * distribution), or NaN under the ratio rule.
         */
        public final double pValue;
        /**
         * The range of the log of the per-domain variance estimates (NaN for a signed quantity).
         */
        public final double logRange;
        /**
         * Whether a decision was made at all (false for a quantity that is not tested, such as the residual variance
         * of the sole indicator of a block).
         */
        public final boolean applicable;
        /**
         * Whether the bootstrap test (rather than the ratio rule) made the decision.
         */
        public final boolean tested;
        /**
         * A note on how the decision was made.
         */
        public final String note;

        Decision(boolean changes, double pValue, double logRange, boolean tested, String note) {
            this.changes = changes;
            this.pValue = pValue;
            this.logRange = logRange;
            this.applicable = true;
            this.tested = tested;
            this.note = note;
        }

        private Decision(String note) {
            this.changes = false;
            this.pValue = Double.NaN;
            this.logRange = Double.NaN;
            this.applicable = false;
            this.tested = false;
            this.note = note;
        }

        static Decision notApplicable(String note) {
            return new Decision(note);
        }

        @Override
        public String toString() {
            if (!applicable) return "not tested (" + note + ")";
            String s = changes ? "CHANGES" : "invariant";
            String range = Double.isNaN(logRange) ? "" : String.format(", variance max/min = %.3g", Math.exp(logRange));
            if (tested) s += String.format(" (p = %.4g%s; %s)", pValue, range, note);
            else s += String.format(" (variance max/min = %.3g; %s)", Math.exp(logRange), note);
            return s;
        }
    }

    /**
     * For a latent, the anchor and conditioning set whose decision is reported, with that decision.
     */
    public static final class LatentEvidence {
        /**
         * The anchor indicator, as an index into the result's indicator list.
         */
        public final int anchor;
        /**
         * The conditioning set, as indices into the latent list: the parents in a minimal explanation under the DAG
         * rule, or the subset showing the least change under the fallback rule.
         */
        public final int[] subset;
        /**
         * The decision for this anchor and conditioning set.
         */
        public final Decision decision;
        /**
         * Under the DAG rule, the number of minimal explanations in which the latent changes; 0 otherwise.
         */
        public final int changesInMinimal;
        /**
         * Under the DAG rule, the number of minimal explanations; 0 otherwise.
         */
        public final int numMinimal;

        LatentEvidence(int anchor, int[] subset, Decision decision, int changesInMinimal, int numMinimal) {
            this.anchor = anchor;
            this.subset = subset;
            this.decision = decision;
            this.changesInMinimal = changesInMinimal;
            this.numMinimal = numMinimal;
        }

        /**
         * Whether this evidence shows less change than the other: a non-change beats a change; among tested
         * decisions a larger p-value; otherwise a smaller log range.
         */
        boolean weakerChange(LatentEvidence other) {
            if (!decision.applicable) return !other.decision.applicable && decision.logRange < other.decision.logRange;
            if (!other.decision.applicable) return true;
            if (decision.changes != other.decision.changes) return !decision.changes;
            if (decision.tested && other.decision.tested) return decision.pValue > other.decision.pValue;
            return decision.logRange < other.decision.logRange;
        }
    }

    /**
     * The full result of a search.
     */
    public static final class Result {
        /**
         * The augmented graph.
         */
        public Graph graph;
        /**
         * The domain labels, in the order of the per-domain arrays.
         */
        public List<String> domainLabels;
        /**
         * The per-domain sample sizes.
         */
        public int[] sampleSizes;
        /**
         * The indicator names, in block order, which is the order of the per-indicator arrays.
         */
        public List<String> indicatorNames;
        /**
         * Per domain, the loadings (unit-latent convention) per indicator.
         */
        public double[][] loadings;
        /**
         * Per domain, the residual variances per indicator.
         */
        public double[][] uniques;
        /**
         * Per domain, the latent correlation matrix in block order.
         */
        public double[][][] latentCorrelations;
        /**
         * The decision per indicator.
         */
        public Decision[] indicatorDecisions;
        /**
         * The reported evidence per latent.
         */
        public LatentEvidence[] latentEvidence;
        /**
         * Per latent, whether it is reported as changing.
         */
        public boolean[] latentChanges;
        /**
         * Per latent, the anchor indicator as an index into the indicator list.
         */
        public int[] anchors;
        /**
         * Per latent, whether no indicator was invariant so that the anchor was taken among all of them.
         */
        public boolean[] anchorFallback;
        /**
         * Under the DAG rule, the sets of changing latents (as indices) of the minimal explanations; empty otherwise.
         */
        public List<List<Integer>> minimalExplanations;
        /**
         * A description of the rule that made the latent decisions.
         */
        public String latentRule;
        /**
         * The learned CPDAG over the latents, or null if not learned.
         */
        public Graph latentCpdag;
        /**
         * Per domain, whether the fit converged.
         */
        public boolean[] converged;
        /**
         * Domains dropped for having too few rows.
         */
        public List<String> droppedDomains;
        /**
         * The text report.
         */
        public String report;

        Result() {
        }
    }
}
