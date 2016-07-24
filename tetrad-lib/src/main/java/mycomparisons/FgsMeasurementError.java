package mycomparisons;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * FGS, adding measurement noise to all variables in the (continuous) dataset.
 *
 * @author jdramsey
 */
public class FgsMeasurementError implements Algorithm, TakesInitialGraph {
    private ScoreWrapper score;

    public FgsMeasurementError(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataSet dataSet, Parameters parameters) {
        dataSet = dataSet.copy();

        double variance = parameters.getDouble("measurementVariance");

        if (variance > 0) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    dataSet.setDouble(i, j, dataSet.getDouble(i, j) + RandomUtil.getInstance().nextNormal(0, variance));
                }
            }
        }

        edu.cmu.tetrad.search.Fgs2 fgs = new edu.cmu.tetrad.search.Fgs2(score.getScore(dataSet, parameters));

        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "FGS (Fast Greedy Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>(score.getParameters());
        parameters.add("measurementVariance");
        return parameters;
    }
}
