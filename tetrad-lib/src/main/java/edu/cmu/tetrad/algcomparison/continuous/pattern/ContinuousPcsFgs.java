package edu.cmu.tetrad.algcomparison.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousPcsFgs implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(ds));
        score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.get("fgsDepth").intValue());
        Graph g = fgs.search();
        IndependenceTest test = new IndTestScore(score);
        PcStable pc = new PcStable(test);
        pc.setInitialGraph(g);
        pc.setDepth(parameters.get("depth").intValue());
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
//        return new Pc(new IndTestDSep(dag)).search();
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "PC-Stable using the graph from FGS as an initial graph, SEM BIC used throughout";
    }


    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
