package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcWfgs implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        WFgs fgs = new WFgs(ds);
//        fgs.setDepth(parameters.get("fgsDepth").intValue());
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Graph g =  fgs.search();
        IndependenceTest test = new IndTestMixedLrt(ds, parameters.get("alpha").doubleValue());
        Cpc pc = new Cpc(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC with the mixed LRT test, using the output of WFGS as an intial graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
