package edu.cmu.tetrad.algcomparison.algorithms.myalgorithms;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.WFgs;

import java.util.ArrayList;
import java.util.List;

/**
 * LiNGAM.
 *
 * @author jdramsey
 */
public class Wfgs implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        WFgs alg = new WFgs(dataSet);
        alg.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return alg.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "WFGS (Whimsical FGS)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> paramters = new ArrayList<>();
        paramters.add("penaltyDiscount");
        return paramters;
    }
}
