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
public final class FcitSepsets2 {

    private FcitSepsets2() {}

    /**
     * A found separator together with the p-value of the test that confirmed it.
     *
     * @param sepset the separating set
     * @param pValue the p-value of the confirming independence test, or null
     */
    public record SepsetResult(Set<Node> sepset, Double pValue) {}

    /**
     * Three-valued search outcome. {@code result} non-null: a separator was found
     * (indeterminate is false). {@code result} null with indeterminate false: the
     * full candidate family was enumerated and every candidate tested dependent.
     * {@code result} null with indeterminate true: the search was budget- or
     * cap-limited (deadline expiry, a skipped indeterminate not-followed branch,
     * a degraded harvest, or a cap-truncated candidate), so a separator may exist
     * in the unexplored remainder. Callers should treat indeterminate as a soft
     * failure, not as evidence of non-separability.
     *
     * @param result        the found separator, or null
     * @param indeterminate whether the search was inconclusive
     */
    public record SepsetSearch(SepsetResult result, boolean indeterminate) {
        /** The family was exhausted without finding a separator. */
        public static final SepsetSearch NOT_FOUND = new SepsetSearch(null, false);
        /** The search was cut short; no verdict. */
        public static final SepsetSearch INDETERMINATE = new SepsetSearch(null, true);
    }

    /**
     * Searches for a set S with x _||_ y | S in the given graph.
     * <p>
     * The candidate family is built in two nested layers. Outer: recursive blocking
     * is run once with no constraints to collect the AMBIGUOUS members of its
     * blocking set (those with at least one circle endpoint), and every subset of
     * those is tried as a "not-followed" set, each producing its own blocking set B.
     * Inner: every common neighbour of (x,y) is forced into B, all of them are
     * removal candidates, and the removed subset is enumerated from empty upward --
     * so "all common neighbours in" is tried first. This guarantees that if the
     * full-retention candidate (every common neighbour in) separates, it is the
     * one returned. It does NOT guarantee retention pairwise: with common = {c, d},
     * the order tries removing {c} before {d}, so a valid separator excluding c
     * can be returned while an equal-size one retaining c exists. (Harmless for
     * soundness: on the true skeleton a real non-collider c lies in every
     * separator of the pair, and off the true skeleton R0 stamps are not sound in
     * general anyway.)
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
     * @return the three-valued outcome: found, not-found, or indeterminate; see
     *         {@link SepsetSearch}
     * @throws InterruptedException if interrupted
     */
    public static SepsetSearch spanningSepsetSearch(Graph graph, IndependenceTest test, Node x, Node y,
                                                    int recursiveDepth, int depth, int rbRadius,
                                                    long deadline, Runnable onTest)
            throws InterruptedException {

        // Full blocking set with no forbidden nodes, used only to harvest the
        // ambiguous nodes that seed the not-followed enumeration.
        RecursiveBlocking.BlockingResult b0result = RecursiveBlocking.blockPathsRecursively(
                graph, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                deadline);

        boolean sawIndeterminate = false;

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (b0result.indeterminate()) {
            // The harvest itself was budget-limited: nfCand degrades to the empty
            // set and only NF = {} is swept. Any not-found verdict below is then
            // inconclusive, not a proof that the full family was exhausted.
            sawIndeterminate = true;
        } else if (b0result.blockingSet() != null) {
            for (Node v : b0result.blockingSet()) {
                // Only ambiguous nodes -- those with at least one circle endpoint.
                if (graph.getAdjacentNodes(v).stream().anyMatch(
                        w -> graph.getEndpoint(v, w) == Endpoint.CIRCLE
                                || graph.getEndpoint(w, v) == Endpoint.CIRCLE)) {
                    nfCandSet.add(v);
                }
            }
        }
        // else: b0 determinate with a null blocking set -- UNBLOCKABLE under
        // NF = {}. nfCand stays empty, the NF = {} pass reuses b0result, finds no
        // set, and the search returns NOT_FOUND. That is a verdict within the
        // implemented family only: a separator could in principle exist under a
        // nonempty NF, but with no blocking set there is nothing to harvest
        // candidates from. Unreachable in practice without explicit latents.

        List<Node> nfCand = new ArrayList<>(nfCandSet);
        nfCand.sort(Comparator.comparing(Node::getName));

        // The graph is not mutated during this call, so the common neighbours are
        // loop-invariant; compute them once.
        List<Node> common = graph.getAdjacentNodes(x);
        common.retainAll(graph.getAdjacentNodes(y));
        List<Node> removalCandidates = new ArrayList<>(common);
        removalCandidates.sort(Comparator.comparing(Node::getName));

        // Enumerate subsets of the "not-followed" set NF subset of nfCand.
        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
        int[] nfChoice;
        while ((nfChoice = nfGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return SepsetSearch.INDETERMINATE;

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

            // SublistGenerator emits the empty subset first; that run is exactly
            // the harvesting call above, so reuse b0result instead of repeating
            // it. (RB is deterministic on identical inputs, and a repeat under the
            // same absolute deadline could only be equal or worse.)
            RecursiveBlocking.BlockingResult result = notFollowed.isEmpty()
                    ? b0result
                    : RecursiveBlocking.blockPathsRecursively(
                    graph, x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
                    deadline);

            if (result == null || result.indeterminate()) {
                // This NF branch was skipped for budget/cap reasons, not refuted.
                sawIndeterminate = true;
                continue;
            }

            if (result.blockingSet() == null) continue;

            // Defensive copy: never mutate the BlockingResult's own set (b0result
            // is shared with the harvest above).
            Set<Node> B = new LinkedHashSet<>(result.blockingSet());
            B.addAll(common);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return SepsetSearch.INDETERMINATE;

                Set<Node> S = new LinkedHashSet<>(B);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);
                S.removeAll(C);

                if (depth != -1 && S.size() > depth) {
                    // Cap-truncated candidate: the family was narrowed by the
                    // depth cap, so a later not-found is inconclusive, mirroring
                    // the cap semantics inside recursive blocking itself.
                    sawIndeterminate = true;
                    continue;
                }

                if (onTest != null) onTest.run();

                IndependenceResult independenceResult = test.checkIndependence(x, y, S);
                if (independenceResult.isIndependent()) {
                    return new SepsetSearch(new SepsetResult(S, independenceResult.getPValue()), false);
                }
            }
        }

        return sawIndeterminate ? SepsetSearch.INDETERMINATE : SepsetSearch.NOT_FOUND;
    }

    /**
     * Back-compatible wrapper: the separator or null, discarding the not-found /
     * indeterminate distinction. Existing callers (the PKE harnesses) compile
     * unchanged; new code should prefer {@link #spanningSepsetSearch} so that a
     * budget-limited search is not silently read as proof of non-separability.
     */
    public static SepsetResult spanningSepset(Graph graph, IndependenceTest test, Node x, Node y,
                                              int recursiveDepth, int depth, int rbRadius,
                                              long deadline, Runnable onTest)
            throws InterruptedException {
        return spanningSepsetSearch(graph, test, x, y, recursiveDepth, depth, rbRadius, deadline, onTest).result();
    }
}