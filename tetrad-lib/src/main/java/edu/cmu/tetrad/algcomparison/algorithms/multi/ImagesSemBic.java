package edu.cmu.tetrad.algcomparison.algorithms.multi;

import edu.cmu.tetrad.algcomparison.algorithms.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScoreImages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelection' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple values.
 *
 * @author jdramsey
 */
public class ImagesSemBic implements MultiDataSetAlgorithm {

    public ImagesSemBic() {
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        List<DataModel> dataModels = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataModels.add(dataSet);
        }

        edu.cmu.tetrad.search.Fgs2 search = new edu.cmu.tetrad.search.Fgs2(new SemBicScoreImages(dataModels));
        search.setFaithfulnessAssumed(true);

        return search.search();
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        return search(Collections.singletonList(dataSet), parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
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
        parameters.add("randomSelection");
        return parameters;
    }
}
