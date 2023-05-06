package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NoSemidirectedRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NoSemidirected-Rec";
    }

    @Override
    public String getDescription() {
        return "Proportion of (X, Y) where if no semidirected path in true then also not in est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fn = 0;

        Graph cpdag = GraphUtilsSearch.cpdagForDag(trueGraph);

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (!cpdag.paths().existsSemiDirectedPathFromTo(x, y)) {
                    if (!estGraph.paths().existsSemiDirectedPathFromTo(x, y)) {
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
