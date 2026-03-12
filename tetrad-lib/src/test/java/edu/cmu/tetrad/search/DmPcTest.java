package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DmPcTest {

    @Test
    public void testDmPcBasic() {
        // Create a simple structure where L1 is a latent cause of X2 and X3, and X1 is an input to L1.
        // X1 -> L1, L1 -> X2, L1 -> X3
        // In DmPc's view: 
        // X1 is an input (indegree 0, outdegree > 0)
        // X2, X3 are outputs (indegree > 0)
        
        Graph dag = new EdgeListGraph();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node l1 = new ContinuousVariable("L1");
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(l1);
        dag.addDirectedEdge(x1, l1);
        dag.addDirectedEdge(l1, x2);
        dag.addDirectedEdge(l1, x3);

        // Generate data from this DAG (including the latent)
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        for (Edge edge : dag.getEdges()) {
            semIm.setEdgeCoef(edge.getNode1(), edge.getNode2(), 1.0);
        }
        for (Node node : dag.getNodes()) {
            semIm.setErrVar(node, 0.1);
        }
        DataSet data = semIm.simulateData(1000, false);
        
        // Remove L1 from the dataset to make it truly latent
        data.removeColumn(l1);

        FisherZ fisherZ = new FisherZ();
        Parameters parameters = new Parameters();
        
        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(0, "X1");
        knowledge.addToTier(1, "X2");
        knowledge.addToTier(1, "X3");
        knowledge.setTierForbiddenWithin(1, true);

        DmPc dmPc = new DmPc(fisherZ.getTest(data, parameters));
        dmPc.setKnowledge(knowledge);
        Graph result = dmPc.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
        System.out.println("Result nodes: " + result.getNodes());
        System.out.println("Result edges: " + result.getEdges());
        
        // We expect a latent node to be introduced that connects X1 to {X2, X3}
        // X1 should be a parent of some latent L, and L should be a parent of X2 and X3.
        
        boolean foundLatent = false;
        for (Node node : result.getNodes()) {
            if (node.getName().startsWith("L")) {
                foundLatent = true;
                assertTrue("Latent should be parent of X2", result.isParentOf(node, result.getNode("X2")));
                assertTrue("Latent should be parent of X3", result.isParentOf(node, result.getNode("X3")));
                assertTrue("X1 should be parent of latent", result.isParentOf(result.getNode("X1"), node));
            }
        }
        assertTrue("Should have found a latent node", foundLatent);
    }

//    @Test
    public void testDmPcModerate() {
        Graph dag = new EdgeListGraph();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");
        Node x5 = new ContinuousVariable("X5");
        Node x6 = new ContinuousVariable("X6");

        Node x7 = new ContinuousVariable("X7");
        Node x8 = new ContinuousVariable("X8");
        Node x9 = new ContinuousVariable("X9");
        Node x10 = new ContinuousVariable("X10");
        Node x11 = new ContinuousVariable("X11");
        Node x12 = new ContinuousVariable("X12");

        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);

        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);
        dag.addNode(x5);
        dag.addNode(x6);
        dag.addNode(x7);
        dag.addNode(x8);
        dag.addNode(x9);
        dag.addNode(x10);
        dag.addNode(x11);
        dag.addNode(x12);
        dag.addNode(l1);
        dag.addNode(l2);

        dag.addDirectedEdge(x1, l1);
        dag.addDirectedEdge(x2, l1);
        dag.addDirectedEdge(x3, l1);

        dag.addDirectedEdge(x4, l2);
        dag.addDirectedEdge(x5, l2);
        dag.addDirectedEdge(x6, l2);

        dag.addDirectedEdge(l1, x7);
        dag.addDirectedEdge(l1, x8);

        dag.addDirectedEdge(l2, x9);
        dag.addDirectedEdge(l2, x10);
        dag.addDirectedEdge(l2, x11);
        dag.addDirectedEdge(l2, x12);

        dag.addDirectedEdge(l1, l2);

        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(5000, false);

        FisherZ fisherZ = new FisherZ();
        Parameters parameters = new Parameters();
        parameters.set("alpha", 0.001);

        Knowledge knowledge = new Knowledge();

        knowledge.addToTier(0, "X1");
        knowledge.addToTier(0, "X2");
        knowledge.addToTier(0, "X3");
        knowledge.addToTier(0, "X4");
        knowledge.addToTier(0, "X5");
        knowledge.addToTier(0, "X6");

        knowledge.addToTier(1, "X7");
        knowledge.addToTier(1, "X8");
        knowledge.addToTier(1, "X9");
        knowledge.addToTier(1, "X10");
        knowledge.addToTier(1, "X11");
        knowledge.addToTier(1, "X12");

        knowledge.setTierForbiddenWithin(1, true);

        DmPc dmPc = new DmPc(fisherZ.getTest(data, parameters));
        dmPc.setKnowledge(knowledge);

        Graph result = dmPc.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
        System.out.println("Result nodes: " + result.getNodes());
        System.out.println("Result edges: " + result.getEdges());

        Node rx1 = result.getNode("X1");
        Node rx2 = result.getNode("X2");
        Node rx3 = result.getNode("X3");
        Node rx4 = result.getNode("X4");
        Node rx5 = result.getNode("X5");
        Node rx6 = result.getNode("X6");

        Node rx7 = result.getNode("X7");
        Node rx8 = result.getNode("X8");
        Node rx9 = result.getNode("X9");
        Node rx10 = result.getNode("X10");
        Node rx11 = result.getNode("X11");
        Node rx12 = result.getNode("X12");

        boolean foundLatentFor78 = false;
        boolean foundLatentFor9to12 = false;

        for (Node node : result.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            boolean latentFor78 =
                    result.isParentOf(node, rx7) &&
                            result.isParentOf(node, rx8) &&
                            result.isParentOf(rx1, node) &&
                            result.isParentOf(rx2, node) &&
                            result.isParentOf(rx3, node);

            boolean latentFor9to12 =
                    result.isParentOf(node, rx9) &&
                            result.isParentOf(node, rx10) &&
                            result.isParentOf(node, rx11) &&
                            result.isParentOf(node, rx12) &&
                            result.isParentOf(rx4, node) &&
                            result.isParentOf(rx5, node) &&
                            result.isParentOf(rx6, node);

            if (latentFor78) {
                foundLatentFor78 = true;
            }

            if (latentFor9to12) {
                foundLatentFor9to12 = true;
            }
        }

        assertTrue("Should have found a latent for X7 and X8 with parents X1, X2, X3.", foundLatentFor78);
        assertTrue("Should have found a latent for X9-X12 with parents including X4, X5, X6.", foundLatentFor9to12);
    }
}
