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
public class MixedCpcLrtWGfci implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        WGfci fgs = new WGfci(ds);
//        fgs.setDepth(parameters.get("fgsDepth"));
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Graph g =  fgs.search();
        IndependenceTest test = new IndTestMixedLrt(ds, parameters.getDouble("alpha"));
        Cpc pc = new Cpc(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC with the mixed LRT test, using the output of WGFCI as an intial graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("alpha");
        return parameters;
    }
}
