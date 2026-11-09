package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * FCIT with a BOSS start on a d-separation oracle. Until 2026-11-08 the FCIT wrapper refused this combination with
 * "For d-separation oracle input, please use the GRaSP option", a guard that predated BOSS's oracle mode
 * (2026-10-10). With the oracle and the BES step on, the run must recover the true PAG exactly.
 *
 * @author josephramsey
 */
public class TestFcitBossOracle {

    private static Graph runFcitBossOnOracle(Graph trueDag, boolean useBes) throws InterruptedException {
        Parameters params = new Parameters();
        params.set(Params.FCIT_STARTS_WITH, 1);           // BOSS
        params.set(Params.USE_BES_DEFAULT_TRUE, useBes);
        params.set(Params.FCIT_CORRECTNESS, 3);           // the oracle-correct configuration
        params.set(Params.VERBOSE, false);

        // Exactly what GeneralAlgorithmRunner does for a graph source: set the graph on the m-separation test and
        // score wrappers, then search with a null data model.
        MSeparationTest test = new MSeparationTest();
        test.setGraph(trueDag);
        MSepScore score = new MSepScore();
        score.setGraph(trueDag);
        Fcit fcit = new Fcit(test, score);
        return fcit.search(null, params);
    }

    @Test
    public void testBossStartOnOracleRecoversTruePag() throws InterruptedException {
        for (int rep = 0; rep < 10; rep++) {
            RandomUtil.getInstance().setSeed(7000 + rep);
            Graph dag = RandomGraph.randomDag(12, 3, 18, 100, 100, 100, false);
            Graph truePag = GraphTransforms.dagToPag(dag, false);

            Graph est = runFcitBossOnOracle(dag, true);

            assertEquals("rep " + rep + ": FCIT (BOSS start, oracle) differs from the true PAG\n"
                         + "true:\n" + truePag + "\nest:\n" + est, truePag, est);
        }
    }
}
