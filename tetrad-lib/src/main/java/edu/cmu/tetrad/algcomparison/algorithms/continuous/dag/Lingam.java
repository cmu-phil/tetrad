package edu.cmu.tetrad.algcomparison.algorithms.continuous.dag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.SemBicScore;

import java.util.ArrayList;
import java.util.List;

/**
 * LiNGAM.
 * @author jdramsey
 */
public class Lingam implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.Lingam lingam = new edu.cmu.tetrad.search.Lingam();
        return lingam.search(dataSet);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    public String getDescription() {
        return "LiNGAM.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}
