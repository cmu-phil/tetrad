package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Adds a column to the output table in which values for the given parameter are listed. The parameter must have
 * numerical values, and these will be represented as continuous.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ParameterColumn implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the parameter to list. If this parameter does not exist, '*' is output.
     */
    private final String parameter;

    /**
     * <p>Constructor for ParameterColumn.</p>
     *
     * @param parameter The name of the parameter to list. If this parameter does not exist, '*' is output.
     */
    public ParameterColumn(String parameter) {
        this.parameter = parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Extra column for " + this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        throw new UnsupportedOperationException();
    }
}
