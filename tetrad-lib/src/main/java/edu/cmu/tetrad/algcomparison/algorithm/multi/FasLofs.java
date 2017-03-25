package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
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
public class FasLofs implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private final Lofs2.Rule rule;
    private IKnowledge knowledge = new Knowledge2();

    public FasLofs(Lofs2.Rule rule) {
        this.rule = rule;
    }

    private Graph getGraph(edu.cmu.tetrad.search.FasLofs search) {
        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.FasLofs search = new edu.cmu.tetrad.search.FasLofs((DataSet) dataSet, rule);
        search.setDepth(parameters.getInt("depth"));
        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        search.setKnowledge(knowledge);
        return getGraph(search);
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
