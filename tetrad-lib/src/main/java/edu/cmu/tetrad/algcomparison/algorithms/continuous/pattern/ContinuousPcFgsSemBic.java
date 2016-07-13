package edu.cmu.tetrad.algcomparison.algorithms.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * PC usign the output of FGS as an initial graph. SEM BIC .
 */
public class ContinuousPcFgsSemBic implements Algorithm {

    @Override
    public Graph search(DataSet ds, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(ds));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Fgs2 fgs = new Fgs2(score);
        Graph g = fgs.search();
        IndependenceTest test = new IndTestScore(score);
        Pc pc = new Pc(test);
        pc.setInitialGraph(g);
        pc.setDepth(parameters.getInt("depth"));
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC using the graph from FGS as an initial graph, SEM BIC used throughout";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("depth");
        return parameters;
    }
}
