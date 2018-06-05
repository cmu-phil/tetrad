package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs2;
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
public class LofsConcatenated implements MultiDataSetAlgorithm, HasKnowledge, PassesInGraph {
    static final long serialVersionUID = 23L;
    private final Lofs2.Rule rule;
    private RandomGraph initialGraph;
    private IKnowledge knowledge = new Knowledge2();

    public LofsConcatenated(Lofs2.Rule rule) {
        this.rule = rule;
    }

    public LofsConcatenated(Lofs2.Rule rule, RandomGraph initialGraph) {
        this.rule = rule;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters, Graph graph) {

        List<DataSet> centered = new ArrayList<>();

        for (DataModel dataSet : dataSets) {
            centered.add(DataUtils.center((DataSet) dataSet));
        }

        DataSet dataSet = DataUtils.concatenate(centered);
        edu.cmu.tetrad.search.FasLofs search = new edu.cmu.tetrad.search.FasLofs((DataSet) dataSet, rule);
        search.setDepth(parameters.getInt("depth"));
        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        search.setKnowledge(knowledge);
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "True graph oriented by " + rule + " using concatenated data";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("depth");
        parameters.add("penaltyDiscount");
        parameters.add("twoCycleAlpha");
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

    @Override
    public Graph search(List<DataModel> dataSet, Parameters parameters) {
        throw new UnsupportedOperationException();
    }
}
