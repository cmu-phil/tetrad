package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScoreImages;
import edu.cmu.tetrad.search.TsDagToPag;

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
public class ImagesSemBic implements MultiDataSetAlgorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public ImagesSemBic() {
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        List<DataModel> dataModels = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataModels.add(dataSet);
        }

        edu.cmu.tetrad.search.Fgs search = new edu.cmu.tetrad.search.Fgs(new SemBicScoreImages(dataModels));
        search.setFaithfulnessAssumed(true);
        IKnowledge knowledge = dataModels.get(0).getKnowledge();
        search.setKnowledge(knowledge);

        return search.search();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList(DataUtils.getContinuousDataSet(dataSet)), parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    @Override
    public String getDescription() {
        return "IMaGES for continuous variables (using the SEM BIC score)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new Fgs(new SemBicScore()).getParameters();
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
