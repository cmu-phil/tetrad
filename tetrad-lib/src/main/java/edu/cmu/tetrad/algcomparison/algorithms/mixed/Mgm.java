package edu.cmu.tetrad.algcomparison.algorithms.mixed;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.MGM;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class Mgm implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        MGM m = new MGM(ds, new double[]{
                parameters.getDouble("mgmParam1"),
                parameters.getDouble("mgmParam2"),
                parameters.getDouble("mgmParam3")
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
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("mgmParam1");
        parameters.add("mgmParam2");
        parameters.add("mgmParam3");
        return parameters;
    }
}