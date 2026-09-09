package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Regression test (2026-9-9): {@link MarkovCheck#computeImpliedFactsForVertex} must
 * return an empty list -- not fail, and not fabricate facts -- for a vertex the graph
 * does not contain. The motivating case: a data set containing a variable (e.g. BUI in
 * the Algerian fire-weather data) that was excluded from the checked graph for
 * near-determinism; the vertex check iterates the data variables, and before the guard
 * the first such variable NPE'd inside the ordered-local-Markov path, killing the whole
 * sweep, while the uniform-Z types would instead have fabricated marginal-independence
 * facts the graph says nothing about.
 */
public class TestMarkovCheckMissingVertex {

    @Test
    public void testForeignVertexYieldsNoFacts() {
        Graph g = new EdgeListGraph();
        Node a = new GraphNode("a"), b = new GraphNode("b"), c = new GraphNode("c");
        g.addNode(a);
        g.addNode(b);
        g.addNode(c);
        g.addDirectedEdge(a, b);
        g.addDirectedEdge(b, c);

        Node foreign = new GraphNode("BUI");   // in the data, not in the graph

        for (ConditioningSetType type : new ConditioningSetType[]{
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY_SINK_ELIMINATION,
                ConditioningSetType.LOCAL_MARKOV,
                ConditioningSetType.PARENTS_AND_NEIGHBORS,
                ConditioningSetType.MARKOV_BLANKET}) {
            List<IndependenceFact> facts =
                    MarkovCheck.computeImpliedFactsForVertex(g, foreign, type);
            assertTrue("A vertex not in the graph must yield no facts under " + type
                    + " (got " + facts.size() + ")", facts.isEmpty());
        }

        // Sanity: a vertex that IS in the graph still yields facts.
        List<IndependenceFact> real = MarkovCheck.computeImpliedFactsForVertex(
                g, a, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        assertTrue("A graph vertex should yield at least one fact here", !real.isEmpty());
    }
}
