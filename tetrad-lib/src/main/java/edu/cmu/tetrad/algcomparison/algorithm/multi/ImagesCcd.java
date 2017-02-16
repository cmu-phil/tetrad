package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;

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
public class ImagesCcd implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public ImagesCcd() {
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        List<DataModel> dataModels = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataModels.add(dataSet);
        }

        SemBicScoreImages score = new SemBicScoreImages(dataModels);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);
        edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
        search.setCollapseTiers(parameters.getBoolean("collapseTiers"));
        search.setOrientConcurrentFeedbackLoops(parameters.getBoolean("orientVisibleFeedbackLoops"));
        search.setDoColliderOrientations(parameters.getBoolean("doColliderOrientation"));
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
        search.setDepth(parameters.getInt("depth"));
        search.setApplyOrientAwayFromCollider(parameters.getBoolean("applyR1"));
        search.setUseOrientTowardDConnections(parameters.getBoolean("orientTowardDConnections"));
        search.setKnowledge(knowledge);
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList(DataUtils.getContinuousDataSet(dataSet)), parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CCD-Max using the IMaGEs score for continuous variables";
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
        parameters.add("doColliderOrientation");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");
        parameters.add("applyR1");
        parameters.add("orientTowardDConnections");
        parameters.add("assumeIID");
        parameters.add("collapseTiers");

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
