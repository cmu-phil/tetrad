package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class ProportionDirectedPathsNotReversedEst implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "semi(X,Y,est)=>!semi(Y,X,true)";
    }

    @Override
    public String getDescription() {
        return "Proportion of semidirected(X, Y) in estimated graph for which there is no semidirected(Y, X) in true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = trueGraph.getNodes();
        int tp = 0;
        int fp = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.existsSemiDirectedPathFromTo(x, y)) {
                    if (!trueGraph.existsSemiDirectedPathFromTo(y, x)) {
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
