package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PcStableMax;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@Bootstrapping
public class PcStableMaxConcatenated implements MultiDataSetAlgorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private final IndependenceWrapper test;
    private final Algorithm externalGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcStableMaxConcatenated(final IndependenceWrapper test, final boolean compareToTrue) {
        this.test = test;
        this.compareToTrue = compareToTrue;
    }

    public PcStableMaxConcatenated(final IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(final List<DataModel> dataModels, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final List<DataSet> dataSets = new ArrayList<>();

            for (final DataModel dataModel : dataModels) {
                dataSets.add((DataSet) dataModel);
            }

            final DataSet dataSet = DataUtils.concatenate(dataSets);
            final PcStableMax search = new PcStableMax(
                    this.test.getTest(dataSet, parameters));
            search.setUseHeuristic(parameters.getBoolean(Params.USE_MAX_P_ORIENTATION_HEURISTIC));
            search.setMaxPathLength(parameters.getInt(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH));
            search.setKnowledge(this.knowledge);
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else {
            final PcStableMaxConcatenated pcStableMaxConcatenated = new PcStableMaxConcatenated(this.test, this.compareToTrue);

            final List<DataSet> datasets = new ArrayList<>();

            for (final DataModel dataModel : dataModels) {
                datasets.add((DataSet) dataModel);
            }
            final GeneralResamplingTest search = new GeneralResamplingTest(datasets, pcStableMaxConcatenated, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

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
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
        } else {
            final PcStableMaxConcatenated pcStableMaxConcatenated = new PcStableMaxConcatenated(this.test, this.compareToTrue);

            final List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
            final GeneralResamplingTest search = new GeneralResamplingTest(dataSets, pcStableMaxConcatenated, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

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
    public Graph getComparisonGraph(final Graph graph) {
        if (this.compareToTrue) {
            return new EdgeListGraph(graph);
        } else {
            return SearchGraphUtils.cpdagForDag(new EdgeListGraph(graph));
        }
    }

    @Override
    public String getDescription() {
        return "PC-Max (\"Peter and Clark\") on concatenating datasets using " + this.test.getDescription()
                + (this.externalGraph != null ? " with initial graph from "
                + this.externalGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new LinkedList<>();
        if (this.test != null) {
            parameters.addAll(this.test.getParameters());
        }
        parameters.add(Params.DEPTH);
        parameters.add(Params.USE_MAX_P_ORIENTATION_HEURISTIC);
        parameters.add(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH);

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.RANDOM_SELECTION_SIZE);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setCompareToTrue(final boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
    }
}
