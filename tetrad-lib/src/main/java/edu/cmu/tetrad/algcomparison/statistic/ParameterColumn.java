package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.Graph;

/**
 * Adds a column to the output table in which values for the given parameter
 * are listed. The parameter must have numerical values, and these will be
 * represented as continuous.
 *
 * @author jdramsey
 */
public class ParameterColumn implements Statistic {
    static final long serialVersionUID = 23L;

    private String parameter;

    /**
     * @param parameter The name of the parameter to list. If this parameter
     *                  does not exist, '*' is output.
     */
    public ParameterColumn(String parameter) {
        this.parameter = parameter;
    }

    @Override
    /**
     * Returns the name of the parameter.
     */
    public String getAbbreviation() {
        return parameter;
    }

    @Override
    /**
     * Return "Extra column for " + parameter".
     */
    public String getDescription() {
        return "Extra column for " + parameter;
    }

    @Override
    /**
     * This value is obtained by the Comparison class internally.
     * @throws UnsupportedOperationException
     */
    public double getValue(Graph trueGraph, Graph estGraph) {
        throw new UnsupportedOperationException();
    }

    @Override
    /**
     * This is not a column that can be included in a utility calculations. This
     * will thow an exception.
     * @throws UnsupportedOperationException
     */
    public double getNormValue(double value) {
        throw new UnsupportedOperationException();
    }
}
