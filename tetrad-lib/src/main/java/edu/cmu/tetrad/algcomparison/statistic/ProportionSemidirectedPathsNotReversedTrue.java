package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * @author jdramsey
 */
public class ProportionSemidirectedPathsNotReversedTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "semi(X,Y,true)==>!semi(Y,X,est)";
    }

    @Override
    public String getDescription() {
        return "Proportion of semi(X, Y) in true graph for which there is no semi(Y, Z) in estimated graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = trueGraph.getNodes();
        int tp = 0;
        int fn = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.existsSemiDirectedPathFromTo(x, y)) {
                    if (!estGraph.existsSemiDirectedPathFromTo(y, x)) {
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
