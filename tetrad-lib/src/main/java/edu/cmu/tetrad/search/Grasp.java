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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.util.Collections.shuffle;


/**
 * Implements the GRaSP algorithms, which uses a certain procedure to search in the space of permutations of variables
 * for ones that imply CPDAGs that are especially close to the CPDAG of the true model. The reference is here:
 * <p>
 * Lam, W. Y., Andrews, B., &amp; Ramsey, J. (2022, August). Greedy relaxations of the sparsest permutation algorithm.
 * In Uncertainty in Artificial Intelligence (pp. 1052-1062). PMLR.
 * <p>
 * GRaSP can use either a score or an independence test; you can provide both, though if you do you need to use the
 * parameters to choose which one will be used. The score option is more scalable and accurate, though the independence
 * option is perhaps a little easier ot deal with theoretically and is useful for generating unit test results.
 * <p>
 * As shown the reference above, GRaSP generates results for the linear, Gaussian case for N = 1000 with precisions for
 * adjacencies and arrowheads near 1 and recalls of about 0.85, when the linear, Gaussian BIC score is used with a
 * penalty of 2. For N = 10,000 recalls also rise up to about 1, so it can be an extraordinarily accurate search for the
 * linear, Gaussian case. But in principle, it can be used with any sort of data, so long as a BIC score is available
 * for that data. So it can be used for the discrete case and for the mixed continuous/discrete case as well.
 * <p>
 * The version of GRaSP described in the above reference is limited to about 100 variables in execution time, after
 * which it become impracticably slow. Recent optimizations allow it to scale further than that; hopefully these will be
 * written up soon and made available.
 * <p>
 * Knowledge can be used with this search. If tiered knowledge is used, then the procedure is carried out for each tier
 * separately, given the variable preceding that tier, which allows the SP algorithm to address tiered (e.g., time
 * series) problems with larger numbers of variables.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see Fges
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public class Grasp {
    /**
     * The variables to be permuted.
     */
    private final List<Node> variables;
    /**
     * The score or test to be used.
     */
    private Score score;
    /**
     * The test to be used.
     */
    private IndependenceTest test;
    /**
     * The knowledge to be used.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The scorer to be used.
     */
    private TeyssierScorer scorer;
    /**
     * The time at which the algorithm started.
     */
    private long start;
    /**
     * Whether to use the score or the test.
     */
    private boolean useScore = true;
    /**
     * Whether to use the Raskutti-Uhler method or the Verma-Pearl method.
     */
    private boolean useRaskuttiUhler;
    /**
     * Whether to impose an ordering on the three GRaSP algorithms.
     */
    private boolean ordered = false;
    /**
     * Whether to use verbose output.
     */
    private boolean verbose;
    /**
     * The maximum depth of the depth-first search for tucks.
     */
    private int uncoveredDepth = 1;
    /**
     * The maximum depth of the depth-first search for uncovered tucks.
     */
    private int nonSingularDepth = 1;
    /**
     * Whether to use the data order or a random order for the initial permutation.
     */
    private boolean useDataOrder = true;
    /**
     * The maximum depth of the depth-first search for singular tucks.
     */
    private int depth = 3;
    /* The number of times to run the algorithm with different starting permutations. */
    private int numStarts = 1;
    /**
     * Whether to allow internal randomness in the algorithm.
     */
    private boolean allowInternalRandomness = false;
    /**
     * Represents the seed used for random number generation or shuffling.
     */
    private long seed = -1;
    private boolean replicatingGraph = false;

    /**
     * Constructor for a score.
     *
     * @param score The score to use.
     */
    public Grasp(@NotNull Score score) {
        this.score = score;
        this.variables = getVariables(null, score);
        this.useScore = true;
    }

    /**
     * Constructor for a test.
     *
     * @param test The test to use.
     */
    public Grasp(@NotNull IndependenceTest test) {
        this.test = test;
        variables = getVariables(test, null);
        this.useScore = false;
        this.useRaskuttiUhler = true;
    }

    /**
     * Constructor that takes both a test and a score; only one is used-- the parameter setting will decide which.
     *
     * @param test  The test to use.
     * @param score The score to use.
     */
    public Grasp(IndependenceTest test, Score score) {
        if (test == null && score == null) throw new IllegalArgumentException("Test and score cannot both be null.");
        this.test = test;
        this.score = score;
        this.variables = getVariables(test, score);
    }

    /**
     * Returns the graph implied by the discovered permutation.
     *
     * @param cpDag True if a CPDAG should be returned, false if a DAG should be returned.
     * @return This graph.
     */
