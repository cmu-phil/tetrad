package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class PossiblyDirectedPathPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PDPP";
    }

    @Override
    public String getDescription() {
        return "Proportion of PD(X, Y) in est for which DD(X, Y) in CPDAG(true)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fp = 0;

        List<Node> nodes = trueGraph.getNodes();
        Graph graph2 = new EdgeListGraph(trueGraph);

        GraphUtils.addPagColoring(estGraph);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                Edge e = estGraph.getEdge(x, y);

                if (e != null) {
                    if (e.pointsTowards(y) && e.getProperties().contains(Edge.Property.pd)) {
                        Edge e2 = graph2.getEdge(x, y);

                        if (e2 != null) {
//                            graph2.removeEdge(e2);

                            if (graph2.existsSemiDirectedPathFromTo(x, Collections.singleton(y)) && !graph2.existsDirectedPathFromTo(x, y)) {
                                tp++;
                            } else {
                                fp++;
                            }

//                            graph2.addEdge(e2);
                        }
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
