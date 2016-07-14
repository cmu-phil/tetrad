package edu.cmu.tetrad.algcomparison.algorithms.oracle.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndTestChooser;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Conserative FCI using the Fisher Z independence test.
 * @author jdramsey
 */
public class Cfci implements Algorithm {
    private IndTestType type;

    public Cfci(IndTestType type) {
        this.type = type;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestChooser().getTest(type, dataSet, parameters);
        edu.cmu.tetrad.search.Cfci pc = new edu.cmu.tetrad.search.Cfci(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "CFCI, using the Fisher Z test.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
