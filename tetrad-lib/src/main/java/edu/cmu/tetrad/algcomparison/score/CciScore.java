package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.ScoredIndTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CCI Score.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "CCI Score",
        command = "cci-score",
        dataType = {DataType.Continuous}
)
public class CciScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final double alpha = parameters.getDouble("alpha");
        IndTestConditionalCorrelation test = new IndTestConditionalCorrelation((DataSet) dataSet, alpha);
        test.setNumFunctions(parameters.getInt("numFunctions"));
        return new ScoredIndTest(test);
    }

    @Override
    public String getDescription() {
        return "CCI Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("numBasisFunctions");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
