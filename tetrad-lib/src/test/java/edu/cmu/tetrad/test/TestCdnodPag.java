package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.cdnod_pag.CdnodPag;
import edu.cmu.tetrad.search.cdnod_pag.CgLrtChangeTest;
import edu.cmu.tetrad.search.cdnod_pag.ChangeTest;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4Strategy;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.text.ParseException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for CD-NOD-PAG.
 */
public class TestCdnodPag {

    @Test
    public void testCdnodPagBasic() throws InterruptedException {
        // Create a simple DAG: X1 -> X2, X1 -> X3, X2 -> X3
        Graph dag = new EdgeListGraph();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x1, x3);
        dag.addDirectedEdge(x2, x3);

        // Generate data
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = null;
        try {
            data = semIm.simulateData(1000, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        Knowledge knowledge = new Knowledge();

        CdnodPag cdnodPag = createCdnodPag(data, knowledge);

        Graph result = cdnodPag.run();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
    }

    @Test
    public void testCdnodPagWithContext() throws InterruptedException {
        // Create a simple DAG with context: C -> X1 -> X2
        Graph dag = new EdgeListGraph();
        Node c = new ContinuousVariable("C");
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        dag.addNode(c);
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addDirectedEdge(c, x1);
        dag.addDirectedEdge(x1, x2);

        // Generate data
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = null;
        try {
            data = semIm.simulateData(1000, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        data.getVariable("C").setName("C");
        data.getVariable("X1").setName("X1");
        data.getVariable("X2").setName("X2");

        // Set C as context via Knowledge tier 0
        Knowledge knowledge = new Knowledge();
        knowledge.setTier(0, Collections.singletonList("C"));
        knowledge.setTier(1, java.util.Arrays.asList("X1", "X2"));

        CdnodPag cdnodPag = createCdnodPag(data, knowledge);

        Graph result = cdnodPag.run();

        assertNotNull(result);
        System.out.println("Result graph with context: " + result);

        Node rc = result.getNode("C");
        Node rx1 = result.getNode("X1");

        // C should be a parent of X1 in some sense. In PAG, it could be C o-> X1 or C -> X1 or C <-> X1 (if latent).
        // Since there are no latents, it should ideally be C -> X1.
        // Actually, CdnodPagOrienter adds an arrowhead at X1 if it changes with C and is stable given C.

        assertTrue(result.getEndpoint(rc, rx1) == Endpoint.ARROW || result.getEndpoint(rc, rx1) == Endpoint.CIRCLE);
        assertTrue(result.getEndpoint(rx1, rc) == Endpoint.TAIL || result.getEndpoint(rx1, rc) == Endpoint.CIRCLE);
    }

    private CdnodPag createCdnodPag(DataSet data, Knowledge knowledge) {
        double alpha = 0.05;
        Parameters parameters = new Parameters();
        parameters.set("alpha", alpha);

        IndependenceTest test1 = new IndTestFisherZ(data, alpha);
        test1 = new CachedIndependenceQueries(test1);
        R0R4Strategy strategy = new R0R4StrategyTestBased(test1);
        FciOrient fciOrient = new FciOrient(strategy);

        CdnodPag.PagBuilder pagBuilder = (DataSet dataWithoutEnv) -> {
            IndependenceTest _test = new IndTestFisherZ(dataWithoutEnv, alpha);
            _test = new CachedIndependenceQueries(_test);
            edu.cmu.tetrad.search.score.Score _score = new edu.cmu.tetrad.search.score.SemBicScore(dataWithoutEnv, true);

            Fcit fcit = new Fcit(_test, _score);
            fcit.setKnowledge(knowledge);
            try {
                return fcit.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Function<Graph, Graph> prop = g -> {
            fciOrient.setKnowledge(knowledge);
            fciOrient.finalOrientation(g);
            return g;
        };

        Function<Graph, Boolean> legalityCheck = g -> {
            return PagLegalityCheck.isLegalPagQuiet(g, Set.of());
        };

        ChangeTest changeTest = new CgLrtChangeTest();

        return new CdnodPag(data, alpha, changeTest, pagBuilder, legalityCheck, prop, knowledge)
                .withMaxSubsetSize(1);
//                .withProxyGuard(true);
    }
}
