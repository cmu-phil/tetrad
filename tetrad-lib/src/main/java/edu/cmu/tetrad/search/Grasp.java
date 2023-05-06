package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.util.Collections.shuffle;


/**
 * <p>Implements the GRaSP algorithms, with various execution flags. GRaSP can use
 * either a score or an independence test; you can provide both, though if you do
 * you need to use the paremeters to choose which one will be used. The score
 * options is more scalable and accurate, though the independence option is
 * perhaps a little easier ot deal with theoretically.</p>
 * <p>Reference:</p>
 * <p>Lam, W. Y., Andrews, B., & Ramsey, J. (2022, August). Greedy relaxations of
 * the sparsest permutation algorithm. In Uncertainty in Artificial Intelligence
 * (pp. 1052-1062). PMLR.</p>
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Grasp {
    private final List<Node> variables;
    private Score score;
    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private TeyssierScorer scorer;
    private long start;
    private boolean useScore = true;
    private boolean useRaskuttiUhler;
    private boolean ordered;
    private boolean verbose;
    private int uncoveredDepth = 1;
    private int nonSingularDepth = 1;
    private boolean useDataOrder = true;
    private int depth = 3;
    private int numStarts = 1;

    /**
     * Constructor for a score.
     *
     * @param score The score to use.
     */
    public Grasp(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
    }

    /**
     * Constructor for a test.
     *
     * @param test The test to use.
     */
    public Grasp(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    /**
     * Constructor that takes both a test and a score; only one is used--
     * the parameter setting will decide which.
     *
     * @param test  The test to use.
     * @param score The score to use.
     */
    public Grasp(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    /**
     * Given an initial permutation, 'order', of the variables, searches
     * for a best permutation of the variables by rearranging the varialbes
     * in 'order'.
     *
     * @param order The initial permutation.
     * @return The discovered permutation at the end of the procedure.
     */
    public List<Node> bestOrder(@NotNull List<Node> order) {
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
            if (Thread.interrupted()) break;

            if ((r == 0 && !this.useDataOrder) || r > 0) {
                shuffle(order);
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

        this.scorer.score(bestPerm);

        long stop = MillisecondTimes.timeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

    /**
     * Returns the number of edges in the DAG implied by the discovered permuttion.
     *
     * @return This number.
     */
    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    /**
     * Returns the graph implied by the discovered permutation.
     *
     * @param cpDag True if a CPDAG should be returned, false if a DAG should be returned.
     * @return This graph.
     */
    @NotNull
    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    /**
     * Sets the number of times the best order algorithm should be rerun with different
     * starting permtutions in search of a best BIC scoring permutation.
     *
     * @param numStarts This number; if 1, it is run just once with the given
     *                  starting permutation; if 2 or higher, it is rerun subsequently
     *                  with random initial permutations and the best scoring
     *                  discovered final permutation is reported.
     * @see #setUseDataOrder(boolean)
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * True if the order of the variables in the data should be used for an initial
     * best-order search, false if a random permutation should be used. (Subsequence
     * automatic best order runs will use random permutations.) This is included
     * so that the algorithm will be capable of outputting the same results with the
     * same data without any randomness.
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
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        if (test != null) {
            this.test.setVerbose(verbose);
        }
    }

    /**
     * Sets the knowledge used in the search. The search is set up to honor all
     * knowledge of forbidden or required directed edges, and tiered knowledge.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Sets the maximum depth of the depth first search that GRaSP perform while
     * searching for a weakly increasing tuck sequence that improves the score.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    /**
     * Sets the maximum depth at which uncovered tucks can be performed within
     * the depth first search of GRaSP.
     *
     * @param uncoveredDepth This depth.
     */
    public void setUncoveredDepth(int uncoveredDepth) {
        if (uncoveredDepth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    /**
     * Sets the maximum depth at which singular tucks can be performed within
     * the depth first search of GRaSP.
     *
     * @param nonSingularDepth This depth.
     */
    public void setNonSingularDepth(int nonSingularDepth) {
        if (nonSingularDepth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

    /**
     * True if the score should be used (if both a score and a test are provided),
     * false if not.
     *
     * @param useScore True if so.
     */
    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    /**
     * True if GRasP0 should be performed before GRaSP1 and GRaSP1 before GRaSP2.
     * False if this ordering should not be imposed.
     *
     * @param ordered True if the ordering should be imposed.
     */
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    /**
     * True if the Raskutti-Uhler method should be used, false if the Verma-Pearl
     * method should be used.
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

    private List<Node> grasp(@NotNull TeyssierScorer scorer) {
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
            TetradLogger.getInstance().forceLogMessage("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (GRaSP)"
                    + " Elapsed " + ((MillisecondTimes.timeMillis() - this.start) / 1000.0 + " s"));
        }

        return scorer.getPi();
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((a, b) -> {
                if (a.getName().equals(b.getName())) return 0;
                else if (this.knowledge.isRequired(a.getName(), b.getName())) return -1;
                else if (this.knowledge.isRequired(b.getName(), a.getName())) return 1;
                else return 0;
            });
        }
    }


    private void graspDfs(@NotNull TeyssierScorer scorer, double sOld, int[] depth, int currentDepth,
                          Set<Set<Node>> tucks, Set<Set<Set<Node>>> dfsHistory) {
        for (Node y : scorer.getShuffledVariables()) {
            Set<Node> ancestors = scorer.getAncestors(y);
            List<Node> parents = new ArrayList<>(scorer.getParents(y));
            shuffle(parents);
            for (Node x : parents) {

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
}