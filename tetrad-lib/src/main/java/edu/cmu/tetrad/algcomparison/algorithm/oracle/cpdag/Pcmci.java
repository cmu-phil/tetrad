package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
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

/**
 * PCMCI wrapper for algcomparison. NOTE: Knowledge comes from TsUtils.createLagData(...) and is used inside Pcmci.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PCMCI",
        command = "pcmci",
        algoType = AlgType.forbid_latent_common_causes
)
public class Pcmci implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    private IndependenceWrapper test;
    // Kept for UI compatibility; the core Pcmci ignores this and uses lagged.getKnowledge().
    private Knowledge knowledge = new Knowledge();

    public Pcmci() {
    }

    public Pcmci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet raw)) {
            throw new IllegalArgumentException("PCMCI requires a DataSet.");
        }

        final int maxLag = Math.max(1, parameters.getInt(Params.TIME_LAG, 1));
        final int maxCondSize = parameters.getInt(Params.DEPTH, 3);
        final double alpha = parameters.getDouble(Params.ALPHA, 0.05);
        final boolean verbose = parameters.getBoolean(Params.VERBOSE, false);

        // Build lagged dataset (this also constructs consistent Knowledge for the lagged space).
        DataSet lagged = TsUtils.createLagData(raw, maxLag);

        // Build the test over the LAGGED variables.
        IndependenceTest indTest = getIndependenceWrapper().getTest(lagged, parameters);

        // Configure search. Pcmci will internally rebuild the lagged view and pull lagged.getKnowledge().
        edu.cmu.tetrad.search.Pcmci search = new edu.cmu.tetrad.search.Pcmci.Builder(raw, indTest)
                .maxLag(maxLag)
                .maxCondSize(maxCondSize)
                .alpha(alpha)
                .verbose(verbose)
                .collapseToLag0(false)
                .build();

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    @Override
    public String getDescription() {
        return "PCMCI using " + (this.test != null ? this.test.getDescription() : "configured test");
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.TIME_LAG);   // -> maxLag
        params.add(Params.DEPTH);      // -> maxCondSize
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        return params;
    }

    // HasKnowledge (kept for UI parity; not used by Pcmci)
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    // Independence wrapper plumbing
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}