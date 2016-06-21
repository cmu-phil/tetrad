package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcFgs implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        ds = DataUtils.convertNumericalDiscreteToContinuous(ds);
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(ds));
        score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Fgs fgs = new Fgs(score);
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
        return "CPC with the mixed LRT test, using the output of FGS as input, treating all discrete as continuous";
    }
}
