package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
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
public class FangLofs implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private final Lofs2.Rule rule;
    private IKnowledge knowledge = new Knowledge2();

    public FangLofs(Lofs2.Rule rule) {
        this.rule = rule;
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        List<DataSet> _dataSets = new ArrayList<>();
        for (DataSet dataSet : dataSets) _dataSets.add(dataSet);
        edu.cmu.tetrad.search.FangLofs search = new edu.cmu.tetrad.search.FangLofs(_dataSets, rule);
        search.setDepth(parameters.getInt("depth"));
        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        search.setMaxCoef(parameters.getDouble("maxCoef"));
        search.setCorrelatedErrorsAlpha(parameters.getDouble("depErrorsAlpha"));
        search.setMarkDependentResidualsInGraph(parameters.getBoolean("markDependentResiduals"));
        search.setKnowledge(knowledge);
        return getGraph(search);
    }

    private Graph getGraph(edu.cmu.tetrad.search.FangLofs search) {
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
        return "FAS followed by " + rule;
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
        parameters.add("maxCoef");
        parameters.add("depErrorsAlpha");
        parameters.add("markDependentResiduals");

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
