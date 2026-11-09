package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.graph.EdgeListGraph;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestFasDetailed {

    @Test
    public void testKnowledgeForbidden() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        nodes.add(x);
        nodes.add(y);

        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, y);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);
        
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("X", "Y");
        knowledge.setForbidden("Y", "X");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        assertFalse(result.isAdjacentTo(x, y));
    }

    @Test
    public void testKnowledgeRequired() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        nodes.add(x);
        nodes.add(y);

        // Independent in true graph
        Graph trueGraph = new EdgeListGraph(nodes);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);
        
        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("X", "Y");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        assertTrue(result.isAdjacentTo(x, y));
    }

    @Test
    public void testDepthLimit() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        nodes.add(x);
        nodes.add(y);
        nodes.add(z);

        // X -> Z -> Y, so X || Y | Z
        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, z);
        trueGraph.addDirectedEdge(z, y);

        IndependenceTest test = new MsepTest(trueGraph);
        
        // Depth 0: X and Y should be adjacent (they are not marginally independent)
        Fas fas0 = new Fas(test);
        fas0.setDepth(0);
        Graph result0 = fas0.search();
        assertTrue(result0.isAdjacentTo(x, y));

        // Depth 1: X and Y should NOT be adjacent
        Fas fas1 = new Fas(test);
        fas1.setDepth(1);
        Graph result1 = fas1.search();
        assertFalse(result1.isAdjacentTo(x, y));
    }

    @Test
    public void testStableVsUnstable() throws InterruptedException {
        // This is harder to test without a case where order matters, 
        // but we can at least ensure both run and produce same result on simple case.
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        nodes.add(x);
        nodes.add(y);
        nodes.add(z);

        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, z);
        trueGraph.addDirectedEdge(z, y);

        IndependenceTest test = new MsepTest(trueGraph);

        Fas fasStable = new Fas(test);
        fasStable.setStable(true);
        Graph resultStable = fasStable.search();

        Fas fasUnstable = new Fas(test);
        fasUnstable.setStable(false);
        Graph resultUnstable = fasUnstable.search();

        assertEquals(resultStable, resultUnstable);
    }
    @Test
    public void testKnowledgeRequiredWithIndependence() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        nodes.add(x);
        nodes.add(y);
        nodes.add(z);

        // X and Y are independent in true graph
        Graph trueGraph = new EdgeListGraph(nodes);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);

        Knowledge knowledge = new Knowledge();
        // Even if we say X-Y is required, if they are independent, 
        // will it be removed?
        knowledge.setRequired("X", "Y");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        // If it's truly required, it should remain.
        assertTrue(result.isAdjacentTo(x, y));
    }

    @Test
    public void testKnowledgeRequiredWithConditioningIndependence() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        nodes.add(x);
        nodes.add(y);
        nodes.add(z);

        // X -> Z -> Y, so X || Y | Z
        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, z);
        trueGraph.addDirectedEdge(z, y);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);

        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("X", "Y");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        // If it's truly required, it should remain even if it's independent given Z.
        assertTrue(result.isAdjacentTo(x, y));
    }
    @Test
    public void testKnowledgeSymmetry() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        nodes.add(x);
        nodes.add(y);

        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, y);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);

        Knowledge knowledge = new Knowledge();
        // Forbid X -> Y but NOT Y -> X.
        // For an UNDIRECTED edge search, does this mean the edge is removed?
        knowledge.setForbidden("X", "Y");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        // Since it's a skeleton search, if ANY direction is forbidden, should it be removed?
        // Let's see what the current code does.
        // current code search() lines 201-202 check BOTH.
        // But removeNodesAboutX uses possibleParents, which filters by knowledge.
        assertTrue(result.isAdjacentTo(x, y));
    }
    @Test
    public void testKnowledgeFilteringSeparator() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        Node w = new ContinuousVariable("W");
        nodes.add(x);
        nodes.add(y);
        nodes.add(z);
        nodes.add(w);

        // X -> W -> Y, so X || Y | W.
        // X and Y are NOT independent given Z.
        // If we forbid W to be a parent of X and forbid W to be a parent of Y,
        // will W still be tried as a separator?
        Graph trueGraph = new EdgeListGraph(nodes);
        trueGraph.addDirectedEdge(x, w);
        trueGraph.addDirectedEdge(w, y);
        trueGraph.addDirectedEdge(x, z);
        trueGraph.addDirectedEdge(y, z);

        IndependenceTest test = new MsepTest(trueGraph);
        Fas fas = new Fas(test);
        
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("W", "X");
        knowledge.setForbidden("W", "Y");
        fas.setKnowledge(knowledge);

        Graph result = fas.search();
        // If W is excluded from possibleParents(X) AND possibleParents(Y),
        // then it won't be tried as a separator at depth 1.
        // Since X and Y are not independent given any other subset (except those containing W),
        // the edge X-Y will remain.
        assertTrue(result.isAdjacentTo(x, y));
    }
}
