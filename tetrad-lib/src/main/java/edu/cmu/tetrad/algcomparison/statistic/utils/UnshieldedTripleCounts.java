package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
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
public class UnshieldedTripleCounts {
    public UnshieldedTripleCounts(Graph truth, Graph est, int missingTruth, int missingEst) {
        Set<Set<Node>> trueTriangles = getTriangles((truth), 0);

        for (Set<Node> triangle : trueTriangles) {
            List<Node> nodes = new ArrayList<>(triangle);

            Node a = nodes.get(0);
            Node b = nodes.get(1);
            Node c = nodes.get(2);

            testOrder(truth, est, a, b, c);
            testOrder(truth, est, a, c, b);
            testOrder(truth, est, b, a, c);
        }
    }

    private void testOrder(Graph truth, Graph est, Node a, Node b, Node c) {
        if (est.isAdjacentTo(a, b) && est.isAdjacentTo(b, c) && !est.isAdjacentTo(a, c)) {
            int count = 0;

            if (!truth.getEdge(a, b).equals(est.getEdge(a, b))) {
                count++;
            }

            if (!truth.getEdge(b, c).equals(est.getEdge(b, c))) {
                count++;
            }

            boolean estCollider = est.isDirectedFromTo(a, b) && est.isDirectedFromTo(c, b);
            boolean truthCollider = truth.isDirectedFromTo(a, b) && truth.isDirectedFromTo(c, b);

            if (estCollider != truthCollider) {
                System.out.println("Flipped a = " + a + " b = " + b + " c = " + c + " error count = " + count + " truthCollider = " + truthCollider + " estCollider = " + estCollider);
            } else {
                System.out.println("Not flipped a = " + a + " b = " + b + " c = " + c + " error count = " + count + " truthCollider = " + truthCollider + " estCollider " + estCollider);
            }
        }
    }

    private Set<Set<Node>> getTriangles(Graph graph, int missing) {
        Set<Set<Node>> triangles = new HashSet<>();

        for (Node b : graph.getNodes()) {
            List<Node> adjb = graph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(adjb.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> _adj = GraphUtils.asList(choice, adjb);
                Node a = _adj.get(0);
                Node c = _adj.get(1);

                int _missing = 0;

                if (!graph.isAdjacentTo(a, b)) _missing++;
                if (!graph.isAdjacentTo(b, c)) _missing++;
                if (!graph.isAdjacentTo(a, c)) _missing++;

                if (_missing == missing) {
                    Set<Node> triangle = new HashSet<>();
                    triangle.add(a);
                    triangle.add(b);
                    triangle.add(c);
                    triangles.add(triangle);
                }
            }
        }

        return triangles;
    }
}
