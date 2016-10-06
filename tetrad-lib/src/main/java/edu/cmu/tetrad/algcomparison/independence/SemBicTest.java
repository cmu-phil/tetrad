package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;

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
        SemBicScore score = new SemBicScore(DataUtils.getCovMatrix(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return new IndTestScore(score);
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
