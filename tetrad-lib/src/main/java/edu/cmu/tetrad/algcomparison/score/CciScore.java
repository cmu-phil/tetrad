package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditionalCorrelationIndependence;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
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
        final IndTestConditionalCorrelation cci = new IndTestConditionalCorrelation(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("cciScoreAlpha"));
        if (parameters.getInt("kernelType") == 1) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Gaussian);
        } else if (parameters.getInt("kernelType") == 2) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Epinechnikov);
        } else {
            throw new IllegalStateException("Kernel not configured.");
        }
        cci.setNumFunctions(parameters.getInt("numBasisFunctions"));
        cci.setKernelMultiplier(parameters.getDouble("kernelMultiplier"));
        cci.setFastFDR(parameters.getBoolean("fastFDR"));
        cci.setKernelRegressionSampleSize(parameters.getInt("kernelRegressionSampleSize"));
        cci.setNumDependenceSpotChecks(parameters.getInt("numDependenceSpotChecks"));
        cci.setEarlyReturn(false);

        if (parameters.getInt("basisType") == 1) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Polynomial);
        } else if (parameters.getInt("basisType") == 2) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        return new ScoredIndTest(cci);
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
        parameters.add("cciScoreAlpha");
        parameters.add("numBasisFunctions");
        parameters.add("kernelType");
        parameters.add("kernelMultiplier");
        parameters.add("basisType");
        parameters.add("fastFDR");
        parameters.add("kernelRegressionSampleSize");
        parameters.add("numDependenceSpotChecks");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}