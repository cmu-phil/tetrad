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

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Conservative PC (CPC).
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CPC",
        command = "cpc",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Cpc implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs {

    private static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    public Cpc() {
    }

    public Cpc(IndependenceWrapper test) {
        this.test = test;
    }

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

            PcCommon.ConflictRule conflictRule;

            switch (parameters.getInt(Params.CONFLICT_RULE)) {
                case 1:
                    conflictRule = PcCommon.ConflictRule.PRIORITIZE_EXISTING;
                    break;
                case 2:
                    conflictRule = PcCommon.ConflictRule.ORIENT_BIDIRECTED;
                    break;
                case 3:
                    conflictRule = PcCommon.ConflictRule.OVERWRITE_EXISTING;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));

            }

//            PcCommon.PcHeuristicType pcHeuristicType;
//
//            switch (parameters.getInt(Params.PC_HEURISTIC)) {
//                case 0:
//                    pcHeuristicType = PcCommon.PcHeuristicType.NONE;
//                    break;
//                case 1:
//                    pcHeuristicType = PcCommon.PcHeuristicType.HEURISTIC_1;
//                    break;
//                case 2:
//                    pcHeuristicType =  PcCommon.PcHeuristicType.HEURISTIC_2;
//                    break;
//                case 3:
//                    pcHeuristicType =  PcCommon.PcHeuristicType.HEURISTIC_3;
//                    break;
//                default:
//                    throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
//
//            }

            edu.cmu.tetrad.search.Cpc search = new edu.cmu.tetrad.search.Cpc(getIndependenceWrapper().getTest(dataModel, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.meekPreventCycles(parameters.getBoolean(Params.MEEK_PREVENT_CYCLES));
//            search.setPcHeuristicType(pcHeuristicType);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(knowledge);
            search.setConflictRule(conflictRule);
            Graph graph = search.search();
            stampWithBic(graph, dataModel);
            return graph;
        } else {
            Cpc pcAll = new Cpc(this.test);

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

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.cpdagForDag(dag);
    }

    @Override
    public String getDescription() {
        return "CPC using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.CONFLICT_RULE);
        parameters.add(Params.MEEK_PREVENT_CYCLES);
        parameters.add(Params.DEPTH);
        parameters.add(Params.TIME_LAG);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
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

    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}