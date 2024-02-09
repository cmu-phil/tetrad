package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumColoredPL implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "#X->Y-Colored-PL";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Number of X-->Y in est where colored PL in Est";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        GraphUtils.addPagColoring(estGraph);

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                if (edge.getProperties().contains(Edge.Property.pl)) {
                    count++;
                }
            }
        }

        return count;
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
