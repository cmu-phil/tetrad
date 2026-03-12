package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

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
}
