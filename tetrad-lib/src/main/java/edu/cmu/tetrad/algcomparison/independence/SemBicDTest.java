package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test. Ignore this for now
 *
 * @author jdramsey
 */
public class SemBicDTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((ICovarianceMatrix) dataSet));
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