//    @NotNull
//    public Graph getGraph(boolean cpDag) {
//        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
//        return this.scorer.getGraph(cpDag);
//    }

    /**
     * Retrieves a graph based on specified parameters.
     *
     * @param cpDag True if a CPDAG (Completed Partially Directed Acyclic Graph) should be returned.
     *              If false, a DAG (Directed Acyclic Graph) will be returned.
     * @return A non-null Graph object, either a CPDAG or DAG depending on the input parameter.
     */
    public @NotNull Graph getGraph(boolean cpDag) {
        return getGraph(cpDag, /*replicating*/ false);
    }

    /**
     * Returns the graph based on the specified parameters. If the parameter `replicating` is true,
     * a replicating graph is returned; otherwise, a regular graph is returned.
     *
     * @param cpDag      True if a CPDAG (Completed Partially Directed Acyclic Graph) should be returned,
     *                   false if a DAG (Directed Acyclic Graph) should be returned.
     * @param replicating True if a replicating graph, which applies a lag replication policy,
     *                    should be returned.
     * @return The generated graph, either a CPDAG or DAG, wrapped in a replicating graph
     *         if `replicating` is true.
     */
    public Graph getGraph(boolean cpDag, boolean replicating) {
        Graph g = this.scorer.getGraph(cpDag);
        return new ReplicatingGraph(g, new LagReplicationPolicy());
    }

    private List<Node> getVariables(IndependenceTest test, Score score) {
        if (test != null) {
            return new ArrayList<>(test.getVariables());
        } else {
            return new ArrayList<>(score.getVariables());
        }
    }

    /**
     * Given an initial permutation, 'order,' of the variables, searches for a best permutation of the variables by
     * rearranging the variables in 'order.'
     *
     * @param order The initial permutation.
     * @return The discovered permutation at the end of the procedure.
     * @throws InterruptedException if any
     */
    public List<Node> bestOrder(@NotNull List<Node> order) throws InterruptedException {
        if (seed != -1) {
            RandomUtil.getInstance().setSeed(seed);
        }

        long start = MillisecondTimes.timeMillis();
        order = new ArrayList<>(order);

        this.scorer = new TeyssierScorer(this.test, this.score);
        this.scorer.setUseRaskuttiUhler(this.useRaskuttiUhler);
        this.scorer.setKnowledge(knowledge);

        if (this.useRaskuttiUhler) {
            this.scorer.setUseScore(false);
            this.scorer.setUseRaskuttiUhler(true);
        } else {
            this.scorer.setUseScore(this.useScore && !(this.score instanceof GraphScore));
        }

        this.scorer.clearBookmarks();

        List<Node> bestPerm = null;
        double best = NEGATIVE_INFINITY;

        this.scorer.score(order);

        for (int r = 0; r < this.numStarts; r++) {
            if (Thread.currentThread().isInterrupted()) break;

            if ((r == 0 && !this.useDataOrder) || r > 0) {
                RandomUtil.shuffle(order);
            }

            this.start = MillisecondTimes.timeMillis();

            makeValidKnowledgeOrder(order);

            this.scorer.score(order);

            List<Node> perm = grasp(this.scorer);

            this.scorer.score(perm);

            if (this.scorer.score() > best) {
                best = this.scorer.score();
                bestPerm = perm;
            }
        }

        if (bestPerm == null) return null;

        this.scorer.score(bestPerm);

        long stop = MillisecondTimes.timeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().log("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().log("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return new ArrayList<>(bestPerm);
    }

    /**
     * Returns the number of edges in the DAG implied by the discovered permutation.
     *
     * @return This number.
     */
    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    /**
     * Sets the number of times the best order algorithm should be rerun with different starting permutations in search
     * of a best BIC scoring permutation.
     *
     * @param numStarts This number, if 1, it is run just once with the given starting permutation, if 2 or higher, it
     *                  is rerun subsequently with random initial permutations, and the best scoring discovered final
     *                  permutation is reported.
     * @see #setUseDataOrder(boolean)
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * True if the order of the variables in the data should be used for an initial best-order search, false if a random
     * permutation should be used. (Subsequence automatic best order runs will use random permutations.) This is
     * included so that the algorithm will be capable of outputting the same results with the same data without any
     * randomness.
     *
     * @param useDataOrder True if so
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Returns the variables.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        if (test != null) {
            this.test.setVerbose(verbose);
        }
    }

    /**
     * Sets the knowledge used in the search. The search is set up to honor all knowledge of forbidden or required
     * directed edges, and tiered knowledge.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Sets the maximum depth of the depth-first search that GRaSP performs while searching for a weakly increasing tuck
     * sequence that improves the score.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    /**
     * Sets the maximum depth at which uncovered tucks can be performed within the depth-first search of GRaSP.
     *
     * @param uncoveredDepth This depth.
     */
    public void setUncoveredDepth(int uncoveredDepth) {
        if (uncoveredDepth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    /**
     * Sets the maximum depth at which singular tucks can be performed within the depth-first search of GRaSP.
     *
     * @param nonSingularDepth This depth.
     */
    public void setNonSingularDepth(int nonSingularDepth) {
        if (nonSingularDepth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

    /**
     * True if the score should be used (if both a score and a test are provided), false if not.
     *
     * @param useScore True, if so.
     */
    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    /**
     * True if GRasP0 should be performed before GRaSP1 and GRaSP1 before GRaSP2. False if this ordering should not be
     * imposed.
     *
     * @param ordered True if the ordering should be imposed.
     */
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    /**
     * True if the Raskutti-Uhler method should be used, false if the Verma-Pearl method should be used.
     *
     * @param useRaskuttiUhler True if RU, false if VP.
     * @see #setNumStarts(int)
     */
    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;

        if (this.useRaskuttiUhler) {
            this.useScore = false;
        }
    }

    /**
     * Sets whether to allow internal randomness in the algorithm. Some steps in the algorithm do shuffling of variables
     * if this is set to true, to help avoid local optima. However, this randomness can lead to different results on
     * different runs of the algorithm, which may be undesirable.
     *
     * @param allowInternalRandomness True if internal randomness should be allowed, false otherwise. This is false by
     *                                default.
     */
    public void setAllowInternalRandomness(boolean allowInternalRandomness) {
        this.allowInternalRandomness = allowInternalRandomness;
    }

    /**
     * Checks if the given order violates the knowledge.
     *
     * @param order The order of nodes to check.
     * @return True if the order violates the knowledge, false otherwise.
     */
    private boolean violatesKnowledge(List<Node> order) {
        if (this.knowledge.isEmpty()) return false;

        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (this.knowledge.isRequired(order.get(i).getName(), order.get(j).getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Implements the GRaSP algorithm to search for a best permutation of variables based on a scorer.
     *
     * @param scorer The scorer used to evaluate the permutations.
     * @return A list of Node objects representing the discovered permutation at the end of the procedure.
     * @throws InterruptedException if any
     */
    private List<Node> grasp(@NotNull TeyssierScorer scorer) throws InterruptedException {
        scorer.clearBookmarks();
        List<int[]> depths = new ArrayList<>();

        // GRaSP-TSP
        if (this.ordered && this.uncoveredDepth != 0 && this.nonSingularDepth != 0) {
            depths.add(new int[]{this.depth < 1 ? Integer.MAX_VALUE : this.depth, 0, 0});
        }

        // GRaSP-ESP
        if (this.ordered && this.nonSingularDepth != 0) {
            depths.add(new int[]{this.depth < 1 ? Integer.MAX_VALUE : this.depth,
                    this.uncoveredDepth < 0 ? Integer.MAX_VALUE : this.uncoveredDepth, 0});
        }

        // GRaSP
        depths.add(new int[]{this.depth < 1 ? Integer.MAX_VALUE : this.depth,
                this.uncoveredDepth < 0 ? Integer.MAX_VALUE : this.uncoveredDepth,
                this.nonSingularDepth < 0 ? Integer.MAX_VALUE : this.nonSingularDepth});

        double sNew = scorer.score();
        double sOld;

        for (int[] depth : depths) {
            do {
                sOld = sNew;
                graspDfs(scorer, sOld, depth, 1, new HashSet<>(), new HashSet<>());
                sNew = scorer.score();
            } while (sNew > sOld);
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("# Edges = " + scorer.getNumEdges()
                                           + " Score = " + scorer.score()
                                           + " (GRaSP)"
                                           + " Elapsed " + ((MillisecondTimes.timeMillis() - this.start) / 1000.0 + " s"));
        }

        return scorer.getPi();
    }

    /**
     * Makes a valid knowledge order by rearranging the given list of nodes according to the knowledge constraints.
     *
     * @param order The initial order of nodes.
     */
    private void makeValidKnowledgeOrder(List<Node> order) {
        if (this.knowledge.isEmpty()) return;

        int index = 0;

        Set<String> tier = new HashSet<>(this.knowledge.getVariablesNotInTiers());
        for (int i = 0; i < order.size(); i++) {
            if (tier.contains(order.get(i).getName())) {
                Node x = order.remove(i);
                order.add(index++, x);
            }
        }

        for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
            tier = new HashSet<>(this.knowledge.getTier(i));
            for (int j = 0; j < order.size(); j++) {
                if (tier.contains(order.get(j).getName())) {
                    Node x = order.remove(j);
                    order.add(index++, x);
                }
            }
        }

        if (this.knowledge.isEmpty()) return;
        for (int i = 1; i < order.size(); i++) {
            String a = order.get(i).getName();
            for (int j = 0; j < i; j++) {
                String b = order.get(j).getName();
                if (this.knowledge.isRequired(a, b)) {
                    Node x = order.remove(i);
                    order.add(j, x);
                    break;
                }
            }
        }
    }

    /**
     * Executes a Depth-First Search (DFS) algorithm for graph traversal with certain parameters.
     *
     * @param scorer       the TeyssierScorer object that scores the graph
     * @param sOld         the original score before the DFS
     * @param depth        an array of integers representing the depth limits for certain conditions
     * @param currentDepth the current depth of the DFS traversal
     * @param tucks        a set of sets of nodes representing the tucks performed
     * @param dfsHistory   a set of sets of sets of nodes representing the DFS history
     * @throws InterruptedException if any
     */
    private void graspDfs(@NotNull TeyssierScorer scorer, double sOld, int[] depth, int currentDepth,
                          Set<Set<Node>> tucks, Set<Set<Set<Node>>> dfsHistory) throws InterruptedException {
        List<Node> vars = scorer.getPi();

        if (allowInternalRandomness) {
            shuffle(vars);
        }

        for (Node y : vars) {
            Set<Node> ancestors = scorer.getAncestors(y);
            List<Node> parents = new ArrayList<>(scorer.getParents(y));

            if (allowInternalRandomness) {
                shuffle(parents);
            }

            for (Node x : parents) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                boolean covered = scorer.coveredEdge(x, y);
                boolean singular = true;
                Set<Node> tuck = new HashSet<>();
                tuck.add(x);
                tuck.add(y);

                if (covered && tucks.contains(tuck)) continue;
                if (currentDepth > depth[1] && !covered) continue;

                int[] idcs = new int[]{scorer.index(x), scorer.index(y)};

                int i = idcs[0];
                scorer.bookmark(currentDepth);

                boolean first = true;
                List<Node> Z = new ArrayList<>(scorer.getOrderShallow().subList(i + 1, idcs[1]));
                Iterator<Node> zItr = Z.iterator();
                do {
                    if (first) {
                        scorer.moveTo(y, i);
                        first = false;
                    } else {
                        Node z = zItr.next();
                        if (ancestors.contains(z)) {
                            if (scorer.getParents(z).contains(x)) {
                                singular = false;
                            }
                            scorer.moveTo(z, i++);
                        }
                    }
                } while (zItr.hasNext());
//                scorer.updateScores(idcs[0], idcs[1]);

                if (currentDepth > depth[2] && !singular) {
                    scorer.goToBookmark(currentDepth);
                    continue;
                }

                if (violatesKnowledge(scorer.getPi())) {
                    scorer.goToBookmark(currentDepth);
                    continue;
                }

                double sNew = scorer.score();
                if (sNew > sOld) {
                    if (verbose) {
                        System.out.printf("Edges: %d \t|\t Score Improvement: %f \t|\t Tucks Performed: %s %s \n",
                                scorer.getNumEdges(), sNew - sOld, tucks, tuck);
                    }
                    return;
                }

                if (sNew == sOld && currentDepth < depth[0]) {
                    tucks.add(tuck);
                    if (currentDepth > depth[1]) {
                        if (!dfsHistory.contains(tucks)) {
                            dfsHistory.add(new HashSet<>(tucks));
                            graspDfs(scorer, sOld, depth, currentDepth + 1, tucks, dfsHistory);
                        }
                    } else {
                        graspDfs(scorer, sOld, depth, currentDepth + 1, tucks, dfsHistory);
                    }
                    tucks.remove(tuck);
                }

                if (scorer.score() > sOld) return;

                scorer.goToBookmark(currentDepth);
            }
        }
    }

    /**
     * Sets the seed for random number generation.
     *
     * @param seed The seed to set.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets the replicatingGraph flag to determine if graph replication is enabled.
     *
     * @param replicatingGraph the boolean value to set the replicatingGraph flag.
     *                         If true, replication is enabled; otherwise, it is disabled.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }
}

