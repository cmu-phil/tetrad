package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFciLrtWfgs implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        WFgs fgs = new WFgs(ds);
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        Graph g = fgs.search();
        IndependenceTest test = new IndTestMixedLrt(ds, parameters.getDouble("alpha"));
        Fci pc = new Fci(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "FCI, using the Mixed LRT test, using the output of WFGS as an intial graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
