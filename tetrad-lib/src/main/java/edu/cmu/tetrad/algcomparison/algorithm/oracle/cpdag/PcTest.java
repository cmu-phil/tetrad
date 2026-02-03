package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * PC-Test
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC-Test",
        command = "pc_test",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PcTest extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();

    public PcTest() {
    }

    public PcTest(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataModel dm = dataModel;
        Knowledge k = new Knowledge(this.knowledge);

        // Time series lagging support (matches the PC wrapper pattern).
        int lag = parameters.getInt(Params.TIME_LAG);
        if (lag > 0) {
            if (!(dm instanceof DataSet ds)) {
                throw new IllegalArgumentException("Expecting a DataSet for time lagging.");
            }

            DataSet lagged = TsUtils.createLagData(ds, lag);
            if (ds.getName() != null) lagged.setName(ds.getName());

            dm = lagged;
            k = lagged.getKnowledge();
        }

        IndependenceTest indTest = test.getTest(dm, parameters);

        edu.cmu.tetrad.search.PcTest search = new edu.cmu.tetrad.search.PcTest(indTest);
        search.setKnowledge(k);
        search.setDepth(parameters.getInt(Params.DEPTH));

        Graph graph = search.search();
        stampWithBic(graph, dm);

        return graph;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "PC-Test using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.TIME_LAG_REPLICATING_GRAPH);
        return params;
    }

    @Override
    public Knowledge getKnowledge() {
        return new Knowledge(this.knowledge);
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}