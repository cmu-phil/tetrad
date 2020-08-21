package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScoreMultiFas;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * Wraps the MultiFask algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FaskVote",
        command = "fask-vote",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class FaskVote implements MultiDataSetAlgorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public FaskVote() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataSet> _dataSets = new ArrayList<>();
            for (DataModel d : dataSets) {
                _dataSets.add((DataSet) d);
            }
            final SemBicScoreMultiFas score = new SemBicScoreMultiFas(dataSets);
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            edu.cmu.tetrad.search.FaskVote search = new edu.cmu.tetrad.search.FaskVote(_dataSets, score);
            search.setParameters(parameters);
            search.setKnowledge(knowledge);
            return search.search();
        } else {
            FaskVote imagesSemBic = new FaskVote();

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                datasets.add((DataSet) dataModel);
            }
            GeneralResamplingTest search = new GeneralResamplingTest(datasets, imagesSemBic, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
        } else {
            FaskVote imagesSemBic = new FaskVote();

            List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets, imagesSemBic, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
//        return SearchGraphUtils.patternForDag(graph);
//        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    @Override
    public String getDescription() {
        return "MultiFASK";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        // MultiFask uses SemBicScore internally, so we'll need to add the score parameters too - Zhou
        List<String> parameters = new LinkedList<>();
        parameters.addAll((new Fges()).getParameters());
        parameters.add(SKEW_EDGE_THRESHOLD);
        parameters.add(TWO_CYCLE_SCREENING_THRESHOLD);
        parameters.add(TWO_CYCLE_TESTING_ALPHA);
        parameters.add(FASK_DELTA);
        parameters.add(FASK_LEFT_RIGHT_RULE);
        parameters.addAll((new SemBicScore()).getParameters());
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.RANDOM_SELECTION_SIZE);

        parameters.add(Params.VERBOSE);

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
