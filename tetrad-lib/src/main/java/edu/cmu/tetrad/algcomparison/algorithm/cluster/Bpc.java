package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BuildPureClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * BPC.
 *
 * @author jdramsey
 */
public class Bpc implements Algorithm, TakesInitialGraph, HasKnowledge, ClusterAlgorithm {
    static final long serialVersionUID = 23L;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Bpc() {}

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        ICovarianceMatrix cov = DataUtils.getCovMatrix(dataSet);
        double alpha = parameters.getDouble("alpha");

        boolean wishart = parameters.getBoolean("useWishart", true);
        TestType testType;

        if (wishart) {
            testType = TestType.TETRAD_WISHART;
        } else {
            testType = TestType.TETRAD_DELTA;
        }

        TestType purifyType = TestType.TETRAD_BASED;

        BuildPureClusters bpc = new BuildPureClusters(cov, alpha, testType, purifyType);
        bpc.setVerbose(parameters.getBoolean("verbose"));

        return bpc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
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
