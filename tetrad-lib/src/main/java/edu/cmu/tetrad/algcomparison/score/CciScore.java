package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.DaudinConditionalIndependence;
import edu.cmu.tetrad.search.IndTestDaudinConditionalIndependence;
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
        name = "Daudin Score",
        command = "daudin-score",
        dataType = {DataType.Continuous}
)
public class CciScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final IndTestDaudinConditionalIndependence cci = new IndTestDaudinConditionalIndependence(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        if (parameters.getInt("kernelType") == 1) {
            cci.setKernel(DaudinConditionalIndependence.Kernel.Gaussian);
        } else if (parameters.getInt("kernelType") == 2) {
            cci.setKernel(DaudinConditionalIndependence.Kernel.Epinechnikov);
        } else {
            throw new IllegalStateException("Kernel not configured.");
        }
        cci.setNumFunctions(parameters.getInt("numBasisFunctions"));
        cci.setKernelMultiplier(parameters.getDouble("kernelMultiplier"));

        if (parameters.getInt("basisType") == 1) {
            cci.setBasis(DaudinConditionalIndependence.Basis.Polynomial);
        } else if (parameters.getInt("basisType") == 2) {
            cci.setBasis(DaudinConditionalIndependence.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        return new ScoredIndTest(cci);
    }

    @Override
    public String getDescription() {
        return "Daudin Score";
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
        parameters.add("kernelType");
        parameters.add("kernelMultiplier");
        parameters.add("basisType");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
