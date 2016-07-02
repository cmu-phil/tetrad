package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestMixedLrt;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.MGM;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedPcsMgm implements Algorithm {
    public Graph search(DataSet ds, Parameters parameters) {
        MGM m = new MGM(ds, new double[]{
                parameters.getDouble("mgmParam1"),
                parameters.getDouble("mgmParam2"),
                parameters.getDouble("mgmParam3")
        });
        Graph gm = m.search();
        IndependenceTest indTest = new IndTestMixedLrt(ds, parameters.getDouble("alpha"));
        PcStable pcs = new PcStable(indTest);
        pcs.setDepth(-1);
        pcs.setInitialGraph(gm);
        pcs.setVerbose(false);
        return pcs.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "PC-Stable, using the Mixed LRT test, with the output of MGM as an intial graph";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
