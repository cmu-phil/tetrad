package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
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
public class FangConcatenated2 implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private boolean empirical = false;
    private boolean rskew = false;
    private IKnowledge knowledge = new Knowledge2();

    public FangConcatenated2() {
        this.empirical = false;
    }

    public FangConcatenated2(boolean empirical, boolean rskew) {
        this.empirical = empirical;
        this.rskew = rskew;
    }

    @Override
    public Graph search(List<DataModel> dataModels,  Parameters parameters) {
        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel dataModel : dataModels) {
            dataSets.add((DataSet) dataModel);
        }

        List<DataSet> centered = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            centered.add(DataUtils.center(dataSet));
        }

        DataSet dataSet = DataUtils.concatenate(centered);
        edu.cmu.tetrad.search.Fang search = new edu.cmu.tetrad.search.Fang(dataSet);
        search.setEmpirical(empirical);
        search.setRskew(rskew);
        search.setDepth(parameters.getInt("depth"));
        search.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        search.setAlpha(parameters.getDouble("twoCycleAlpha"));
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
        return "FANG (Fast Adjacency search followed by Non-Gaussian orientation)"
                + (empirical ? " (Empirical)" : "") + (rskew ? " (RSkew) " : "");
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
}
