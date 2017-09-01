package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.IndTestDescription;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@IndTestDescription(name = "conditional-correlation", description = "Conditional Correlation Test", dataType = DataType.Continuous)
public class ConditionalCorrelation implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestConditionalCorrelation(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
    }

    @Override
    public String getDescription() {
        return "Conditional correlation test";
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
