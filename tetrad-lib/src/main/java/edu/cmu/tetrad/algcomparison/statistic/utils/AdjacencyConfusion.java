package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class AdjacencyConfusion {
    private int adjTp;
    private int adjFp;
    private int adjFn;
    private int adjTn;

    public AdjacencyConfusion(Graph truth, Graph est) {
        adjTp = 0;
        adjFp = 0;
        adjFn = 0;
        adjTn = 0;

        est = GraphUtils.replaceNodes(est, truth.getNodes());
        truth = GraphUtils.replaceNodes(truth, est.getNodes());

        List<Node> nodes = truth.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                boolean estAdj = est.isAdjacentTo(x, y);
                boolean truthAdj = truth.isAdjacentTo(x, y);

                if (estAdj && !truthAdj) {
                    adjFp++;
                }

                if (truthAdj && !estAdj) {
                    adjFn++;
                }

                if (truthAdj && estAdj) {
                    adjTp++;
                }

                if (!truthAdj && !estAdj) {
                    adjTn++;
                }
            }
        }
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
