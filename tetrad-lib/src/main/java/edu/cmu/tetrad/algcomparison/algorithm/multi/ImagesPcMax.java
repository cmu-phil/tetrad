package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScoreImages3;
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
public class ImagesPcMax implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public ImagesPcMax() {
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        SemBicScoreImages3 score = new SemBicScoreImages3(dataSets);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);
        edu.cmu.tetrad.search.PcMax search = new edu.cmu.tetrad.search.PcMax(test);
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
        search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
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

        parameters.add("numRandomSelections");
        parameters.add("randomSelectionSize");

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
