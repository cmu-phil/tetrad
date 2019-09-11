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
    private int adjTn;
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

        setNumCoveringErrors(0);
        setNumUncoveringErrors(0);

        for (Triple triple : allColliders) {
            if (estColliders.contains(triple) &&
                    !trueColliders.contains(triple)) {
                fp++;

                if (est.isAdjacentTo(triple.getX(), triple.getZ()) && !truth.isAdjacentTo(triple.getX(), triple.getZ())) {
                    setNumCoveringErrors(getNumCoveringErrors() + 1);
                } else if (truth.isAdjacentTo(triple.getX(), triple.getZ()) && !est.isAdjacentTo(triple.getX(), triple.getZ())) {
                    setNumUncoveringErrors(getNumUncoveringErrors() + 1);
                }
            }

            if (trueColliders.contains(triple) &&
                    !estColliders.contains(triple)) {
                fn++;

                if (est.isAdjacentTo(triple.getX(), triple.getZ()) && !truth.isAdjacentTo(triple.getX(), triple.getZ())) {
                    setNumCoveringErrors(getNumCoveringErrors() + 1);
                } else if (truth.isAdjacentTo(triple.getX(), triple.getZ()) && !est.isAdjacentTo(triple.getX(), triple.getZ())) {
                    setNumUncoveringErrors(getNumUncoveringErrors() + 1);
                }
            }

            if (trueColliders.contains(triple) &&
                    estColliders.contains(triple)) {
                tp++;
            }
        }

        int allColliderCCount = allColliders.size();

        adjTn = allColliderCCount - trueColliders.size();
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

//                if (graph.isAdjacentTo(a, c)) continue;

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

    public int getAdjTn() {
        return adjTn;
    }

    public int getNumCoveringErrors() {
        return numCoveringErrors;
    }

    public void setNumCoveringErrors(int numCoveringErrors) {
        this.numCoveringErrors = numCoveringErrors;
    }

    public int getNumUncoveringErrors() {
        return numUncoveringErrors;
    }

    public void setNumUncoveringErrors(int numUncoveringErrors) {
        this.numUncoveringErrors = numUncoveringErrors;
    }
}
