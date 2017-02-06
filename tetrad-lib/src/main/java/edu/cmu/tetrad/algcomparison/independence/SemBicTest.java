package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBicTest implements IndependenceWrapper {
    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix((DataSet) dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return new IndTestScore(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "SEM BIC test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("penaltyDiscount");
        return params;
    }
}
