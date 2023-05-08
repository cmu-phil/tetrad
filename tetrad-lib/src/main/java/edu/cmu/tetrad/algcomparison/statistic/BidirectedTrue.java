package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class BidirectedTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BT";
    }

    @Override
    public String getDescription() {
        return "Number of estimated bidirected edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);

        int t = 0;

        for (Edge edge : pag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) t++;
        }

        System.out.println("True # bidirected edges = " + t);

        return t;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
