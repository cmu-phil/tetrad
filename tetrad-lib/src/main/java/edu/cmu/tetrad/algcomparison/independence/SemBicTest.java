package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScoreImages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBicTest implements IndependenceWrapper {

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        List<DataModel> dataModels = new ArrayList<>();
        dataModels.add(dataSet);
        return new IndTestScore(new SemBicScoreImages(dataModels), parameters.getDouble("alpha"));
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
        return Collections.singletonList("alpha");
    }

}
