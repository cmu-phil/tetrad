package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;

/**
 * No cyclic paths condition.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoCyclicPathsCondition implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NoCyclicPathsCondition() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NoCyclicPaths";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the no cyclic paths condition passes, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = estGraph;

        for (Node n : pag.getNodes()) {
            if (pag.paths().existsDirectedPath(n, n)) {
                return 0;
            }
        }

        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
