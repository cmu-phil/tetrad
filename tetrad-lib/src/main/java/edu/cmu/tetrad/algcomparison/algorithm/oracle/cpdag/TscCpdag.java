package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Peter/Clark algorithm (PC).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TSC-CPDAG",
        command = "tsc-cpdag",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class TscCpdag extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Pc.</p>
     */
    public TscCpdag() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        double alpha = parameters.getDouble(Params.FOFC_ALPHA);
        double ridge = parameters.getDouble(Params.REGULARIZATION_LAMBDA);
        int ess = parameters.getInt(Params.EXPECTED_SAMPLE_SIZE);
        boolean verbose = parameters.getBoolean(Params.VERBOSE);

        edu.cmu.tetrad.search.TscCpdag search = new edu.cmu.tetrad.search.TscCpdag((DataSet) dataModel, ess);
        search.setAlpha(alpha);
        search.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setEffectiveSampleSize(ess);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setRidge(ridge);
        search.setVerbose(verbose);
        return search.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Block-CPDAG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.FOFC_ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.NUM_STARTS);
        parameters.add(Params.EXPECTED_SAMPLE_SIZE);
        parameters.add(Params.REGULARIZATION_LAMBDA);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}
