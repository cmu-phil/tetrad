package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWfgsFci implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        WFgs fgs = new WFgs(ds);
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Graph g = fgs.search();
        IndependenceTest test = new IndTestMixedLrt(ds, parameters.get("alpha").doubleValue());
        Fci pc = new Fci(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "FCI, using the Mixed LRT test, using the output of WFGS as an intial graph";
    }
}
