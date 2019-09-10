package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class ColliderConfusion {
    private int adjTp;
    private int adjFp;
    private int adjFn;
    private int adjTn;

    public ColliderConfusion(Graph truth, Graph est) {
        Set<Triple> trueColliders = getColliders((truth));
        Set<Triple> estColliders = getColliders((est));

        Set<Triple> allColliders = new HashSet<>(trueColliders);
        allColliders.addAll(estColliders);

        adjTp = 0;
        adjFp = 0;
        adjFn = 0;

        for (Triple triple : allColliders) {
            if (estColliders.contains(triple) &&
                    !trueColliders.contains(triple)) {
                adjFp++;
            }

            if (trueColliders.contains(triple) &&
                    !estColliders.contains(triple)) {
                adjFn++;
            }

            if (trueColliders.contains(triple) &&
                    estColliders.contains(triple)) {
                adjTp++;
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

    public int getAdjTp() {
        return adjTp;
    }

    public int getAdjFp() {
        return adjFp;
    }

    public int getAdjFn() {
        return adjFn;
    }

    public int getAdjTn() {
        return adjTn;
    }

}
