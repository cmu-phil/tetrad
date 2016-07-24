package mycomparisons.experimental;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.GPc;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Experimental;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedGpcCg implements Algorithm, Experimental {
    public Graph search(DataSet dataSet, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(dataSet);
        GPc pc = new GPc(score);
        pc.setHeuristicSpeedup(true);
        pc.setFgsDepth(parameters.getInt("depth"));
        return pc.search();
    }

    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "GPC using the Conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("depth");
        return parameters;
    }
}
