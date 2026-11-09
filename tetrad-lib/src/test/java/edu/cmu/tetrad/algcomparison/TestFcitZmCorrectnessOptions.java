package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FcitZm;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * FCIT-ZM with the three-way correctness choice it gained on 2026-11-09 through {@code CorrectnessGates}.
 *
 * <p>On a d-separation oracle, option 3 (the saturating pass, ZM's original configuration) must recover the true
 * PAG: that is the oracle-correctness property ZM's class doc states. Option 1 (the I-map gate, saturating pass
 * off) recovering it is NOT a proved property; it is the generalized-Meek question for ZM, and this test pins the
 * seeds on which it held when the gate was added, as a regression guard. On data, every option must return a
 * legal PAG. Option 2 needs a SEM BIC score and so is refused on an oracle.
 *
 * @author josephramsey
 */
public class TestFcitZmCorrectnessOptions {

    private static Graph runOnOracle(Graph dag, int option) throws InterruptedException {
        MSeparationTest test = new MSeparationTest();
        test.setGraph(dag);
        MSepScore score = new MSepScore();
        score.setGraph(dag);
        Parameters p = new Parameters();
        p.set(Params.FCIT_CORRECTNESS, option);
        p.set(Params.VERBOSE, false);
        return new FcitZm(test, score).search(null, p);
    }

    private static Graph runOnData(DataSet data, int option) throws InterruptedException {
        Parameters p = new Parameters();
        p.set(Params.ALPHA, 0.01);
        p.set(Params.PENALTY_DISCOUNT, 2.0);
        p.set(Params.FCIT_CORRECTNESS, option);
        p.set(Params.NUM_STARTS, 1);
        p.set(Params.VERBOSE, false);
        return new FcitZm(new FisherZ(), new SemBicScore()).runSearch(data, p);
    }

    @Test
    public void testOraclePagRecoveredUnderOptions3And1() throws InterruptedException {
        for (int rep = 0; rep < 6; rep++) {
            RandomUtil.getInstance().setSeed(9300 + rep);
            Graph dag = RandomGraph.randomDag(11, 3, 16, 100, 100, 100, false);
            Graph truePag = GraphTransforms.dagToPag(dag, false);

            assertEquals("option 3 (proved oracle-correct), rep " + rep, truePag, runOnOracle(dag, 3));
            assertEquals("option 1 (regression guard, not a proved property), rep " + rep, truePag, runOnOracle(dag, 1));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOption2RefusedOnOracle() throws InterruptedException {
        RandomUtil.getInstance().setSeed(9300);
        Graph dag = RandomGraph.randomDag(11, 3, 16, 100, 100, 100, false);
        runOnOracle(dag, 2);
    }

    @Test
    public void testAllOptionsReturnLegalPagsOnData() throws InterruptedException {
        for (int rep = 0; rep < 4; rep++) {
            RandomUtil.getInstance().setSeed(9100 + rep);
            Graph dag = RandomGraph.randomDag(13, 3, 17, 100, 100, 100, false);
            DataSet data;
            try {
                data = new SemIm(new SemPm(dag)).simulateData(1000, false);
            } catch (java.text.ParseException e) {
                throw new IllegalStateException(e);
            }
            List<Node> keep = new ArrayList<>();
            for (Node n : data.getVariables()) {
                Node d = dag.getNode(n.getName());
                if (d == null || d.getNodeType() != NodeType.LATENT) keep.add(n);
            }
            data = data.subsetColumns(keep);

            for (int option = 1; option <= 3; option++) {
                Graph g = runOnData(data, option);
                assertTrue("option " + option + ", rep " + rep + ": not a legal PAG",
                        PagLegalityCheck.isLegalPag(g, new HashSet<>()).isLegalPag());
            }
        }
    }
}
