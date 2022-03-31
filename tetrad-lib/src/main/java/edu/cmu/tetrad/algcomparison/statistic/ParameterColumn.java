package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
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

    private final String parameter;

    /**
     * @param parameter The name of the parameter to list. If this parameter
     *                  does not exist, '*' is output.
     */
    public ParameterColumn(String parameter) {
        this.parameter = parameter;
    }

    @Override
    public String getAbbreviation() {
        return this.parameter;
    }

    @Override
    public String getDescription() {
        return "Extra column for " + this.parameter;
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getNormValue(double value) {
        throw new UnsupportedOperationException();
    }
}
