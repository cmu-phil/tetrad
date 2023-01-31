package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * @author jdramsey
 */
public class NoAlmostCyclicPathsInMagCondition implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NoAlmostCyclicInMag";
    }

    @Override
    public String getDescription() {
        return "1 if the no almost cyclic paths condition passes in MAG, 0 if not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph mag = SearchGraphUtils.pagToMag(estGraph);

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (mag.existsDirectedPathFromTo(x, y)) {
                    return 0;
                } else if (mag.existsDirectedPathFromTo(y, x)) {
                    return 0;
                }
            }
        }

        return 1;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
