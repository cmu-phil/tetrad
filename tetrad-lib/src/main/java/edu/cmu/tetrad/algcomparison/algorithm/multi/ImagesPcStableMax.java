package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStableMax;
import edu.cmu.tetrad.search.SemBicScoreImages3;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
public class ImagesPcStableMax implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public ImagesPcStableMax() {
    }

    @Override
    public Graph search(List<DataModel> dataModels, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            List<DataSet> dataSets = new ArrayList<>();

            for (DataModel dataModel : dataModels) {
                dataSets.add((DataSet) dataModel);
            }

            SemBicScoreImages3 score = new SemBicScoreImages3(dataSets);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            IndependenceTest test = new IndTestScore(score);
            PcStableMax search = new PcStableMax(test);
            search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
            search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
            search.setKnowledge(knowledge);
            search.setDepth(parameters.getInt("depth"));
            return search.search();
    	}else{
    		ImagesPcStableMax imagesPcStableMax = new ImagesPcStableMax();
    		//imagesPcStableMax.setKnowledge(knowledge);
    		
    		List<DataSet> datasets = new ArrayList<>();

			for (DataModel dataModel : dataModels) {
				datasets.add((DataSet) dataModel);
			}
			GeneralBootstrapTest search = new GeneralBootstrapTest(datasets, imagesPcStableMax,
					parameters.getInt("bootstrapSampleSize"));
            search.setKnowledge(knowledge);

			BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
			switch (parameters.getInt("bootstrapEnsemble", 1)) {
			case 0:
				edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
				break;
			case 1:
				edgeEnsemble = BootstrapEdgeEnsemble.Highest;
				break;
			case 2:
				edgeEnsemble = BootstrapEdgeEnsemble.Majority;
			}
			search.setEdgeEnsemble(edgeEnsemble);
			search.setParameters(parameters);
			search.setVerbose(parameters.getBoolean("verbose"));
			return search.search();
    	}
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
    	if (!parameters.getBoolean("bootstrapping")) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
    	}else{
    		ImagesPcStableMax imagesPcStableMax = new ImagesPcStableMax();
    		imagesPcStableMax.setKnowledge(knowledge);
    		
    		List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
			GeneralBootstrapTest search = new GeneralBootstrapTest(dataSets, imagesPcStableMax,
					parameters.getInt("bootstrapSampleSize"));

			BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
			switch (parameters.getInt("bootstrapEnsemble", 1)) {
			case 0:
				edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
				break;
			case 1:
				edgeEnsemble = BootstrapEdgeEnsemble.Highest;
				break;
			case 2:
				edgeEnsemble = BootstrapEdgeEnsemble.Majority;
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
        return "PC-Max using the IMaGES score for continuous variables";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");

        parameters.add("depth");
        parameters.add("orientVisibleFeedbackLoops");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
        parameters.add("applyR1");
        parameters.add("orientTowardDConnections");

        parameters.add("numRuns");
        parameters.add("randomSelectionSize");
        // Bootstrapping
  		parameters.add("bootstrapSampleSize");
  		parameters.add("bootstrapEnsemble");
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
