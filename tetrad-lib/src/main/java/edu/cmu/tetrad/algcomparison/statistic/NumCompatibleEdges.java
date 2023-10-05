package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import static edu.cmu.tetrad.graph.GraphUtils.compatible;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumCompatibleEdges implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#CE";
    }

    @Override
    public String getDescription() {
        return "Number compatible X*-*Y";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addPagColoring(estGraph);

        Graph pag = GraphSearchUtils.dagToPag(trueGraph);

        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            Edge trueEdge = pag.getEdge(edge.getNode1(), edge.getNode2());

            if (compatible(edge, trueEdge)) {
                tp++;
            } else {
                fp++;
            }
        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
