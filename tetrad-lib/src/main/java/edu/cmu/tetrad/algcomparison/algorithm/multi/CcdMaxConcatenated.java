package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
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
public class CcdMaxConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
	static final long serialVersionUID = 23L;
	private IKnowledge knowledge = new Knowledge2();
	private IndependenceWrapper test;

	public CcdMaxConcatenated(IndependenceWrapper test) {
		this.test = test;
	}

	@Override
	public Graph search(List<DataModel> dataModels, Parameters parameters) {
		if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}

			DataSet dataSet = DataUtils.concatenate(dataSets);

			IndependenceTest test = this.test.getTest(dataSet, parameters);
			edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
			search.setDoColliderOrientations(parameters.getBoolean(Params.DO_COLLIDER_ORIENTATION));
			search.setUseHeuristic(parameters.getBoolean(Params.USE_MAX_P_ORIENTATION_HEURISTIC));
			search.setMaxPathLength(parameters.getInt(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH));
			search.setKnowledge(knowledge);
			search.setDepth(parameters.getInt(Params.DEPTH));
			search.setApplyOrientAwayFromCollider(parameters.getBoolean(Params.APPLY_R1));
			search.setUseOrientTowardDConnections(parameters.getBoolean(Params.ORIENT_TOWARD_DCONNECTIONS));
			return search.search();
		} else {
			CcdMaxConcatenated algorithm = new CcdMaxConcatenated(test);

			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}
			GeneralResamplingTest search = new GeneralResamplingTest(dataSets, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
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
		}else{
			CcdMaxConcatenated algorithm = new CcdMaxConcatenated(test);

			List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
			GeneralResamplingTest search = new GeneralResamplingTest(dataSets, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
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
	}

	@Override
	public String getDescription() {
		return "CCD-Max (Cyclic Discovery Search Max), concatenting datasets, using " + test.getDescription();
	}

	@Override
	public DataType getDataType() {
		return DataType.Continuous;
	}

	@Override
	public List<String> getParameters() {
		List<String> parameters = test.getParameters();
		parameters.add(Params.DEPTH);
		parameters.add(Params.ORIENT_VISIBLE_FEEDBACK_LOOPS);
		parameters.add(Params.DO_COLLIDER_ORIENTATION);
		parameters.add(Params.USE_MAX_P_ORIENTATION_HEURISTIC);
		parameters.add(Params.MAX_P_ORIENTATION_MAX_PATH_LENGTH);
		parameters.add(Params.APPLY_R1);
		parameters.add(Params.ORIENT_TOWARD_DCONNECTIONS);

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
