package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Math.abs;
import static java.util.Collections.sort;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Boss2 {
    private final List<Node> variables;
    private final Score score;
    private IKnowledge knowledge = new Knowledge2();
    private boolean verbose = true;

    public Boss2(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    /**
     * Prints local graphs for all variables and returns the one of them.
     */
    public Graph search(@NotNull List<Node> order) {
        long start = System.currentTimeMillis();
        order = new ArrayList<>(order);
        Map<Node, Set<Node>> keeps = new HashMap<>();

        TeyssierScorer2 scorer0 = new TeyssierScorer2(this.score);
        scorer0.setKnowledge(this.knowledge);
        makeValidKnowledgeOrder(order);
        scorer0.score(order);

        if (verbose) {
            System.out.println("Initial score = " + scorer0.score() + " Elapsed = " + (System.currentTimeMillis() - start) / 1000.0 + " s");
        }

        List<Node> pi1, pi2 = scorer0.getPi();

        do {
            int count = 1;
            pi1 = pi2;

            List<Node> targets = scorer0.getPi();

//            targets.sort(Comparator.comparingInt(o -> scorer0.getParents(o).size()));

            for (Node target : targets) {
                System.out.print("\r" + count++);
                betterMutationBossTarget(scorer0, target, keeps);
            }

            System.out.println();

            pi2 = scorer0.getPi();

            if (verbose) {
                System.out.println("# vars = " + scorer0.getPi().size() + " # Edges = " + scorer0.getNumEdges()
                        + " Score = " + scorer0.score() + " (betterMutationBoss3)" + " Elapsed "
                        + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
            }
        } while (!pi1.equals(pi2));

        long stop = System.currentTimeMillis();

        System.out.println("Elapsed time = " + (stop - start) / 1000.0 + " s");

        return scorer0.getGraph(true);
    }

    public void betterMutationBossTarget(@NotNull TeyssierScorer2 scorer, Node target, Map<Node, Set<Node>> keeps) {
        double sp = scorer.score();

        Set<Node> keep = getKeep(scorer, target);

        if (keep.equals(keeps.get(target))) return;

        System.out.print("\t" + keep.size());

        keeps.put(target, keep);

        scorer.bookmark();

        for (Node x : keep) {
            int i = scorer.index(x);

            for (int j = i - 1; j >= 0; j--) {
                if (!keep.contains(scorer.get(j))) continue;
//                if (!scorer.adjacent(x, scorer.get(j))) continue;

                if (tuck(x, j, scorer)) {
                    if (scorer.score() > sp && !violatesKnowledge(scorer.getPi())) {
                        sp = scorer.score();
                        scorer.bookmark();
                    } else {
                        scorer.goToBookmark();
                    }
                }
            }
        }
    }

    @NotNull
    private static Set<Node> getKeep(@NotNull TeyssierScorer2 scorer, Node target) {
        Set<Node> keep = new HashSet<>();
        keep.add(target);
        keep.addAll(scorer.getAdjacentNodes(target));
        return keep;
    }

    @NotNull    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
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

    private boolean tuck(Node k, int j, TeyssierScorer2 scorer) {
        if (!scorer.adjacent(k, scorer.get(j))) return false;
        int _k = scorer.index(k);
        if (j >= _k) return false;
//        if (abs(_k - j) > 50) return false;
        int _j = j;

        Set<Node> ancestors = scorer.getAncestors(k);

        for (int i = j + 1; i <= _k; i++) {
            if (ancestors.contains(scorer.get(i))) {
                scorer.moveToNoUpdate(scorer.get(i), j++);
            }
        }

        scorer.updateScores(_j, _k);

        return true;
    }
}