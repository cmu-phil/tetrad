package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndTestPositiveCorr;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class PositiveCorr implements IndependenceWrapper {
    static final long serialVersionUID = 23L;
    private double alpha = 0.001;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble("alpha");
        this.alpha = alpha;

        if (dataSet instanceof DataSet) {
            return new IndTestPositiveCorr((DataSet) dataSet, alpha);
        }

        throw new IllegalArgumentException("Expecting a data set.");
    }

    @Override
    public String getDescription() {
        return "Fisher Z test, alpha = " + alpha;
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }
}
