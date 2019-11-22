package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class TriangleConfusion {
    private int tp;
    private int fp;
    private int fn;
    private int tn;
    private int numCoveringErrors;
    private int numUncoveringErrors;

    public TriangleConfusion(Graph truth, Graph est, int missingTruth, int missingEst) {
        Set<Set<Node>> trueTriangles = getTriangles((truth), missingTruth);
        Set<Set<Node>> estTriangles = getTriangles((est), missingEst);

        Set<Set<Node>> allTriangles = new HashSet<>(trueTriangles);
        allTriangles.addAll(estTriangles);

        tp = 0;
        fp = 0;
        fn = 0;

        numCoveringErrors = 0;
        numUncoveringErrors = 0;

        for (Set<Node> triangle : allTriangles) {
            if (estTriangles.contains(triangle) && !trueTriangles.contains(triangle)) {
                fp++;
            }

            if (trueTriangles.contains(triangle) && !estTriangles.contains(triangle)) {
                fn++;
            }

            if (trueTriangles.contains(triangle) && estTriangles.contains(triangle)) {
                tp++;
            }
        }

        tn = allTriangles.size() - trueTriangles.size();
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
