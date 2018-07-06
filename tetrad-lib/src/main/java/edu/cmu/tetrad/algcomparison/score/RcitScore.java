package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Cci;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.ScoredIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.rcit.RandomizedConditionalIndependenceTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CCI Score.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "RCIT Score",
        command = "rcit-score",
        dataType = {DataType.Continuous}
)
public class RcitScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final RandomizedConditionalIndependenceTest rcit = new RandomizedConditionalIndependenceTest(DataUtils.getContinuousDataSet(dataSet));
        rcit.setAlpha(parameters.getDouble("alpha"));
        rcit.setNum_feature(parameters.getInt("rcitNumFeatures"));
        return new ScoredIndTest(rcit);
    }

    @Override
    public String getDescription() {
        return "RCIT Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("rcitNumFeatures");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
