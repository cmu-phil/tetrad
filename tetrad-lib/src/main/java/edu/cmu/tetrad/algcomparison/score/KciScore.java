package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.ScoredIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.KCI;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CCI Score.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Kernal Independence Score",
        command = "kci-score",
        dataType = {DataType.Continuous}
)
public class KciScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final KCI kci = new KCI(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        kci.setApproximate(parameters.getBoolean("kciUseAppromation"));
        kci.setWidthMultiplier(parameters.getDouble("kernelMultiplier"));
        kci.setNumBootstraps(parameters.getInt("kciNumBootstraps"));
        kci.setThreshold(parameters.getDouble("thresholdForNumEigenvalues"));
        return new ScoredIndTest(kci);
    }

    @Override
    public String getDescription() {
        return "KCI Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("kciUseAppromation");
        parameters.add("alpha");
        parameters.add("kernelMultiplier");
        parameters.add("kciNumBootstraps");
        parameters.add("thresholdForNumEigenvalues");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
