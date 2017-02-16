package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
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
public class CcdMaxConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();
    private IndependenceWrapper test;

    public CcdMaxConcatenated(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
//        List<DataModel> dataModels = new ArrayList<>();
//        IKnowledge knowledge = null;
//
//        for (DataSet dataSet : dataSets) {
//            dataSet = TimeSeriesUtils.createLagData(dataSet, parameters.getInt("numLags"));
//            dataModels.add(dataSet);
//
//            if (knowledge == null) {
//                knowledge = dataSet.getKnowledge();
//            }
//        }
//
//        SemBicScoreImages images = new SemBicScoreImages(dataModels);
//        images.setPenaltyDiscount(1);
//        IndependenceTest test = new IndTestScore(images);

        DataSet dataSet = DataUtils.concatenate(dataSets);
        dataSet = TimeSeriesUtils.createLagData(dataSet, parameters.getInt("numLags"));
        IKnowledge knowledge = dataSet.getKnowledge();

        IndependenceTest test = this.test.getTest(dataSet, parameters);
        edu.cmu.tetrad.search.CcdMax search = new edu.cmu.tetrad.search.CcdMax(test);
        search.setCollapseTiers(parameters.getBoolean("collapseTiers"));
        search.setOrientConcurrentFeedbackLoops(parameters.getBoolean("orientVisibleFeedbackLoops"));
        search.setDoColliderOrientations(parameters.getBoolean("doColliderOrientation"));
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
        search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
        search.setApplyOrientAwayFromCollider(parameters.getBoolean("applyR1"));
        search.setUseOrientTowardDConnections(parameters.getBoolean("orientTowardDConnections"));

        Graph search1 = search.search();



        return search1;
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
