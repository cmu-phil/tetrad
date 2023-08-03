package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.search.work_in_progress.FasLofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author josephramsey
 */
@Bootstrapping
public class FaskLofsConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private final Lofs.Rule rule;
    private Knowledge knowledge = new Knowledge();

    public FaskLofsConcatenated(Lofs.Rule rule) {
        this.rule = rule;
    }

    @Override
    public Graph search(List<DataModel> dataModels, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            List<DataSet> dataSets = new ArrayList<>();

            for (DataModel dataModel : dataModels) {
                dataSets.add((DataSet) dataModel);
            }

            DataSet dataSet = DataUtils.concatenate(dataSets);

            FasLofs search = new FasLofs(dataSet, this.rule);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            search.setKnowledge(this.knowledge);
            return getGraph(search);
        } else {
            FaskLofsConcatenated algorithm = new FaskLofsConcatenated(this.rule);

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataModels) {
                datasets.add((DataSet) dataModel);
            }
            GeneralResamplingTest search = new GeneralResamplingTest(
                    datasets,
                    algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(null);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        // Not used.
    }

    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }

    private Graph getGraph(FasLofs search) {
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet)), parameters);
        } else {
            FaskLofsConcatenated algorithm = new FaskLofsConcatenated(this.rule);

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setScoreWrapper(null);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "FAS followed by " + this.rule;
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.PENALTY_DISCOUNT);

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.RANDOM_SELECTION_SIZE);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
