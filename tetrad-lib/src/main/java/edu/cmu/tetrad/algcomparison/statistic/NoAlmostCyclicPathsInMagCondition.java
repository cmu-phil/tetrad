package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

/**
 * No almost cyclic paths condition in MAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoAlmostCyclicPathsInMagCondition implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NoAlmostCyclicInMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the no almost cyclic paths condition passes in MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph mag = GraphTransforms.pagToMag(estGraph);

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (mag.paths().existsDirectedPathFromTo(x, y)) {
                    return 0;
                } else if (mag.paths().existsDirectedPathFromTo(y, x)) {
                    return 0;
                }
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
