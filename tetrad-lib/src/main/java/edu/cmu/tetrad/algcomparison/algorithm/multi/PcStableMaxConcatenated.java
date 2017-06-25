package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PcStableMax;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
public class PcStableMaxConcatenated implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private boolean compareToTrue = false;
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public PcStableMaxConcatenated(IndependenceWrapper test, boolean compareToTrue) {
        this.test = test;
        this.compareToTrue = compareToTrue;
    }

    public PcStableMaxConcatenated(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(List<DataModel> dataModels, Parameters parameters) {
        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel dataModel : dataModels) {
            dataSets.add((DataSet) dataModel);
        }

        DataSet dataSet = DataUtils.concatenate(dataSets);
        PcStableMax search = new PcStableMax(
                test.getTest(dataSet, parameters));
        search.setUseHeuristic(parameters.getBoolean("useMaxPOrientationHeuristic"));
        search.setMaxPathLength(parameters.getInt("maxPOrientationMaxPathLength"));
        search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
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
        return "PC-Max (\"Peter and Clark\") on concatenating datasets using " + test.getDescription()
                + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("useMaxPOrientationHeuristic");
        parameters.add("maxPOrientationMaxPathLength");

        parameters.add("numRuns");
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

    public void setCompareToTrue(boolean compareToTrue) {
        this.compareToTrue = compareToTrue;
    }
}
