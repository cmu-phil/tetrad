package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * @author jdramsey
 */
public class MixedFciCG implements Algorithm {
    public Graph search(DataSet Dk, Map<String, Number> parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(Dk);
        IndependenceTest test = new IndTestScore(score);
        Fci fgs = new Fci(test);
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag2(dag).convert();
    }

    @Override
    public String getDescription() {
        return "FGS using the conditional Gaussian BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
