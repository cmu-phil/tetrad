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

/**
 * The RLCDTest class contains unit tests for evaluating the functionality
 * and execution of the RLCD algorithm for causal discovery.
 * The tests focus on stage 1 of the RLCD algorithm including
 * skeleton discovery, latent phases, rank tests, and configuration
 * of the algorithm's parameters.
 */
public class RLCDTest {

    /**
     * Constructs a new instance of the RLCDTest class, which contains unit tests
     * to validate the functionality and behavior of the Rank-based Latent Causal
     * Discovery (RLCD) algorithm. Tests primarily focus on Phase 1 of the RLCD
     * algorithm, including skeleton discovery, parameter configuration, rank
     * testing, and graph construction.
     */
    public RLCDTest() {

    }

    /**
     * <p>Tests the functionality of the RLCD (Rank-based Latent Causal Discovery) algorithm’s
     * Phase&nbsp;1. This includes validation of skeleton discovery, rank tests, parameter
     * configurations, and graph construction during the initial phase of the algorithm.</p>
     *
     * <p>The method performs the following tasks:</p>
     *
     * <ul>
     *   <li>Configures {@code RLCDParams} to use the FGES method for Stage&nbsp;1.</li>
     *   <li>Customizes the rank-test factory to estimate ranks using Wilks’ method.</li>
     *   <li>Generates a random graph and uses its structure to simulate a dataset.</li>
     *   <li>Executes Phase&nbsp;1 of the RLCD algorithm to compute the skeleton of the causal graph.</li>
     *   <li>Tests RLCD with different configurations and logs information about partitions and results.</li>
     * </ul>
     */
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
