package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagRecallTails implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DTR";
    }

    @Override
    public String getDescription() {
        return "Precision for Tails (DTPT / (DTPT + DFNT) compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.isAncestorOf(x, y)) {
                    Edge edge2 = estGraph.getEdge(x, y);

                    if (edge2 != null) {
                        if (edge2.getProximalEndpoint(x) == Endpoint.TAIL) {
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
