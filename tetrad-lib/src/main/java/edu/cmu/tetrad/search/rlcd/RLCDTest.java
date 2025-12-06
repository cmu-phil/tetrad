package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

public class RLCDTest {

    @Test
    public void testPhase1() {
        RLCDParams params = new RLCDParams();
        params.setStage1Method(RLCDParams.Stage1Method.FGES);
        params.setStage1GesSparsity(2.0);
        params.setStage1PartitionThreshold(3);
        params.setStages(1);  // until latent phases are ready

        params.setRankTestFactory(new RankTestFactory() {
            @Override
            public RankTest create(DataSet dataSet) {
                SimpleMatrix cov = new CovarianceMatrix(dataSet).getMatrix().getSimpleMatrix();

                return (pCols, qCols, k, alpha) -> {
                    int estimatedRank = RankTests.estimateWilksRank(cov, pCols, qCols, k, alpha);
                    return (estimatedRank <= k);
                };
            }
        });

        Graph graph = RandomGraph.randomGraph(10, 2, 10, 100, 100, 100, false);
        SemPm pm =  new SemPm(graph);
        SemIm im =  new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);

        RLCD rlcd = new RLCD(dataSet, params);
        Graph gStage1 = rlcd.search(true);       // Phase 1 skeleton
        Graph gFull   = rlcd.search(false);

        Phase1.runPhase1(dataSet, params);// currently same as skeleton

        RLCDParams p = new RLCDParams();
        p.setStage1Method(RLCDParams.Stage1Method.FGES);
        p.setStages(1);  // skeleton only

        RLCD _rlcd = new RLCD(dataSet, p);
        Phase1Result r = _rlcd.debugPhase1();
        Phase1.logPartitions(r);
        Graph g1 = r.getSkeleton();
    }

}
