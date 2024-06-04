package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;
import static java.lang.Double.NEGATIVE_INFINITY;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraspTol {
    private final List<Node> variables;
    private Score score;
    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private TeyssierScorer scorer;
    private long start;
    // flags
    private boolean useScore = true;
    private boolean usePearl;
    private boolean ordered;
    private boolean verbose;
    private int uncoveredDepth = 1;
    private int nonSingularDepth = 1;
    private int toleranceDepth;
    private boolean useDataOrder = true;
    private boolean allowRandomnessInsideAlgorithm;

    // other params
    private int depth = 4;
    private int numStarts = 1;

    /**
     * <p>Constructor for GraspTol.</p>
     *
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     */
    public GraspTol(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
    }

    /**
     * <p>Constructor for GraspTol.</p>
     *
     * @param test a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public GraspTol(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    /**
     * <p>Constructor for GraspTol.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     */
    public GraspTol(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    /**
     * <p>Constructor for GraspTol.</p>
     *
     * @param variables a {@link java.util.List} object
     */
    public GraspTol(List<Node> variables) {
        this.variables = variables;
    }

    /**
     * <p>bestOrder.</p>
     *
     * @param order a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public List<Node> bestOrder(@NotNull List<Node> order) {
        long start = MillisecondTimes.timeMillis();
        order = new ArrayList<>(order);

        this.scorer = new TeyssierScorer(this.test, this.score);
        this.scorer.setUseRaskuttiUhler(this.usePearl);

        if (this.usePearl) {
            this.scorer.setUseScore(false);
        } else {
            this.scorer.setUseScore(this.useScore && !(this.score instanceof GraphScore));
        }

        this.scorer.setKnowledge(this.knowledge);
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

//            betterMutation(scorer);
//            List<Node> perm  = scorer.getPi();

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
            TetradLogger.getInstance().log("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().log("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

    /**
     * <p>betterMutation.</p>
     *
     * @param scorer a {@link edu.cmu.tetrad.search.utils.TeyssierScorer} object
     */
    public void betterMutation(@NotNull TeyssierScorer scorer) {
        List<Node> pi = scorer.getPi();
        double s;
        double sp = scorer.score(pi);
        scorer.bookmark();

        do {
            s = sp;

            for (Node k : scorer.getPi()) {
                sp = NEGATIVE_INFINITY;
                int index = scorer.index(k);

                for (int j = index; j >= 0; j--) {
                    scorer.moveTo(k, j);
//                    tuck(k, j, scorer);

                    if (scorer.score() > sp) {
                        if (!violatesKnowledge(scorer.getPi())) {
                            sp = scorer.score();
                            scorer.bookmark();
                        }
                    }
                }

                scorer.goToBookmark();
                scorer.bookmark();

                for (int j = index; j < scorer.size(); j++) {
                    scorer.moveTo(k, j);
//                    tuck(k, j, scorer);

                    if (scorer.score() > sp) {
                        if (!violatesKnowledge(scorer.getPi())) {
                            sp = scorer.score();
                            scorer.bookmark();

                            if (verbose) {
                                System.out.println("# Edges = " + scorer.getNumEdges()
                                                   + " Score = " + scorer.score()
                                                   + " (betterMutation)"
                                                   + " Elapsed " + ((MillisecondTimes.timeMillis() - start) / 1000.0 + " sp"));
                            }
                        }
                    }
                }

                scorer.goToBookmark();
            }
        } while (sp > s);
    }

    private void tuck(Node k, int j, TeyssierScorer scorer) {
        if (j >= scorer.index(k)) return;
        List<Node> d2 = new ArrayList<>();
        for (int i = j + 1; i < scorer.index(k); i++) {
            d2.add(scorer.get(i));
        }

        List<Node> gammac = new ArrayList<>(d2);
        gammac.removeAll(scorer.getAncestors(k));

        Node first = null;

        if (!gammac.isEmpty()) {
            first = gammac.get(0);

            for (Node n : gammac) {
                if (scorer.index(n) < scorer.index(first)) {
                    first = n;
                }
            }
        }

        if (scorer.getParents(k).contains(scorer.get(j))) {
            if (first != null) {
                scorer.moveTo(scorer.get(j), scorer.index(first));
            }
//            scorer.moveTo(j, scorer.index(first));
            scorer.moveTo(k, j);
        }
    }


    /**
     * <p>getNumEdges.</p>
     *
     * @return a int
     */
    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((o1, o2) -> {
                if (o1.getName().equals(o2.getName())) {
                    return 0;
                } else if (this.knowledge.isRequired(o1.getName(), o2.getName())) {
                    return 1;
                } else if (this.knowledge.isRequired(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return 1;
                } else {
                    return 0;
                }
            });
        }
    }

    /**
     * <p>grasp.</p>
     *
     * @param scorer a {@link edu.cmu.tetrad.search.utils.TeyssierScorer} object
     * @return a {@link java.util.List} object
     */
    public List<Node> grasp(@NotNull TeyssierScorer scorer) {
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
                graspDfsTol(scorer, sOld, depth, 1, this.toleranceDepth, 0, new HashSet<>(), new HashSet<>());
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


    private void graspDfsTol(@NotNull TeyssierScorer scorer, double sOld, int[] depth, int currentDepth,
                             int tol, int tolCur,
                             Set<Set<Node>> tucks, Set<Set<Set<Node>>> dfsHistory) {
        List<Node> variables;

        if (this.allowRandomnessInsideAlgorithm) {
            variables = scorer.getShuffledVariables();
        } else {
            variables = scorer.getPi();
        }

        for (Node y : variables) {
            if (Thread.interrupted()) break;

            Set<Node> ancestors = scorer.getAncestors(y);
            List<Node> parents = new ArrayList<>(scorer.getParents(y));

            if (this.allowRandomnessInsideAlgorithm) {
                shuffle(parents);
            }

            for (Node x : parents) {
                if (Thread.interrupted()) break;

                boolean covered = scorer.coveredEdge(x, y);
                boolean singular = true;
                Set<Node> tuck = new HashSet<>();
                tuck.add(x);
                tuck.add(y);

                if (covered && tucks.contains(tuck)) continue;
                if (currentDepth > depth[1] && !covered) continue;

                int[] idcs = {scorer.index(x), scorer.index(y)};

                int i = idcs[0];
                scorer.bookmark(currentDepth);

                boolean first = true;
                List<Node> Z = new ArrayList<>(scorer.getOrderShallow().subList(i + 1, idcs[1]));
                Iterator<Node> zItr = Z.iterator();
                do {
                    if (first) {
                        scorer.moveTo(y, i);
//                        scorer.moveToNoUpdate(y, i);
                        first = false;
                    } else {
                        Node z = zItr.next();
                        if (ancestors.contains(z)) {
                            if (scorer.getParents(z).contains(x)) {
                                singular = false;
                            }
                            scorer.moveTo(z, i++);
//                            scorer.moveToNoUpdate(z, i++);
                        }
                    }
                } while (zItr.hasNext());
//                scorer.updateScores(idcs[0], idcs[1]);


                if (currentDepth > depth[2] && !singular) {
                    scorer.goToBookmark(currentDepth);
                    continue;
                }

                if (violatesKnowledge(scorer.getPi())) continue;

                double sNew = scorer.score();

                if (sNew > sOld) {
                    if (this.verbose) {
                        String s = String.format("Edges: %d \t|\t Score Improvement: %f \t|\t Tucks Performed: %s %s",
                                scorer.getNumEdges(), sNew - sOld, tucks, tuck);
                        TetradLogger.getInstance().log(s);

//                        System.out.printf("Edges: %d \t|\t Score Improvement: %f \t|\t Tucks Performed: %s %s \n",
//                                scorer.getNumEdges(), sNew - sOld, tucks, tuck);
                    }
                    return;
                } else if (sNew == sOld && currentDepth < depth[0]) {
                    tucks.add(tuck);
                    if (currentDepth > depth[1]) {
                        if (!dfsHistory.contains(tucks)) {
                            dfsHistory.add(new HashSet<>(tucks));
                            graspDfsTol(scorer, sOld, depth, currentDepth + 1, tol, tolCur, tucks, dfsHistory);
                        }
                    }
                    tucks.remove(tuck);
                } else if (sNew < sOld && currentDepth < depth[0] && tolCur < tol) {
                    tucks.add(tuck);
                    graspDfsTol(scorer, sOld, depth, currentDepth + 1, tol, tolCur + 1, tucks, dfsHistory);
                    tucks.remove(tuck);
                }

                if (scorer.score() > sOld) return;

                scorer.goToBookmark(currentDepth);
            }
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @param cpDag a boolean
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    @NotNull
    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

//        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
//        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    /**
     * <p>Setter for the field <code>numStarts</code>.</p>
     *
     * @param numStarts a int
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.test.setVerbose(verbose);
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * <p>Setter for the field <code>depth</code>.</p>
     *
     * @param depth a int
     */
    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    /**
     * <p>Setter for the field <code>uncoveredDepth</code>.</p>
     *
     * @param uncoveredDepth a int
     */
    public void setUncoveredDepth(int uncoveredDepth) {
        if (this.depth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    /**
     * <p>Setter for the field <code>nonSingularDepth</code>.</p>
     *
     * @param nonSingularDepth a int
     */
    public void setNonSingularDepth(int nonSingularDepth) {
        if (this.depth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

    /**
     * <p>Setter for the field <code>useScore</code>.</p>
     *
     * @param useScore a boolean
     */
    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * <p>Setter for the field <code>ordered</code>.</p>
     *
     * @param ordered a boolean
     */
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    /**
     * <p>setUseRaskuttiUhler.</p>
     *
     * @param usePearl a boolean
     */
    public void setUseRaskuttiUhler(boolean usePearl) {
        this.usePearl = usePearl;
    }

    /**
     * <p>Setter for the field <code>useDataOrder</code>.</p>
     *
     * @param useDataOrder a boolean
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * <p>Setter for the field <code>allowRandomnessInsideAlgorithm</code>.</p>
     *
     * @param allowRandomnessInsideAlgorithm a boolean
     */
    public void setAllowRandomnessInsideAlgorithm(boolean allowRandomnessInsideAlgorithm) {
        this.allowRandomnessInsideAlgorithm = allowRandomnessInsideAlgorithm;
    }

    /**
     * <p>Setter for the field <code>toleranceDepth</code>.</p>
     *
     * @param toleranceDepth a int
     */
    public void setToleranceDepth(int toleranceDepth) {
        this.toleranceDepth = toleranceDepth;
    }
}
