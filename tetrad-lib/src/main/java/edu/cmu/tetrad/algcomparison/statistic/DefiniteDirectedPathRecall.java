package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class DefiniteDirectedPathRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DDPR";
    }

    @Override
    public String getDescription() {
        return "Proportion of DP(X, Y) in CPDAG(true) for which DP(X, Y) in est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fn = 0;

        List<Node> nodes = trueGraph.getNodes();
        Graph cpdag = GraphUtilsSearch.cpdagForDag(trueGraph);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (cpdag.paths().existsDirectedPathFromTo(x, y)) {
                    if (estGraph.paths().existsDirectedPathFromTo(x, y)) {
                        tp++;
                    } else {
                        fn++;
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
