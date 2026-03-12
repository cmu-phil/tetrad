package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
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

        DmPcRobust dmPc = new DmPcRobust(fisherZ.getTest(data, parameters));
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

    @Test
    public void testDmPcModerate() {
        RandomUtil.getInstance().setSeed(123456789L);

        Graph dag = new EdgeListGraph();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x5 = new ContinuousVariable("X5");
        Node x6 = new ContinuousVariable("X6");
        Node x7 = new ContinuousVariable("X7");

        Node x9 = new ContinuousVariable("X9");
        Node x10 = new ContinuousVariable("X10");
        Node x11 = new ContinuousVariable("X11");
        Node x12 = new ContinuousVariable("X12");
        Node x13 = new ContinuousVariable("X13");
        Node x14 = new ContinuousVariable("X14");

        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);

        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x5);
        dag.addNode(x6);
        dag.addNode(x7);
        dag.addNode(x9);
        dag.addNode(x10);
        dag.addNode(x11);
        dag.addNode(x12);
        dag.addNode(x13);
        dag.addNode(x14);
        dag.addNode(l1);
        dag.addNode(l2);

        dag.addDirectedEdge(l1, l2);

        dag.addDirectedEdge(l1, x9);
        dag.addDirectedEdge(l1, x10);
        dag.addDirectedEdge(l1, x11);

        dag.addDirectedEdge(l2, x12);
        dag.addDirectedEdge(l2, x13);
        dag.addDirectedEdge(l2, x14);

        dag.addDirectedEdge(x1, l1);
        dag.addDirectedEdge(x2, l1);
        dag.addDirectedEdge(x3, l1);

        dag.addDirectedEdge(x5, l2);
        dag.addDirectedEdge(x6, l2);
        dag.addDirectedEdge(x7, l2);

        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(5000, false);

        FisherZ fisherZ = new FisherZ();
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, 0.001);

        Knowledge knowledge = new Knowledge();

        knowledge.addToTier(0, "X1");
        knowledge.addToTier(0, "X2");
        knowledge.addToTier(0, "X3");
        knowledge.addToTier(0, "X5");
        knowledge.addToTier(0, "X6");
        knowledge.addToTier(0, "X7");

        knowledge.addToTier(1, "X9");
        knowledge.addToTier(1, "X10");
        knowledge.addToTier(1, "X11");
        knowledge.addToTier(1, "X12");
        knowledge.addToTier(1, "X13");
        knowledge.addToTier(1, "X14");

        knowledge.setTierForbiddenWithin(1, true);

        DmPcRobust dmPc = new DmPcRobust(fisherZ.getTest(data, parameters));
        dmPc.setKnowledge(knowledge);

        Graph result = dmPc.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
        System.out.println("Result nodes: " + result.getNodes());
        System.out.println("Result edges: " + result.getEdges());

        Node rx1 = result.getNode("X1");
        Node rx2 = result.getNode("X2");
        Node rx3 = result.getNode("X3");
        Node rx5 = result.getNode("X5");
        Node rx6 = result.getNode("X6");
        Node rx7 = result.getNode("X7");

        Node rx9 = result.getNode("X9");
        Node rx10 = result.getNode("X10");
        Node rx11 = result.getNode("X11");
        Node rx12 = result.getNode("X12");
        Node rx13 = result.getNode("X13");
        Node rx14 = result.getNode("X14");

        Node upstreamLatent = null;
        Node downstreamLatent = null;

        for (Node node : result.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            int upstreamChildrenCount = 0;
            if (result.isParentOf(node, rx9)) upstreamChildrenCount++;
            if (result.isParentOf(node, rx10)) upstreamChildrenCount++;
            if (result.isParentOf(node, rx11)) upstreamChildrenCount++;

            boolean latentFor9to11 =
                    upstreamChildrenCount >= 2 &&
                            result.isParentOf(rx1, node) &&
                            result.isParentOf(rx2, node) &&
                            result.isParentOf(rx3, node);

            boolean latentFor12to14 =
                    result.isParentOf(node, rx12) &&
                            result.isParentOf(node, rx13) &&
                            result.isParentOf(node, rx14) &&
                            result.isParentOf(rx5, node) &&
                            result.isParentOf(rx6, node) &&
                            result.isParentOf(rx7, node);

            if (latentFor9to11) {
                upstreamLatent = node;
            }

            if (latentFor12to14) {
                downstreamLatent = node;
            }
        }

        assertNotNull("Should have found an upstream latent with parents X1, X2, X3 and at least two of X9, X10, X11 as children.",
                upstreamLatent);
        assertNotNull("Should have found a downstream latent with parents X5, X6, X7 and children X12, X13, X14.",
                downstreamLatent);
        assertTrue("Should have found a latent-to-latent edge from the upstream latent to the downstream latent.",
                result.isParentOf(upstreamLatent, downstreamLatent));
    }
}
