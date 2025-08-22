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

///**
// * Peter/Clark algorithm (PC).
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "TSC-PC",
//        command = "tsc-pc",
//        algoType = AlgType.forbid_latent_common_causes
//)
//@Bootstrapping
public class TscPc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
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
    public TscPc() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        boolean verbose = parameters.getBoolean(Params.VERBOSE);

        int _singletonPolicy = parameters.getInt(Params.TSC_SINGLETON_POLICY);
        edu.cmu.tetrad.search.TscPc.SingletonPolicy singletonPolicy = edu.cmu.tetrad.search.TscPc.SingletonPolicy.values()[_singletonPolicy - 1];

        edu.cmu.tetrad.search.TscPc search = new edu.cmu.tetrad.search.TscPc((DataSet) dataModel);
        search.setAlphaCluster(parameters.getDouble(Params.FOFC_ALPHA));
        search.setAlphaPc(parameters.getDouble(Params.ALPHA));
        search.setEffectiveSampleSize(parameters.getInt(Params.EXPECTED_SAMPLE_SIZE));
        search.setPcDepth(parameters.getInt(Params.DEPTH));
        search.setSingletonPolicy(singletonPolicy);
        search.setEnableHierarchy(parameters.getBoolean(Params.TSC_ENABLE_HIERARCHY));
        search.setMinRankDrop(parameters.getInt(Params.TSC_MIN_RANK_DROP));
        search.setEdgePolicy(edu.cmu.tetrad.search.TscPc.EdgePolicy.ALLOW_MEASURE_TO_MEASURE);
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
        return "TSC-PC";
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
        parameters.add(Params.ALPHA);
        parameters.add(Params.FOFC_ALPHA);
//        parameters.add(Params.EBIC_GAMMA);
//        parameters.add(Params.PENALTY_DISCOUNT);
//        parameters.add(Params.NUM_STARTS);
        parameters.add(Params.EXPECTED_SAMPLE_SIZE);
        parameters.add(Params.REGULARIZATION_LAMBDA);
//        parameters.add(Params.TSC_PC_USE_BOSS);
        parameters.add(Params.TSC_SINGLETON_POLICY);
        parameters.add(Params.TSC_ENABLE_HIERARCHY);
        parameters.add(Params.TSC_MIN_RANK_DROP);
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
