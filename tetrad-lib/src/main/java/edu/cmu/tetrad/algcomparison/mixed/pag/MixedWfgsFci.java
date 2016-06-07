package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.pitt.csb.mgm.MGM;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWfgsFci implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        WGfci fgs = new WGfci(ds);
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Graph g =  fgs.search();
        IndependenceTest test = new IndTestMixedLrt(ds, parameters.get("alpha").doubleValue());
        Fci pc = new Fci(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "WFGS-FCI: uses the output of WGFCI as an intial graph " +
                "for PC-Stable, using the Mixed LRT test.";
    }
}
