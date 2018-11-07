package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
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
public class CcdMaxConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
	static final long serialVersionUID = 23L;
	private IKnowledge knowledge = new Knowledge2();
	private IndependenceWrapper test;

	public CcdMaxConcatenated(IndependenceWrapper test) {
		this.test = test;
	}

	@Override
	public Graph search(List<DataModel> dataModels, Parameters parameters) {
		if (parameters.getInt("numberResampling") < 1) {
			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}

			DataSet dataSet = DataUtils.concatenate(dataSets);

			IndependenceTest test = this.test.getTest(dataSet, parameters);
			edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
			search.setDoColliderOrientations(parameters.getBoolean("doColliderOrientation"));
			search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
			search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
			search.setKnowledge(knowledge);
			search.setDepth(parameters.getInt("depth"));
			search.setApplyOrientAwayFromCollider(parameters.getBoolean("applyR1"));
			search.setUseOrientTowardDConnections(parameters.getBoolean("orientTowardDConnections"));
			return search.search();
		} else {
			CcdMaxConcatenated algorithm = new CcdMaxConcatenated(test);

			List<DataSet> dataSets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				dataSets.add((DataSet) dataModel);
			}
			GeneralResamplingTest search = new GeneralResamplingTest(dataSets, algorithm, parameters.getInt("numberResampling"));
            search.setKnowledge(knowledge);
            
            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
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
			search.setParameters(parameters);
			search.setVerbose(parameters.getBoolean("verbose"));
			return search.search();
		}
	}

	@Override
	public Graph search(DataModel dataSet, Parameters parameters) {
		if (parameters.getInt("numberResampling") < 1) {
			return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
		}else{
			CcdMaxConcatenated algorithm = new CcdMaxConcatenated(test);

			List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
			GeneralResamplingTest search = new GeneralResamplingTest(dataSets, algorithm, parameters.getInt("numberResampling"));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble("percentResampleSize"));
            search.setResamplingWithReplacement(parameters.getBoolean("resamplingWithReplacement"));
            
            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt("resamplingEnsemble", 1)) {
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
			search.setParameters(parameters);
			search.setVerbose(parameters.getBoolean("verbose"));
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
		parameters.add("depth");
		parameters.add("orientVisibleFeedbackLoops");
		parameters.add("doColliderOrientation");
		parameters.add("useMaxPOrientationHeuristic");
		parameters.add("maxPOrientationMaxPathLength");
		parameters.add("applyR1");
		parameters.add("orientTowardDConnections");

		parameters.add("numRuns");
		parameters.add("randomSelectionSize");

		// Resampling
        parameters.add("numberResampling");
        parameters.add("percentResampleSize");
        parameters.add("resamplingWithReplacement");
        parameters.add("resamplingEnsemble");
		parameters.add("verbose");

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
