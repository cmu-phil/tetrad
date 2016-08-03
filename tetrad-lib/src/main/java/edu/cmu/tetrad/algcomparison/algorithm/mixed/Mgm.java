package edu.cmu.tetrad.algcomparison.algorithm.mixed;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.MGM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jdramsey
 */
public class Mgm implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        MGM m = new MGM(ds, new double[]{
                parameters.getDouble("mgmParam1", .1),
                parameters.getDouble("mgmParam2", .1),
                parameters.getDouble("mgmParam3", .1)
        });
        return m.search();
    }

    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    public String getDescription() {
        return "Returns the output of the MGM (Mixed Graphical Model) algorithm (a Markov random field)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("mgmParam1", 0.01);
        parameters.put("mgmParam2", 0.01);
        parameters.put("mgmParam3", 0.01);
        return parameters;
    }
}