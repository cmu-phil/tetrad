package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * @author jdramsey
 */
public class NoCyclicPathsCondition implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NoCyclic";
    }

    @Override
    public String getDescription() {
        return "1 if the no cyclic paths condition passes, 0 if not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = estGraph;

        for (Node n : pag.getNodes()) {
            if (pag.paths().existsDirectedPathFromTo(n, n)) {
                return 0;
            }
        }

        return 1;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
