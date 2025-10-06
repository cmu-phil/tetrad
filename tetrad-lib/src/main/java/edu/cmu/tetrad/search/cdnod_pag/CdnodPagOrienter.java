package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;

import java.util.*;

import static edu.cmu.tetrad.search.cdnod_pag.PagEdgeUtils.partiallyOriented;
import static edu.cmu.tetrad.search.cdnod_pag.PagEdgeUtils.orientArrowheadAt;

/**
 * CD-NOD-PAG orienter (conservative arrowheads-only) with environment INCLUDED in the graph.
 * - Never orients arrowheads INTO the environment node.
 * - Excludes env from S subsets for stabilization tests (except proxy-guard checks).
 */
public final class CdnodPagOrienter {

    public interface Propagator {
        void propagate(Graph pag);
    }

    private final Graph pag;
    private final ChangeOracle oracle;
    private final Propagator propagator;

    private int maxSubsetSize = 1; // neighbors depth bound
    private boolean useProxyGuard = false;   // check X not just proxying for E
    private boolean excludeEnvFromS = true;  // don't use E in S
    private boolean forbidArrowheadsIntoEnv = true; // no X ?-> E

    public CdnodPagOrienter(Graph pag, ChangeOracle oracle, Propagator propagator) {
        this.pag = Objects.requireNonNull(pag);
        this.oracle = Objects.requireNonNull(oracle);
        this.propagator = Objects.requireNonNull(propagator);
    }

    public CdnodPagOrienter withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPagOrienter withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }
    public CdnodPagOrienter withExcludeEnvFromS(boolean on) { this.excludeEnvFromS = on; return this; }
    public CdnodPagOrienter withForbidArrowheadsIntoEnv(boolean on) { this.forbidArrowheadsIntoEnv = on; return this; }

    public void run() {
        Deque<Runnable> undo = new ArrayDeque<>();
        List<Edge> cand = partiallyOriented(pag);
        Node E = oracle.env();

        for (Edge e : cand) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            // do not orient INTO env
            if (forbidArrowheadsIntoEnv) {
                if (y.equals(E) && tryC1(x, y, undo)) { /* would try, but will skip inside */ }
                if (x.equals(E) && tryC1(y, x, undo)) { /* skip child=E inside */ }
            }

            // try "x stabilizes y"
            if (tryC1(x, y, undo)) continue;
            // try "y stabilizes x"
            if (tryC1(y, x, undo)) continue;
        }

        propagator.propagate(pag); // standard FCI propagation

        if (!PagLegalityCheck.isLegalPagQuiet(pag, Set.of())) {
            // rollback ALL CD-NOD orientations if illegal after propagation
            while (!undo.isEmpty()) undo.pop().run();
            propagator.propagate(pag);
        }
    }

    private boolean tryC1(Node parentCand, Node child, Deque<Runnable> undo) {
        Node E = oracle.env();
        if (forbidArrowheadsIntoEnv && child.equals(E)) return false; // never add heads into E

        Set<Node> neigh = new LinkedHashSet<>(pag.getAdjacentNodes(child));
        neigh.remove(parentCand);
        if (excludeEnvFromS) neigh.remove(E);

        // If there's no variation in child at all, nothing to absorb.
        if (!oracle.changes(child, Collections.emptySet())) return false;

        for (Set<Node> S : SmallSubsetIter.subsets(neigh, maxSubsetSize)) {
            if (!oracle.changes(child, S)) continue;

            Set<Node> SplusX = new LinkedHashSet<>(S);
            SplusX.add(parentCand);

            if (oracle.changes(child, SplusX)) continue; // adding X did NOT stabilize

            // Optional proxy guard using E: ensure X isn't just standing in for E.
            if (useProxyGuard) {
                Set<Node> SplusE = new LinkedHashSet<>(S); SplusE.add(E);
                boolean stabilizedByE = !oracle.changes(child, SplusE);
                if (!stabilizedByE) continue; // E should stabilize if it's the driver

                Set<Node> SplusXE = new LinkedHashSet<>(SplusE); SplusXE.add(parentCand);
                boolean extraBeyondE = oracle.changes(child, SplusXE) != oracle.changes(child, SplusE);
                if (extraBeyondE) continue; // X shouldn't add benefit beyond E
            }

            // Propose X ?-> child (arrowhead at child)
            Endpoint oldXY = pag.getEndpoint(parentCand, child);
            Endpoint oldYX = pag.getEndpoint(child, parentCand);

            orientArrowheadAt(pag, parentCand, child);

            undo.push(() -> {
                pag.setEndpoint(parentCand, child, oldXY);
                pag.setEndpoint(child, parentCand, oldYX);
            });
            return true;
        }
        return false;
    }
}
