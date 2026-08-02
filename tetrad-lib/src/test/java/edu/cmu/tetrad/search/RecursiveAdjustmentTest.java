package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class RecursiveAdjustmentTest {

    @Test
    public void bidirectedBackdoorIsWitnessed() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), w = new GraphNode("W");
        Graph mag = new EdgeListGraph(List.of(x, y, w));
        mag.addDirectedEdge(x, y);      // causal, amenable (directed out of X)
        mag.addBidirectedEdge(x, w);    // latent confounder
        mag.addDirectedEdge(w, y);      // confounding path X <-> W -> Y

        List<Set<Node>> sets = new RecursiveAdjustment(mag)
                .adjustmentSets(x, y, "MAG", 4, -1, 3, -1,
                        RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST,
                        false, null, null, Collections.emptySet());

        assertEquals(List.of(Set.of(w)), sets);
    }
}