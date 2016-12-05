package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLRT;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.graph.Graph;

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
//        List<DataModel> dataModels = new ArrayList<>();

        List<Node> origVars = new ArrayList<>(dataSets.get(0).getVariables());

//        DataSet dataSet0 = dataSets.get(0);
//        List<Node> variables = dataSet0.getVariables();
//        DiscreteVariable S = new DiscreteVariable("S", dataSets.size());
//        variables.add(S);
//
//        DataSet concatenated = new BoxDataSet(new MixedDataBox(variables, dataSet0.getNumRows() * dataSets.size()), variables);
//
//        for (int k = 0; k < dataSets.size(); k++) {
//            DataSet dataSet = dataSets.get(k);
//
//            for (int i = 0; i < dataSet.getNumRows(); i++) {
//                for (int j = 0; j < dataSet.getNumColumns(); j++) {
//                    concatenated.setDouble(k * dataSet.getNumRows() + i, j, dataSet.getDouble(i, j));
//                }
//
//                concatenated.setInt(k * dataSet.getNumRows() + i, concatenated.getColumn(S), k);
//            }
//
////            dataModels.add(dataSet);
//        }

//        final ConditionalGaussianScore score = new ConditionalGaussianScore(concatenated);
//        score.setPenaltyDiscount(4);
//        edu.cmu.tetrad.search.Fgs search = new edu.cmu.tetrad.search.Fgs(score);

//        final IndTestConditionalGaussianLRT score = new IndTestConditionalGaussianLRT(concatenated, 0.0001);
//        edu.cmu.tetrad.search.PcMax search = new edu.cmu.tetrad.search.PcMax(score);
//
        final SemBicScoreImages3 score = new SemBicScoreImages3(dataSets);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        edu.cmu.tetrad.search.Fgs search = new edu.cmu.tetrad.search.Fgs(score);
        search.setMaxDegree(parameters.getInt("maxDegree"));

        Graph g = search.search();

//        g.removeNode(g.getNode("S"));

        g = GraphUtils.replaceNodes(g, origVars);

        return g;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        return search(Collections.singletonList(DataUtils.getContinuousDataSet(dataSet)), parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
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
