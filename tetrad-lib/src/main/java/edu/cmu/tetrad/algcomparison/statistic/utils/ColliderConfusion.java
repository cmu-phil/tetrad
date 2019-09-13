package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class ColliderConfusion {
    private int tp;
    private int fp;
    private int fn;
    private int tn;
    private int numCoveringErrors;
    private int numUncoveringErrors;

    public ColliderConfusion(Graph truth, Graph est) {
        Set<Triple> trueColliders = getColliders((truth));
        Set<Triple> estColliders = getColliders((est));

        Set<Triple> allColliders = new HashSet<>(trueColliders);
        allColliders.addAll(estColliders);

        tp = 0;
        fp = 0;
        fn = 0;

        numCoveringErrors = 0;
        numUncoveringErrors = 0;

        for (Triple collider : allColliders) {
            if (estColliders.contains(collider) && !trueColliders.contains(collider)) {
                fp++;

                if (est.isAdjacentTo(collider.getX(), collider.getZ())
                        && !est.isAmbiguousTriple(collider.getX(), collider.getY(), collider.getZ())
                        && !truth.isAdjacentTo(collider.getX(), collider.getZ())) {
                    numCoveringErrors++;
                } else if (truth.isAdjacentTo(collider.getX(), collider.getZ())
                        && !est.isAmbiguousTriple(collider.getX(), collider.getY(), collider.getZ())
                        && !est.isAdjacentTo(collider.getX(), collider.getZ())) {
                    numUncoveringErrors++;
                }
            }

            if (trueColliders.contains(collider) && !estColliders.contains(collider)) {
                fn++;

                if (est.isAdjacentTo(collider.getX(), collider.getZ())
                        && !est.isAmbiguousTriple(collider.getX(), collider.getY(), collider.getZ())
                        && !truth.isAdjacentTo(collider.getX(), collider.getZ())) {
                    numCoveringErrors++;
                } else if (truth.isAdjacentTo(collider.getX(), collider.getZ())
                        && !est.isAmbiguousTriple(collider.getX(), collider.getY(), collider.getZ())
                        && !est.isAdjacentTo(collider.getX(), collider.getZ())) {
                    numUncoveringErrors++;
                }
            }

            if (trueColliders.contains(collider) &&
                    estColliders.contains(collider)) {
                tp++;
            }
        }

        tn = allColliders.size() - trueColliders.size();
    }

    private Set<Triple> getColliders(Graph graph) {
        Set<Triple> colliders = new HashSet<>();

        for (Node b : graph.getNodes()) {
            List<Node> adjb = graph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                if (graph.isDefCollider(a, b, c)) {
                    colliders.add(new Triple(a, b, c));
                }
            }
        }

        return colliders;
    }

    public int getTp() {
        return tp;
    }

    public int getFp() {
        return fp;
    }

    public int getFn() {
        return fn;
    }

    public int getTn() {
        return tn;
    }

    public int getNumCoveringErrors() {
        return numCoveringErrors;
    }

    public int getNumUncoveringErrors() {
        return numUncoveringErrors;
    }

}
