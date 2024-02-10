package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;

/**
 * No cyclic paths condition.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoCyclicPathsInMagCondition implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NoCyclicInMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the no cyclic paths condition passes in MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph mag = GraphTransforms.pagToMag(estGraph);

        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPathFromTo(n, n)) {
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
