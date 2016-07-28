package edu.cmu.tetrad.algcomparison.algorithms.multi;

import edu.cmu.tetrad.algcomparison.algorithms.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BdeuScoreImages;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 *
 * @author jdramsey
 */
public class ImagesBDeu implements MultiDataSetAlgorithm {

    public ImagesBDeu() {
    }

    @Override
    public Graph search(List<DataSet> dataSets, Parameters parameters) {
        List<DataModel> dataModels = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataModels.add(dataSet);
        }

        edu.cmu.tetrad.search.Fgs2 search = new edu.cmu.tetrad.search.Fgs2(new BdeuScoreImages(dataModels));
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
        return "IMaGES for discrete variables (using the BDeu score)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        return new Fgs(new BdeuScore()).getParameters();
    }
}
