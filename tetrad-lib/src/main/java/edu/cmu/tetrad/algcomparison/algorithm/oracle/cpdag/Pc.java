package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
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
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Peter/Clark algorithm (PC).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC",
        command = "pc",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Pc implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs {
    @Serial
    private static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    /**
     * <p>Constructor for Pc.</p>
     */
    public Pc() {
    }

    /**
     * <p>Constructor for Pc.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public Pc(IndependenceWrapper test) {
        this.test = test;
    }

    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
                DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                dataModel = timeSeries;
                knowledge = timeSeries.getKnowledge();
            }

            PcCommon.ConflictRule conflictRule = switch (parameters.getInt(Params.CONFLICT_RULE)) {
                case 1 -> PcCommon.ConflictRule.PRIORITIZE_EXISTING;
                case 2 -> PcCommon.ConflictRule.ORIENT_BIDIRECTED;
                case 3 -> PcCommon.ConflictRule.OVERWRITE_EXISTING;
                default ->
                        throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
            };

            PcCommon.PcHeuristicType pcHeuristicType = switch (parameters.getInt(Params.PC_HEURISTIC)) {
                case 0 -> PcCommon.PcHeuristicType.NONE;
                case 1 -> PcCommon.PcHeuristicType.HEURISTIC_1;
                case 2 -> PcCommon.PcHeuristicType.HEURISTIC_2;
                case 3 -> PcCommon.PcHeuristicType.HEURISTIC_3;
                default ->
                        throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
            };

            edu.cmu.tetrad.search.Pc search = new edu.cmu.tetrad.search.Pc(getIndependenceWrapper().getTest(dataModel, parameters));
            search.setUseMaxPHeuristic(parameters.getBoolean(Params.USE_MAX_P_HEURISTIC));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setMeekPreventCycles(parameters.getBoolean(Params.MEEK_PREVENT_CYCLES));
            search.setPcHeuristicType(pcHeuristicType);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(this.knowledge);
            search.setStable(parameters.getBoolean(Params.STABLE_FAS));
            search.setConflictRule(conflictRule);
            Graph graph = search.search();
            stampWithBic(graph, dataModel);
            return graph;
        } else {
            Pc pcAll = new Pc(this.test);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, pcAll, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.cpdagForDag(dag);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "PC using " + this.test.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.USE_MAX_P_HEURISTIC);
        parameters.add(Params.CONFLICT_RULE);
        parameters.add(Params.MEEK_PREVENT_CYCLES);
        parameters.add(Params.PC_HEURISTIC);
        parameters.add(Params.DEPTH);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /** {@inheritDoc} */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /** {@inheritDoc} */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
