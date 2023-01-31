package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.List;

/**
 * @author jdramsey
 */
public class NoAlmostCyclicPathsCondition implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NoAlmostCyclic";
    }

    @Override
    public String getDescription() {
        return "1 if the no almost cyclic paths condition passes, 0 if not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = estGraph;

        for (Edge e : pag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (pag.getPaths().existsDirectedPathFromTo(x, y)) {
                    return 0;
                } else if (pag.getPaths().existsDirectedPathFromTo(y, x)) {
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
