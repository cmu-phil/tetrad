package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StructuralHammingDistance implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "SHD";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Structural Hamming Distance";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.GraphComparison comparison = GraphSearchUtils.getGraphComparison(trueGraph, estGraph);
        return comparison.getShd();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(0.001 * value);
    }
}
