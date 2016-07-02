package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcFgsCg implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(ds);
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.getInt("fgsDepth"));
        Graph g = fgs.search();
        IndependenceTest test = new IndTestScore(score);
        Cpc pc = new Cpc(test);
        pc.setInitialGraph(g);
        pc.setDepth(parameters.getInt("depth"));
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
//        return new Pc(new IndTestDSep(dag)).search();
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC using the graph from FGS as an initial graph, Conditional Gaussian BIC used throughout";
    }


    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
