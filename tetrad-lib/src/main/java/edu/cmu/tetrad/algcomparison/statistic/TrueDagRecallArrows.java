package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagRecallArrows implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "*->-Rec";
    }

    @Override
    public String getDescription() {
        return "Proportion of <Y, X> where there is no directed(Y, X) in the true for which and X*->Y in the estimated graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (!trueGraph.getPaths().isAncestorOf(y, x)) {
                    Edge edge2 = estGraph.getEdge(x, y);

                    if (edge2 != null) {
                        if (edge2.getProximalEndpoint(y) == Endpoint.ARROW) {
                            tp++;
                        } else {
                            fn++;
                        }
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
