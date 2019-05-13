package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@Bootstrapping
public class FgesConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
	static final long serialVersionUID = 23L;
	private ScoreWrapper score;
	private IKnowledge knowledge = new Knowledge2();
	private Algorithm initialGraph = null;
	private boolean compareToTrue = false;

	public FgesConcatenated(ScoreWrapper score) {
		this.score = score;
	}

	public FgesConcatenated(ScoreWrapper score, boolean compareToTrue) {
		this.score = score;
		this.compareToTrue = compareToTrue;
	}

	public FgesConcatenated(ScoreWrapper score, Algorithm initialGraph) {
		this.score = score;
		this.initialGraph = initialGraph;
	}

	@Override
	public Graph search(List<DataModel> dataModels, Parameters parameters) {
		if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}

			DataSet dataSet = DataUtils.concatenate(dataSets);

			Graph initial = null;
			if (initialGraph != null) {

				initial = initialGraph.search(dataSet, parameters);
			}

			edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score.getScore(dataSet, parameters));
			search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
			search.setKnowledge(knowledge);
			search.setVerbose(parameters.getBoolean(Params.VERBOSE));
			search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));

			Object obj = parameters.get("printStedu.cmream");
			if (obj instanceof PrintStream) {
				search.setOut((PrintStream) obj);
			}

			if (initial != null) {
				search.setInitialGraph(initial);
			}

			return search.search();
		} else {
			FgesConcatenated fgesConcatenated = new FgesConcatenated(score, initialGraph);
			fgesConcatenated.setCompareToTrue(compareToTrue);

			List<DataSet> datasets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				datasets.add((DataSet) dataModel);
			}
			GeneralResamplingTest search = new GeneralResamplingTest(datasets, fgesConcatenated, parameters.getInt(Params.NUMBER_RESAMPLING));
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
			FgesConcatenated fgesConcatenated = new FgesConcatenated(score, initialGraph);
			fgesConcatenated.setCompareToTrue(compareToTrue);

			List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
			GeneralResamplingTest search = new GeneralResamplingTest(dataSets, fgesConcatenated, parameters.getInt(Params.NUMBER_RESAMPLING));
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
		if (compareToTrue) {
			return new EdgeListGraph(graph);
		} else {
			return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
		}
	}

	@Override
	public String getDescription() {
		return "FGES (Fast Greedy Equivalence Search) on concatenated data using " + score.getDescription();
	}

	@Override
	public DataType getDataType() {
		return DataType.Continuous;
	}

	@Override
	public List<String> getParameters() {
            List<String> parameters = new ArrayList<>();
            parameters.add(Params.FAITHFULNESS_ASSUMED);
            parameters.add(Params.MAX_DEGREE);

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

	/**
	 * @param compareToTrue
	 *            true if the result should be compared to the true graph, false
	 *            if to the pattern of the true graph.
	 */
	public void setCompareToTrue(boolean compareToTrue) {
		this.compareToTrue = compareToTrue;
	}
}
