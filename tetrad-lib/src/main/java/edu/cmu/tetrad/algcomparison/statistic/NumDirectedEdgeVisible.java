package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NumDirectedEdgeVisible implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X->Y-Visible";
    }

    @Override
    public String getDescription() {
        return "Number of X-->Y for which X-->Y visible in true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        Graph pag = SearchGraphUtils.dagToPag(trueGraph);

        for (Edge edge : pag.getEdges()) {
            if (pag.defVisible(edge)) {
                tp++;
            }
        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
