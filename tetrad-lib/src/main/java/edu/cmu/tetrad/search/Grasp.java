package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

import static java.util.Collections.shuffle;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Grasp {
    private final List<Node> variables;
    private Score score;
    private IndependenceTest test;
    private IKnowledge knowledge = new Knowledge2();
    private TeyssierScorer scorer;
    private long start;
    // flags
    private boolean useScore = true;
    private boolean usePearl;
    private boolean ordered;
    private boolean verbose;
    private boolean cachingScores = true;
    private int uncoveredDepth = 1;
    private int nonSingularDepth = 1;
    private int toleranceDepth;
    private boolean useDataOrder = true;
    private boolean allowRandomnessInsideAlgorithm;

    // other params
    private int depth = 4;
    private int numStarts = 1;

    public Grasp(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
    }

    public Grasp(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    public Grasp(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    public List<Node> bestOrder(@NotNull List<Node> order) {
        long start = System.currentTimeMillis();
        order = new ArrayList<>(order);

        this.scorer = new TeyssierScorer(this.test, this.score);
        this.scorer.setUseVermaPearl(this.usePearl);

        if (this.usePearl) {
            this.scorer.setUseScore(false);
        } else {
            this.scorer.setUseScore(this.useScore && !(this.score instanceof GraphScore));
        }

        this.scorer.setKnowledge(this.knowledge);
        this.scorer.clearBookmarks();

        this.scorer.setCachingScores(this.cachingScores);

        List<Node> bestPerm = null;
        double best = Double.NEGATIVE_INFINITY;

        this.scorer.score(order);

        for (int r = 0; r < this.numStarts; r++) {
            if (Thread.interrupted()) break;

            if ((r == 0 && !this.useDataOrder) || r > 0) {
                shuffle(order);
            }

            this.start = System.currentTimeMillis();

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

        long stop = System.currentTimeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

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
                } else if (this.knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return 1;
                } else {
                    return 1;
                }
            });
        }
    }

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
            TetradLogger.getInstance().forceLogMessage("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (GRaSP)"
                    + " Elapsed " + ((System.currentTimeMillis() - this.start) / 1000.0 + " s"));
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
//                        scorer.moveTo(y, i);
                        scorer.moveToNoUpdate(y, i);
                        first = false;
                    } else {
                        Node z = zItr.next();
                        if (ancestors.contains(z)) {
                            if (scorer.getParents(z).contains(x)) {
                                singular = false;
                            }
//                            scorer.moveTo(z, i++);
                            scorer.moveToNoUpdate(z, i++);
                        }
                    }
                } while (zItr.hasNext());
                scorer.updateScores(idcs[0], idcs[1]);


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
                        TetradLogger.getInstance().forceLogMessage(s);

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

    @NotNull
    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    public void setCacheScores(boolean cachingScores) {
        this.cachingScores = cachingScores;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.test.setVerbose(verbose);
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    public void setUncoveredDepth(int uncoveredDepth) {
        if (this.depth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    public void setNonSingularDepth(int nonSingularDepth) {
        if (this.depth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

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

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    public void setUseRaskuttiUhler(boolean usePearl) {
        this.usePearl = usePearl;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setAllowRandomnessInsideAlgorithm(boolean allowRandomnessInsideAlgorithm) {
        this.allowRandomnessInsideAlgorithm = allowRandomnessInsideAlgorithm;
    }

    public void setToleranceDepth(int toleranceDepth) {
        this.toleranceDepth = toleranceDepth;
    }
}