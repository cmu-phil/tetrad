package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcWfgs implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        WFgs fgs = new WFgs(ds);
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Graph g =  fgs.search();
        ConditionalGaussianScore score = new ConditionalGaussianScore(ds);
        IndTestScore test = new IndTestScore(score);
        Cpc pc = new Cpc(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC with the conditional Gaussian score, using the output of WFGS as an intial graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscout");
        return parameters;
    }
}
