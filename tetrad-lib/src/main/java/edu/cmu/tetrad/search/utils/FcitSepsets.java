/// ////////////////////////////////////////////////////////////////////////////
// FcitSepsets.java                                                            //
//                                                                             //
// The separator search used by FCIT, extracted so that the algorithm and the  //
// enumeration harnesses (PhantomKernelEnumerator*) call ONE copy. Previously  //
// the harness carried a transcription of this search, which made any harness  //
// result conditional on the transcription staying faithful; that is now true  //
// by construction.                                                            //
//                                                                             //
// The body is Fcit.findIndependenceCheckRecursive with the caches, the        //
// live-PAG adjacency guards (an artifact of the parallel lookahead reading a  //
// mutating graph), and the check counter lifted out to the caller. What       //
// remains is a pure function of (graph, test, pair, params).                  //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.*;

/**
 * FCIT's separator search: recursive blocking with an enumerated not-followed set,
 * spanned over all subsets of the common neighbours of the pair.
 *
 * @author josephramsey
 */
public final class FcitSepsets {

    private FcitSepsets() {}

    /**
     * A found separator together with the p-value of the test that confirmed it.
     *
     * @param sepset the separating set
     * @param pValue the p-value of the confirming independence test, or null
     */
    public record SepsetResult(Set<Node> sepset, Double pValue) {}

    /**
     * Searches for a set S with x _||_ y | S in the given graph.
     * <p>
     * The candidate family is built in two nested layers. Outer: recursive blocking
     * is run once with no constraints to collect the AMBIGUOUS members of its
     * blocking set (those with at least one circle endpoint), and every subset of
     * those is tried as a "not-followed" set, each producing its own blocking set B.
     * Inner: every common neighbour of (x,y) is forced into B, all of them are
     * removal candidates, and the removed subset is enumerated from empty upward --
     * so "all common neighbours in" is tried first. This is what guarantees that
     * whenever a valid separator CONTAINING a common neighbour c exists, that is the
     * one returned, and R0 is therefore never handed a separator excluding c that
     * would make it stamp the collider x*-&gt;c&lt;-*y.
     * <p>
     * The first set the test confirms independent is returned.
     *
     * @param graph          the graph to search (FCIT passes its working PAG)
     * @param test           the independence test (an oracle, in harness use)
     * @param x              one endpoint
     * @param y              the other endpoint
     * @param recursiveDepth recursive-blocking depth, -1 for unlimited
     * @param depth          maximum conditioning-set size, -1 for unlimited
     * @param rbRadius       recursive-blocking radius, -1 for unlimited
     * @param deadline       absolute wall-clock deadline in ms; Long.MAX_VALUE for none
     * @param onTest         run once per executed independence test, or null
     * @return the separator and its p-value, or null if none was found
     * @throws InterruptedException if interrupted
     */
    public static SepsetResult spanningSepset(Graph graph, IndependenceTest test, Node x, Node y,
                                              int recursiveDepth, int depth, int rbRadius,
                                              long deadline, Runnable onTest)
            throws InterruptedException {

        // Full blocking set with no forbidden nodes, used only to harvest the
        // ambiguous nodes that seed the not-followed enumeration.
        RecursiveBlocking.BlockingResult b0result = RecursiveBlocking.blockPathsRecursively(
                graph, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                deadline);

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (!b0result.indeterminate() && b0result.blockingSet() != null) {
            for (Node v : b0result.blockingSet()) {
                // Only ambiguous nodes -- those with at least one circle endpoint.
                if (graph.getAdjacentNodes(v).stream().anyMatch(
                        w -> graph.getEndpoint(v, w) == Endpoint.CIRCLE
                                || graph.getEndpoint(w, v) == Endpoint.CIRCLE)) {
                    nfCandSet.add(v);
                }
            }
        }

        List<Node> nfCand = new ArrayList<>(nfCandSet);
        nfCand.sort(Comparator.comparing(Node::getName));

        // Enumerate subsets of the "not-followed" set NF subset of nfCand.
        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
        int[] nfChoice;
        while ((nfChoice = nfGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return null;

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    graph, x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
                    deadline);

            if (result == null || result.indeterminate()) continue;

            Set<Node> B = result.blockingSet();
            if (B == null) continue;

            List<Node> common = graph.getAdjacentNodes(x);
            common.retainAll(graph.getAdjacentNodes(y));
            B.addAll(common);
            List<Node> removalCandidates = new ArrayList<>(common);
            removalCandidates.sort(Comparator.comparing(Node::getName));

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null;

                Set<Node> S = new LinkedHashSet<>(B);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);
                S.removeAll(C);

                if (depth != -1 && S.size() > depth) continue;

                if (onTest != null) onTest.run();

                IndependenceResult independenceResult = test.checkIndependence(x, y, S);
                if (independenceResult.isIndependent()) {
                    return new SepsetResult(S, independenceResult.getPValue());
                }
            }
        }

        return null;
    }
}