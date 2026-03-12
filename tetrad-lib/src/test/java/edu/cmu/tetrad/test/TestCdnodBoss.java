package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.CdnodBoss;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for CD-NOD Boss.
 */
public class TestCdnodBoss {

    @Test
    public void testCdnodBossBasic() throws InterruptedException {
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
        DataSet data = semIm.simulateData(1000, false);

        // CdnodBoss expects last column to be C
        double[] cIndex = new double[1000];
        for (int i = 0; i < 1000; i++) cIndex[i] = i;

        DataSet dataWithC = CdnodBoss.Builder.appendChangeIndexAsLastColumn(data, cIndex, "C");
        IndependenceTest test = new IndTestFisherZ(dataWithC, 0.05);

        CdnodBoss cdnodBoss = new CdnodBoss.Builder()
                .test(test)
                .data(dataWithC)
                .build();

        // This might fail with NullPointerException due to 'id' map not being initialized
        Graph result = cdnodBoss.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
    }

    @Test
    public void testCdnodBossWithContext() throws InterruptedException {
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
        DataSet data = semIm.simulateData(1000, false);

        // Set C as context via Knowledge tier 0
        Knowledge knowledge = new Knowledge();
        knowledge.setTier(0, Collections.singletonList("C"));
        knowledge.setTier(1, java.util.Arrays.asList("X1", "X2"));

        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        CdnodBoss cdnodBoss = new CdnodBoss.Builder()
                .test(test)
                .data(data)
                .knowledge(knowledge)
                .build();

        Graph result = cdnodBoss.search();

        assertNotNull(result);
        System.out.println("Result graph with context: " + result);

        Node rc = result.getNode("C");
        Node rx1 = result.getNode("X1");

        assertTrue(result.isParentOf(rc, rx1));
        assertTrue(result.getParents(rc).isEmpty());
    }
}
