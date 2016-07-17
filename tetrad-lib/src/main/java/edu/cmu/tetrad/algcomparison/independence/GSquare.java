package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestGSquare;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 * @author jdramsey
 */
public class GSquare implements IndTestWrapper {
    private DataSet dataSet = null;
    private IndependenceTest test = null;
    private Parameters parameters;

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        this.parameters = parameters;
        if (dataSet != this.dataSet) {
            this.dataSet = dataSet;
            this.test = new IndTestGSquare(dataSet, parameters.getDouble("alpha"));
        }
        return test;
    }

    @Override
    public String getDescription() {
        return "G Square test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return Collections.singletonList("alpha");
    }

}
