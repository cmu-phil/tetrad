package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * @author josephramsey
 */
public class NonancestorPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "Nonanc-Prec";
    }

    @Override
    public String getDescription() {
        return "Proportion of NOT X~~>Y in the estimated graph for which also NOT X~~>Y in true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
//                if (x == y) continue;
                if (!estGraph.paths().isAncestorOf(x, y)) {
                    if (!trueGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
