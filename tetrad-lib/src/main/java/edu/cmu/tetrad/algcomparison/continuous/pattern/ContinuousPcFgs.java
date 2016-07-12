package edu.cmu.tetrad.algcomparison.continuous.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousPcFgs implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(ds));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.getInt("fgsDepth"));
        Graph g = fgs.search();
        IndependenceTest test = new IndTestScore(score);
        Pc pc = new Pc(test);
        pc.setInitialGraph(g);
        pc.setDepth(parameters.getInt("depth"));
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
//        return new Pc(new IndTestDSep(dag)).search();
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "PC using the graph from FGS as an initial graph, SEM BIC used throughout";
    }


    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
