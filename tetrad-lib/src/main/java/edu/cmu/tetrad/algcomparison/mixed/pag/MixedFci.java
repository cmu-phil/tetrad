package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.IndTestMixedLrt;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFci implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
        Fci pc = new Fci(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "FCI using the Mixed LRT test";
    }
}
