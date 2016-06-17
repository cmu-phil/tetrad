package edu.cmu.tetrad.algcomparison.discrete.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteCfci implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestChiSquare(dataSet, parameters.get("alpha").doubleValue());
        Cfci pc = new Cfci(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "CFCI ising the Chi Square test";
    }
}
