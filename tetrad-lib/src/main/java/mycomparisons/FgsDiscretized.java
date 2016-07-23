package mycomparisons;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * FGS (the heuristic version).
 *
 * @author jdramsey
 */
public class FgsDiscretized implements Algorithm, TakesInitialGraph {
    private ScoreWrapper score;

    public FgsDiscretized(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataSet dataSet, Parameters parameters) {
        dataSet = dataSet.copy();

        Discretizer discretizer = new Discretizer(dataSet);

        List<Node> variables = dataSet.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            discretizer.equalIntervals(variables.get(i), parameters.getInt("numCategories"));
        }

        DataSet discreteData = discretizer.discretize();

        edu.cmu.tetrad.search.Fgs2 fgs = new edu.cmu.tetrad.search.Fgs2(score.getScore(discreteData, parameters));

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
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>(score.getParameters());
        parameters.add("numCategories");
        return parameters;
    }
}
