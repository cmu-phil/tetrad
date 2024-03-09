package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Records the elapsed time of the algorithm in seconds. This is a placeholder, really; the elapsed time is calculated
 * by the comparison class and recorded if this statistic is used.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ElapsedCpuTime implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the ElapsedCpuTime class.
     */
    public ElapsedCpuTime() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "E-CPU";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Elapsed CPU Time in Seconds";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return Double.NaN; // This has to be handled separately.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1 - FastMath.tanh(0.001 * value);
    }
}
