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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.rlcd.LatentGroups;
import edu.cmu.tetrad.search.rlcd.PooledRankTest;
import edu.cmu.tetrad.search.rlcd.RankTester;
import edu.cmu.tetrad.search.rlcd.RlcdClusterSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Rank-based Latent Causal Discovery (RLCD), from Dong, Huang, Ng, Song, Zheng, Jin, Legaspi, Spirtes, and Zhang,
 * "A versatile causal discovery framework to allow causally-related hidden variables," ICLR 2024. This is a
 * translation of the implementation released in causal-learn
 * (<code>causallearn.search.HiddenCausal.RLCD</code>), for linear models with possibly causally related latent
 * variables.
 * <p>
 * The method has two stages. Stage 1 runs an ordinary score-based search over the observed variables (BOSS by
 * default here; GES in causal-learn) and partitions the variables into groups: maximal cliques of the resulting
 * skeleton with at least <code>partitionCliqueThreshold</code> members are merged when they share at least two
 * variables, and merged groups with at least four variables become partitions. Stage 2 runs the rank-deficiency
 * cluster search of {@link RlcdClusterSearch} within each partition, introducing latent variables. The output graph
 * is the stage-1 graph with, inside each partition, the observed-observed edges replaced by what stage 2 reports,
 * plus the latent variables and their edges.
 * <p>
 * Output conventions follow causal-learn: a directed edge for each parent-child relation found; an undirected edge
 * where the stage-1 graph had an undirected edge between variables not inside a common partition, and between
 * latent roots chained by the finish-up step; and no edge between
 * co-members of a multi-variable atomic cover (a rank-k cluster with k &gt; 1 is placed under k latents that are not
 * connected to one another). Latent nodes are of type {@link NodeType#LATENT} and named L1, L2, ...
 * <p>
 * Known limitations inherited from the released code: candidate sets with no observed member are never rank-tested
 * (see {@link RlcdClusterSearch}); the finish-up step chains leftover latent roots in an order that is arbitrary in
 * the Python and alphabetical here, without testing whether consecutive roots are dependent; and stage-1
 * edges within a partition in which at least one latent was introduced are dropped whether or not stage 2 explains
 * them, while a partition with no latent keeps its stage-1 edges unchanged.
 * <p>
 * The translation was checked against the causal-learn code on nine simulated structures (pure clusters, a latent
 * chain, a rank-2 cluster, an observed non-sink, an impure indicator, an observed DAG with no latents) by feeding both
 * the same stage-1 graph; all agreed exactly up to relabeling of latents and the finish-step chain order.
 * <p>
 * One deliberate difference from the released code: the edges of the finish-step chain are undirected here, where
 * the Python directs them in an order that depends on its hash seed, since their orientation is not identifiable
 * from rank constraints.
 *
 * @author josephramsey (translation)
 * @see RlcdClusterSearch
 * @see Chi2RankTest
 */
public class Rlcd {

    /**
     * Stage-1 search methods.
     */
    public enum Stage1Method {
        /**
         * BOSS with the SEM BIC score.
         */
        BOSS,
        /**
         * FGES with the SEM BIC score (closest to the GES stage of causal-learn).
         */
        FGES
    }

    private final DataSet dataSet;
    /**
     * The imputations searched over as one; just the data set when there is only one.
     */
    private final List<DataSet> imputations;
    private final List<Node> variables;
    private RankTester rankTester;
    private double alpha = 0.01;
    private double[] alphaByRank = null;
    private int maxK = 3;
    private boolean allowNonLeafX = true;
    private boolean unfoldCovers = true;
    private boolean checkV = true;
    private int partitionCliqueThreshold = 3;
    private Stage1Method stage1Method = Stage1Method.BOSS;
    private double penaltyDiscount = 1.0;
    private Graph stage1Graph = null;
    private long seed = -1;
    private boolean verbose = false;
    private Knowledge knowledge = new Knowledge();

    private Graph stage1Result;
    private List<List<Node>> partition;
    private List<LatentGroups> latentGroups;
    private int[][] adjacency;
    private List<String> allVariableNames;

    /**
     * Constructs the search over a continuous data set.
     *
     * @param dataSet the data.
     */
    public Rlcd(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");
        if (!dataSet.isContinuous()) throw new IllegalArgumentException("RLCD requires continuous data.");
        this.dataSet = dataSet;
        this.imputations = List.of(dataSet);
        this.variables = new ArrayList<>(dataSet.getVariables());
    }

    /**
     * Constructs ONE search over several imputations of a data set with missing values. Stage 1 is run on the
     * average of the imputations' covariance matrices, and the stage-2 rank tests are pooled over the imputations
     * by {@link PooledRankTest}, so that every decision is made once, from all of the imputations. With a single
     * data set this is the same as {@link #Rlcd(DataSet)}.
     * <p>
     * The average covariance matrix is a consistent estimate of the covariance matrix when the values are missing
     * at random and the imputation model is right, but stage 1 scores it at the full sample size, which overstates
     * the information in it by the fraction that was imputed.
     *
     * @param imputations the imputed data sets: continuous, with the same variables in the same order and the same
     *                    number of rows.
     */
    public Rlcd(List<DataSet> imputations) {
        if (imputations == null || imputations.isEmpty()) {
            throw new IllegalArgumentException("At least one data set is required.");
        }

        DataSet first = imputations.getFirst();

        for (DataSet dataSet : imputations) {
            if (dataSet == null) throw new NullPointerException("Data set is null.");
            if (!dataSet.isContinuous()) throw new IllegalArgumentException("RLCD requires continuous data.");

            if (!dataSet.getVariableNames().equals(first.getVariableNames())
                || dataSet.getNumRows() != first.getNumRows()) {
                throw new IllegalArgumentException("To be pooled as imputations, the data sets must have the same "
                                                   + "variables, in the same order, and the same number of rows.");
            }
        }

        this.dataSet = first;
        this.imputations = new ArrayList<>(imputations);
        this.variables = new ArrayList<>(first.getVariables());
    }

    /**
     * Sets the rank test. The default is {@link Chi2RankTest} over the data.
     *
     * @param rankTester the test.
     */
    public void setRankTester(RankTester rankTester) {
        this.rankTester = rankTester;
    }

    /**
     * Sets the significance level used for all rank tests (default 0.01, as in causal-learn).
     *
     * @param alpha the level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0, 1).");
        this.alpha = alpha;
    }

    /**
     * Optionally sets a different significance level for each hypothesized rank: entry k is used when testing rank
     * ≤ k. Ranks beyond the array fall back to the single alpha. Pass null to use the single alpha throughout.
     *
     * @param alphaByRank the levels by rank.
     */
    public void setAlphaByRank(double[] alphaByRank) {
        this.alphaByRank = alphaByRank == null ? null : alphaByRank.clone();
    }

    /**
     * Sets the largest cluster cardinality to search for (default 3).
     *
     * @param maxK the maximum k.
     */
    public void setMaxK(int maxK) {
        if (maxK < 1) throw new IllegalArgumentException("maxK must be at least 1.");
        this.maxK = maxK;
    }

    /**
     * Whether observed variables may act as non-sinks (parents conditioned on in the rank tests). Default true.
     *
     * @param allowNonLeafX the flag.
     */
    public void setAllowNonLeafX(boolean allowNonLeafX) {
        this.allowNonLeafX = allowNonLeafX;
    }

    /**
     * Whether discovered latent covers are unfolded into their children for further rank tests. Default true.
     *
     * @param unfoldCovers the flag.
     */
    public void setUnfoldCovers(boolean unfoldCovers) {
        this.unfoldCovers = unfoldCovers;
    }

    /**
     * Whether a rank-deficient set is discarded when one of its proper subsets is already rank deficient at the
     * corresponding lower rank. Default true.
     *
     * @param checkV the flag.
     */
    public void setCheckV(boolean checkV) {
        this.checkV = checkV;
    }

    /**
     * Sets the minimum size of a stage-1 clique to be considered for a partition (default 3).
     *
     * @param threshold the threshold.
     */
    public void setPartitionCliqueThreshold(int threshold) {
        if (threshold < 1) throw new IllegalArgumentException("threshold must be at least 1.");
        this.partitionCliqueThreshold = threshold;
    }

    /**
     * Sets the stage-1 search method (default BOSS).
     *
     * @param method the method.
     */
    public void setStage1Method(Stage1Method method) {
        this.stage1Method = method;
    }

    /**
     * Sets the SEM BIC penalty discount for the stage-1 search (default 1, the standard BIC penalty; causal-learn's
     * default GES sparsity of 0.5 corresponds to this value).
     *
     * @param penaltyDiscount the penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Supplies a stage-1 graph over the data variables directly, skipping the stage-1 search. Directed edges are
     * kept directed, any other edge is treated as undirected.
     *
     * @param stage1Graph the graph, or null to run the stage-1 search.
     */
    public void setStage1Graph(Graph stage1Graph) {
        this.stage1Graph = stage1Graph;
    }

    /**
     * Sets the random seed for the stage-1 BOSS search; −1 (default) leaves it unseeded.
     *
     * @param seed the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets background knowledge over the observed variables. Knowledge is applied in three places: the stage-1
     * search honors it as any search does; in stage 2 an observed variable is never used as a non-sink (parent) of a
     * candidate member it is forbidden to cause, and a cluster whose cover would create a forbidden observed-to-
     * observed edge is rejected; and required edges between observed variables are restored in the output if stage
     * 2 dropped them, oriented as required. Knowledge cannot refer to latent variables, which are created by the
     * search, so it cannot keep an observed variable from being placed under a latent.
     *
     * @param knowledge the knowledge; null means none.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
    }

    /**
     * Whether to log the search trace through {@link TetradLogger}.
     *
     * @param verbose the flag.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Runs the search.
     *
     * @return the output graph over observed and latent variables.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
        int nx = variables.size();
        List<String> xvars = new ArrayList<>();
        for (Node v : variables) xvars.add(v.getName());

        if (rankTester == null) {
            rankTester = imputations.size() > 1 ? new PooledRankTest(imputations) : new Chi2RankTest(dataSet);
        }

        // Stage 1.
        Graph g1 = stage1Graph != null ? stage1Graph : runStage1();
        this.stage1Result = g1;
        int[][] adj = stage1Adjacency(g1, xvars);
        int[][] adjStage1 = copy(adj);

        this.partition = getPartition(adjStage1, partitionCliqueThreshold);
        log("Partition of cliques:");
        for (List<Node> group : partition) log("   " + group);

        String latentPrefix = choosePrefix(xvars);

        // Stage 2, per partition.
        this.latentGroups = new ArrayList<>();
        RlcdClusterSearch search = new RlcdClusterSearch(xvars, rankTester, this::alphaFor, maxK,
                allowNonLeafX, unfoldCovers, checkV);
        search.setKnowledge(knowledge);
        if (verbose) search.setLog(this::log);

        for (List<Node> group : partition) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<String> currentXvars = new ArrayList<>();
            List<Integer> currentIdx = new ArrayList<>();
            for (Node node : group) {
                currentXvars.add(node.getName());
                currentIdx.add(xvars.indexOf(node.getName()));
            }

            Set<String> neighbourSet = new TreeSet<>();
            for (int i : currentIdx) {
                for (int j = 0; j < nx; j++) {
                    if (!currentIdx.contains(j) && adj[i][j] != 0) neighbourSet.add(xvars.get(j));
                }
            }

            int m = currentXvars.size();
            boolean[][] localAdj = new boolean[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    localAdj[a][b] = adj[currentIdx.get(a)][currentIdx.get(b)] != 0;
                }
            }

            // A variable that knowledge forbids from being a parent of every other member cannot be a non-sink.
            List<String> nonSinkCandidates = new ArrayList<>();
            for (String c : currentXvars) {
                boolean canParentSomething = false;
                for (String x : currentXvars) {
                    if (!x.equals(c) && !knowledge.isForbidden(c, x)) {
                        canParentSomething = true;
                        break;
                    }
                }
                if (canParentSomething) nonSinkCandidates.add(c);
            }

            LatentGroups current = new LatentGroups(currentXvars, nonSinkCandidates, neighbourSet, localAdj,
                    latentPrefix);
            current = search.findClusters(current);
            latentGroups.add(current);

            LatentGroups.AdjacencyResult out = current.toAdjacency();
            int[][] outAdj = getReducedAdj(out.adjacency(), m);
            int numNewLatent = outAdj.length - m;

            if (numNewLatent > 0) {
                int oldSize = adj.length;
                int[][] temp = new int[oldSize + numNewLatent][oldSize + numNewLatent];
                for (int i = 0; i < oldSize; i++) System.arraycopy(adj[i], 0, temp[i], 0, oldSize);
                adj = temp;

                List<Integer> latentIdx = new ArrayList<>();
                for (int i = oldSize; i < oldSize + numNewLatent; i++) latentIdx.add(i);

                copyByIdx(adj, outAdj, 0, m, 0, m, currentIdx, currentIdx);
                copyByIdx(adj, outAdj, 0, m, m, m + numNewLatent, currentIdx, latentIdx);
                copyByIdx(adj, outAdj, m, m + numNewLatent, 0, m, latentIdx, currentIdx);
                copyByIdx(adj, outAdj, m, m + numNewLatent, m, m + numNewLatent, latentIdx, latentIdx);
            }
            // As in the Python, a partition in which no latent was introduced leaves its stage-1 block untouched.
        }

        // Assemble the output graph.
        List<String> allVars = new ArrayList<>(xvars);
        Set<String> taken = new HashSet<>(xvars);
        int li = 1;
        for (int i = nx; i < adj.length; i++) {
            String name;
            do {
                name = "L" + li++;
            } while (taken.contains(name));
            taken.add(name);
            allVars.add(name);
        }
        this.allVariableNames = allVars;

        // Required edges between observed variables are kept, oriented as required, if stage 2 dropped them.
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nx; j++) {
                if (i == j || !knowledge.isRequired(xvars.get(i), xvars.get(j))) continue;
                if (adj[i][j] == -1 && adj[j][i] == 1) continue;             // already i --> j
                if (adj[i][j] == 1 && adj[j][i] == -1) {                      // found j --> i: conflict, keep
                    log("Knowledge requires " + xvars.get(i) + " --> " + xvars.get(j)
                        + " but the search oriented it the other way; left as found.");
                    continue;
                }
                adj[i][j] = -1;
                adj[j][i] = 1;
                log("Adding required edge " + xvars.get(i) + " --> " + xvars.get(j) + " from knowledge.");
            }
        }

        // Co-members of an atomic cover (−2) get no edge.
        for (int i = 0; i < adj.length; i++) {
            for (int j = 0; j < adj.length; j++) {
                if (adj[i][j] == -2) adj[i][j] = 0;
            }
        }
        this.adjacency = adj;

        List<Node> nodes = new ArrayList<>(variables);
        for (int i = nx; i < adj.length; i++) {
            GraphNode latent = new GraphNode(allVars.get(i));
            latent.setNodeType(NodeType.LATENT);
            nodes.add(latent);
        }
        return adjacencyToGraph(adj, nodes);
    }

    /**
     * @return the stage-1 graph used (either supplied or searched); null before {@link #search()}.
     */
    public Graph getStage1Graph() {
        return stage1Result;
    }

    /**
     * @return the partition of observed variables searched in stage 2; null before {@link #search()}.
     */
    public List<List<Node>> getPartition() {
        return partition;
    }

    /**
     * @return the per-partition latent structures, for inspection; null before {@link #search()}.
     */
    public List<LatentGroups> getLatentGroups() {
        return latentGroups;
    }

    /**
     * The final signed adjacency over observed then latent variables, in causal-learn's encoding: A[i][j] = −1 and
     * A[j][i] = 1 for i → j; −1 both ways for an undirected edge.
     *
     * @return the matrix; null before {@link #search()}.
     */
    public int[][] getAdjacency() {
        return adjacency;
    }

    /**
     * @return observed variable names followed by latent names, indexing {@link #getAdjacency()}.
     */
    public List<String> getAllVariableNames() {
        return allVariableNames;
    }

    // ---------------------------------------------------------------- stage 1

    private double alphaFor(int rank) {
        if (alphaByRank != null && rank >= 0 && rank < alphaByRank.length) return alphaByRank[rank];
        return alpha;
    }

    private Graph runStage1() throws InterruptedException {
        SemBicScore score = new SemBicScore(stage1Covariance());
        score.setPenaltyDiscount(penaltyDiscount);
        if (stage1Method == Stage1Method.FGES) {
            Fges fges = new Fges(score);
            fges.setKnowledge(knowledge);
            fges.setVerbose(false);
            return fges.search();
        } else {
            Boss boss = new Boss(score);
            boss.setVerbose(false);
            PermutationSearch ps = new PermutationSearch(boss);
            ps.setKnowledge(knowledge);
            if (seed != -1) ps.setSeed(seed);
            return ps.search();
        }
    }

    /**
     * The covariance matrix stage 1 is scored on: the data set's, or with several imputations the average of
     * theirs, at the common sample size.
     */
    private CovarianceMatrix stage1Covariance() {
        CovarianceMatrix first = new CovarianceMatrix(dataSet);
        if (imputations.size() == 1) return first;

        Matrix sum = first.getMatrix();

        for (int i = 1; i < imputations.size(); i++) {
            sum = sum.plus(new CovarianceMatrix(imputations.get(i)).getMatrix());
        }

        return new CovarianceMatrix(first.getVariables(), sum.scalarMult(1.0 / imputations.size()),
                first.getSampleSize());
    }

    /**
     * Encodes a graph over the data variables in causal-learn's convention.
     */
    private int[][] stage1Adjacency(Graph g, List<String> xvars) {
        int n = xvars.size();
        int[][] adj = new int[n][n];
        for (Edge edge : g.getEdges()) {
            int i = xvars.indexOf(edge.getNode1().getName());
            int j = xvars.indexOf(edge.getNode2().getName());
            if (i < 0 || j < 0) {
                throw new IllegalArgumentException("Stage-1 graph node not in data: " + edge);
            }
            if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.ARROW) {
                adj[i][j] = -1;
                adj[j][i] = 1;
            } else if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.TAIL) {
                adj[j][i] = -1;
                adj[i][j] = 1;
            } else {
                adj[i][j] = -1;
                adj[j][i] = -1;
            }
        }
        return adj;
    }

    /**
     * Partitions the observed variables (Python <code>getPartition</code>): maximal cliques of the skeleton with
     * at least <code>threshold</code> members, merged when two cliques share at least two variables; merged groups
     * of at least four variables are returned, each ordered by data column.
     */
    private List<List<Node>> getPartition(int[][] adjStage1, int threshold) {
        int n = variables.size();
        Graph skeleton = new EdgeListGraph(variables);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adjStage1[i][j] != 0 || adjStage1[j][i] != 0) {
                    skeleton.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        List<Set<Integer>> cliques = new ArrayList<>();
        for (Set<Node> clique : GraphUtils.maximalCliques(skeleton, variables)) {
            if (clique.size() < threshold) continue;
            Set<Integer> idx = new TreeSet<>();
            for (Node node : clique) idx.add(variables.indexOf(node));
            cliques.add(idx);
        }
        cliques.sort(Comparator.comparing(Object::toString));

        int c = cliques.size();
        int[] root = new int[c];
        for (int i = 0; i < c; i++) root[i] = i;
        for (int i = 0; i < c; i++) {
            for (int j = 0; j < c; j++) {
                if (i == j) continue;
                Set<Integer> common = new HashSet<>(cliques.get(i));
                common.retainAll(cliques.get(j));
                if (common.size() >= 2) {
                    int a = find(root, i), b = find(root, j);
                    if (a != b) root[b] = a;
                }
            }
        }

        Map<Integer, SortedSet<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < c; i++) {
            groups.computeIfAbsent(find(root, i), k -> new TreeSet<>()).addAll(cliques.get(i));
        }

        List<List<Node>> out = new ArrayList<>();
        for (SortedSet<Integer> group : groups.values()) {
            if (group.size() >= 4) {
                List<Node> nodes = new ArrayList<>();
                for (int i : group) nodes.add(variables.get(i));
                out.add(nodes);
            }
        }
        out.sort(Comparator.comparingInt((List<Node> group) -> variables.indexOf(group.get(0)))
                .thenComparingInt(List::size));
        return out;
    }

    private static int find(int[] root, int k) {
        while (root[k] != k) {
            root[k] = root[root[k]];
            k = root[k];
        }
        return k;
    }

    /**
     * Keeps the observed indices 0..numObserved−1 and every latent that lies on a simple path between two observed
     * variables, dropping the rest (Python <code>getReducedAdj</code>).
     */
    private static int[][] getReducedAdj(int[][] adj, int numObserved) {
        int n = adj.length;
        SortedSet<Integer> keep = new TreeSet<>();
        for (int i = 0; i < numObserved; i++) keep.add(i);
        for (int start = 0; start < numObserved; start++) {
            boolean[] onPath = new boolean[n];
            onPath[start] = true;
            dfsPaths(adj, numObserved, start, start, onPath, keep);
        }
        List<Integer> idx = new ArrayList<>(keep);
        int[][] out = new int[idx.size()][idx.size()];
        for (int a = 0; a < idx.size(); a++) {
            for (int b = 0; b < idx.size(); b++) out[a][b] = adj[idx.get(a)][idx.get(b)];
        }
        return out;
    }

    private static void dfsPaths(int[][] adj, int numObserved, int start, int current, boolean[] onPath,
                                 Set<Integer> keep) {
        if (current < numObserved && current != start) {
            for (int i = 0; i < onPath.length; i++) if (onPath[i]) keep.add(i);
            return;
        }
        for (int j = 0; j < adj.length; j++) {
            if (adj[current][j] != 0 && !onPath[j]) {
                onPath[j] = true;
                dfsPaths(adj, numObserved, start, j, onPath, keep);
                onPath[j] = false;
            }
        }
    }

    private static void copyByIdx(int[][] target, int[][] source, int r0, int r1, int c0, int c1,
                                  List<Integer> rowsInTarget, List<Integer> colsInTarget) {
        for (int r = r0; r < r1; r++) {
            for (int c = c0; c < c1; c++) {
                target[rowsInTarget.get(r - r0)][colsInTarget.get(c - c0)] = source[r][c];
            }
        }
    }

    private static int[][] copy(int[][] a) {
        int[][] out = new int[a.length][];
        for (int i = 0; i < a.length; i++) out[i] = a[i].clone();
        return out;
    }

    /**
     * Chooses an internal prefix for latent names that cannot collide with an observed variable name.
     */
    private static String choosePrefix(List<String> xvars) {
        String prefix = "L";
        while (true) {
            boolean collides = false;
            for (String x : xvars) {
                if (x.startsWith(prefix) && x.substring(prefix.length()).matches("\\d+")) {
                    collides = true;
                    break;
                }
            }
            if (!collides) return prefix;
            prefix = "_" + prefix;
        }
    }

    /**
     * Converts a causal-learn-encoded adjacency into a Tetrad graph (Python <code>_adjacency_to_causal_graph</code>).
     */
    private static Graph adjacencyToGraph(int[][] adj, List<Node> nodes) {
        Graph graph = new EdgeListGraph(nodes);
        for (int i = 0; i < adj.length; i++) {
            for (int j = i + 1; j < adj.length; j++) {
                if (adj[i][j] == 0 && adj[j][i] == 0) continue;
                if (adj[i][j] == -1 && adj[j][i] == 1) {
                    graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                } else if (adj[i][j] == 1 && adj[j][i] == -1) {
                    graph.addDirectedEdge(nodes.get(j), nodes.get(i));
                } else {
                    graph.addUndirectedEdge(nodes.get(i), nodes.get(j));
                }
            }
        }
        return graph;
    }

    private void log(String message) {
        if (verbose) TetradLogger.getInstance().log(message);
    }
}
