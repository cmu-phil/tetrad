package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DagmaTest {

    @Test
    public void testDagmaBasic() {
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

        Dagma dagma = new Dagma(data);
        dagma.setLambda1(0.01);
        dagma.setWThreshold(0.01);
        dagma.setCpdag(false);

        Graph result = dagma.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);

        // Check if edges are in the result
        assertTrue("Result should contain X1 adjacent to X2", result.isAdjacentTo(result.getNode("X1"), result.getNode("X2")));
        assertTrue("Result should contain X1 adjacent to X3", result.isAdjacentTo(result.getNode("X1"), result.getNode("X3")));
        assertTrue("Result should contain X2 adjacent to X3", result.isAdjacentTo(result.getNode("X2"), result.getNode("X3")));
    }
}
