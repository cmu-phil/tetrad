package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FindOneFactorClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * FOFC.
 *
 * @author jdramsey
 */
public class Fofc implements Algorithm, TakesInitialGraph, HasKnowledge, ClusterAlgorithm {
    static final long serialVersionUID = 23L;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Fofc() {}

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(dataSet);
        double alpha = parameters.getDouble("alpha");

        boolean wishart = parameters.getBoolean("useWishart", true);
        TestType testType;

        if (wishart) {
            testType = TestType.TETRAD_WISHART;
        } else {
            testType = TestType.TETRAD_DELTA;
        }

        boolean gap = parameters.getBoolean("useGap", true);
        FindOneFactorClusters.Algorithm algorithm;

        if (gap) {
            algorithm = FindOneFactorClusters.Algorithm.GAP;
        } else {
            algorithm = FindOneFactorClusters.Algorithm.SAG;
        }

        edu.cmu.tetrad.search.FindOneFactorClusters search
                = new edu.cmu.tetrad.search.FindOneFactorClusters(cov, testType, algorithm, alpha);
        search.setVerbose(parameters.getBoolean("verbose"));

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "FOFC (Find One Factor Clusters)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("useWishart");
        parameters.add("useGap");
        parameters.add("verbose");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
