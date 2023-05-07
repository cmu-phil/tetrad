package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;

/**
 * @author josephramsey
 */
public class NoCyclicPathsInMagCondition implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NoCyclicInMag";
    }

    @Override
    public String getDescription() {
        return "1 if the no cyclic paths condition passes in MAG, 0 if not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph mag = GraphUtilsSearch.pagToMag(estGraph);

        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPathFromTo(n, n)) {
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
