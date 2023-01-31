package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * @author jdramsey
 */
public class NonancestorRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "Nonanc-Rec";
    }

    @Override
    public String getDescription() {
        return "Proportion of NOT X~~>Y in the true graph for which also NOT X~~>Y in estimated graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
//                if (x == y) continue;
                if (!trueGraph.getPaths().isAncestorOf(x, y)) {
                    if (!estGraph.getPaths().isAncestorOf(x, y)) {
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
