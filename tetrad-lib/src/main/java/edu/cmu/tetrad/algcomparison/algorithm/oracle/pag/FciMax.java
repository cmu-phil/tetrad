package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;

/**
 * FCI.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCI-Max",
        command = "fci-max",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class FciMax implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    public FciMax() {
    }

    public FciMax(IndependenceWrapper test) {
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
//            }

            edu.cmu.tetrad.search.FciMax search = new edu.cmu.tetrad.search.FciMax(this.test.getTest(dataModel, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(this.knowledge);
            search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
            search.setPossibleMsepSearchDone(parameters.getBoolean(Params.POSSIBLE_MSEP_DONE));
//            search.setPcHeuristicType(pcHeuristicType);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            FciMax algorithm = new FciMax(this.test);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return dagToPag(new EdgeListGraph(graph));
    }

    public String getDescription() {
        return "FCI-Max (Fast Causal Inference Max P-value) using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DO_DISCRIMINATING_PATH_RULE);
        parameters.add(Params.POSSIBLE_MSEP_DONE);
//        parameters.add(Params.PC_HEURISTIC);
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
