///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.search.Ion;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the ION (Integration of Overlapping Networks) algorithm.
 * <p>
 * Reference: Danks, D., Glymour, C., &amp; Tillman, R. E. (2008). Integrating locally learned causal structures with
 * overlapping variables. In Advances in Neural Information Processing Systems 21 (NIPS 2008), pp. 1665-1672.
 *
 * @author josephramsey
 */
public class TestIon {

    /**
     * ION must never return a graph containing a directed cycle. Step 3.c of the reference rejects candidate graphs
     * that are cyclic or that entail an independence known not to hold; this test checks that rejected graphs really
     * are removed from the search rather than propagated to the output.
     * <p>
     * The input PAGs here are individually legal but jointly inconsistent: they claim the ancestral relations A before
     * B, B before C, and C before A, which no acyclic model can satisfy. Jointly inconsistent inputs arise in practice
     * whenever the input PAGs are estimated from finite samples of different datasets, so ION must handle them without
     * emitting cyclic graphs. A fourth input supplies a separation fact so that the branch-and-prune step actually
     * runs. The correct output for these inputs is the empty list; before the step 3.c fix, ION returned graphs
     * containing the directed cycle A -&gt; B -&gt; C -&gt; A.
     */
    @Test
    public void testNoCyclicOutputForInconsistentInputs() {

        // P1 over {A,B}: A -> B
        Graph p1 = new EdgeListGraph(List.of(new GraphNode("A"), new GraphNode("B")));
        p1.addDirectedEdge(p1.getNode("A"), p1.getNode("B"));

        // P2 over {B,C}: B -> C
        Graph p2 = new EdgeListGraph(List.of(new GraphNode("B"), new GraphNode("C")));
        p2.addDirectedEdge(p2.getNode("B"), p2.getNode("C"));

        // P3 over {A,C}: C -> A
        Graph p3 = new EdgeListGraph(List.of(new GraphNode("A"), new GraphNode("C")));
        p3.addDirectedEdge(p3.getNode("C"), p3.getNode("A"));

        // P4 over {B,C,D}: B -> C, C o-o D, B not adjacent to D. This yields the separation
        // fact B _||_ D | {C}, so step 3 has something to branch on.
        Graph p4 = new EdgeListGraph(List.of(new GraphNode("B"), new GraphNode("C"), new GraphNode("D")));
        p4.addDirectedEdge(p4.getNode("B"), p4.getNode("C"));
        p4.addEdge(new Edge(p4.getNode("C"), p4.getNode("D"), Endpoint.CIRCLE, Endpoint.CIRCLE));

        Ion ion = new Ion(new ArrayList<>(List.of(p1, p2, p3, p4)));
        List<Graph> output = ion.search();

        for (Graph graph : output) {
            assertFalse("ION returned a graph with a directed cycle: " + graph,
                    graph.paths().existsDirectedCycle());
        }
    }

    /**
     * A consistent sanity case: the two marginal PAGs of the chain A -&gt; B -&gt; C over {A,B} and {B,C}. The output
     * must be nonempty, acyclic, and must include the graph A o-o B, B o-o C with A and C nonadjacent, since that graph
     * has the same m-separations and m-connections as the inputs on their shared variables. This guards against the
     * step 3.c rejection fix accidentally pruning legitimate candidates on consistent inputs.
     */
    @Test
    public void testChainMarginalsConsistent() {

        // P1 over {A,B}: A o-o B
        Graph p1 = new EdgeListGraph(List.of(new GraphNode("A"), new GraphNode("B")));
        p1.addEdge(new Edge(p1.getNode("A"), p1.getNode("B"), Endpoint.CIRCLE, Endpoint.CIRCLE));

        // P2 over {B,C}: B o-o C
        Graph p2 = new EdgeListGraph(List.of(new GraphNode("B"), new GraphNode("C")));
        p2.addEdge(new Edge(p2.getNode("B"), p2.getNode("C"), Endpoint.CIRCLE, Endpoint.CIRCLE));

        Ion ion = new Ion(new ArrayList<>(List.of(p1, p2)));
        List<Graph> output = ion.search();

        assertFalse("ION returned no graphs for consistent inputs.", output.isEmpty());

        boolean containsChainSkeleton = false;

        for (Graph graph : output) {
            assertFalse("ION returned a graph with a directed cycle: " + graph,
                    graph.paths().existsDirectedCycle());

            if (graph.getNumEdges() == 2
                && graph.isAdjacentTo(graph.getNode("A"), graph.getNode("B"))
                && graph.isAdjacentTo(graph.getNode("B"), graph.getNode("C"))
                && !graph.isAdjacentTo(graph.getNode("A"), graph.getNode("C"))) {
                containsChainSkeleton = true;
            }
        }

        assertTrue("ION output should include a graph with the chain skeleton A-B-C.",
                containsChainSkeleton);
    }
}
