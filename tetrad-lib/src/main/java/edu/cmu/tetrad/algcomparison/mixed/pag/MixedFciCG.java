package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * @author jdramsey
 */
public class MixedFciCG implements Algorithm {
    public Graph search(DataSet Dk, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(Dk);
        IndependenceTest test = new IndTestScore(score);
        Fci fgs = new Fci(test);
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    @Override
    public String getDescription() {
        return "FCI using the conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
