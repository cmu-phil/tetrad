package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Equivalence regression test (2026-9-9) for
 * {@link MarkovCheck#computeImpliedFactsByVertex}, the whole-graph fact computation
 * added as a pure optimization: for the ordered-local-Markov conditioning types it
 * computes the full model once and buckets facts per vertex, where the per-vertex path
 * recomputes the full model inside {@code getModelForNode} for every vertex (measured
 * at roughly V times the single-model cost). This test pins the optimization's
 * contract: for every vertex of every graph, the bucket equals the per-vertex method's
 * facts as a set, and the union equals {@code computeAllImpliedFacts}. Any divergence
 * here means the speedup changed semantics.
 */
public class TestImpliedFactsByVertex {

    private static final ConditioningSetType[] TYPES = {
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY_SINK_ELIMINATION,
            ConditioningSetType.LOCAL_MARKOV,
            ConditioningSetType.MARKOV_BLANKET
    };

    @Test
    public void testBulkEqualsPerVertex() {
        RandomUtil.getInstance().setSeed(90210L);

        for (int rep = 0; rep < 5; rep++) {
            Graph dag = RandomGraph.randomGraph(12, 0, 18, 100, 100, 100, false);

            // Three graph shapes the repair search actually evaluates: a DAG, its
            // CPDAG, and its MAG (standing in for the screening MAGs).
            List<Graph> shapes = List.of(
                    dag,
                    GraphTransforms.dagToCpdag(dag),
                    GraphTransforms.dagToMag(dag));

            for (Graph g : shapes) {
                for (ConditioningSetType type : TYPES) {
                    Map<String, List<IndependenceFact>> byVertex =
                            MarkovCheck.computeImpliedFactsByVertex(g, type);

                    assertEquals("Every vertex must have an entry (" + type + ")",
                            g.getNumNodes(), byVertex.size());

                    Set<IndependenceFact> union = new HashSet<>();
                    for (Node x : g.getNodes()) {
                        Set<IndependenceFact> perVertex = new HashSet<>(
                                MarkovCheck.computeImpliedFactsForVertex(g, x, type));
                        Set<IndependenceFact> bucket =
                                new HashSet<>(byVertex.get(x.getName()));
                        assertEquals("Bucket for " + x.getName() + " under " + type
                                        + " must equal the per-vertex facts",
                                perVertex, bucket);
                        union.addAll(bucket);
                    }

                    assertEquals("Union of buckets must equal computeAllImpliedFacts ("
                                    + type + ")",
                            new HashSet<>(MarkovCheck.computeAllImpliedFacts(g, type)),
                            union);
                }
            }
        }
    }
}
