package edu.cmu.tetrad.algcomparison.algorithms.discrete.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CFCI using the Chi Square independence test.
 * @author jdramsey
 */
public class DiscreteCfciGs implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestGSquare(dataSet, parameters.getDouble("alpha"));
        Cfci pc = new Cfci(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "CFCI ising the Chi Square test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
