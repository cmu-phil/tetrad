package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import java.util.*;

/**
 * End-to-end runner for CD-NOD-PAG with the environment variable INCLUDED in the graph.
 *
 * Usage:
 *   - Expect the LAST column to be the environment node E when lastIsEnv = true.
 *   - Build the PAG on ALL columns (including E).
 *   - Strongly recommended: pass background knowledge to your FCIT/RFCI/FCI so that
 *       no edges are oriented INTO E (i.e., E has no parents).
 *   - This runner will also (conservatively) strip any arrowheads INTO E after the search
 *       to comply with exogeneity, then apply the CD-NOD-PAG orienter.
 */
public final class CdnodPag {

    /** Build a PAG from the full dataset (including env). */
    @FunctionalInterface
    public interface PagBuilder {
        Graph search(DataSet fullDataIncludingEnv);
    }

    /** Factory for your FCI/FCIT propagation pass (R0–R10 + discriminating paths, etc.). */
    @FunctionalInterface
    public interface PropagatorFactory {
        CdnodPagOrienter.Propagator make();
    }

    private final DataSet dataAll;
    private final boolean lastIsEnv;
    private final double alpha;
    private final ChangeTest changeTest;
    private final PagBuilder pagBuilder;
    private final PropagatorFactory propFactory;

    private int maxSubsetSize = 1;
    private boolean useProxyGuard = true;
    private boolean stripArrowheadsIntoEnv = true; // post-hoc guard in case builder didn't enforce knowledge

    public CdnodPag(DataSet dataAll,
                    boolean lastIsEnv,
                    double alpha,
                    ChangeTest changeTest,
                    PagBuilder pagBuilder,
                    PropagatorFactory propFactory) {
        this.dataAll = Objects.requireNonNull(dataAll);
        this.lastIsEnv = lastIsEnv;
        this.alpha = alpha;
        this.changeTest = Objects.requireNonNull(changeTest);
        this.pagBuilder = Objects.requireNonNull(pagBuilder);
        this.propFactory = Objects.requireNonNull(propFactory);
    }

    public CdnodPag withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPag withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }
    public CdnodPag withStripArrowheadsIntoEnv(boolean on) { this.stripArrowheadsIntoEnv = on; return this; }

    public Graph run() {
        List<Node> vars = new ArrayList<>(dataAll.getVariables());
        if (vars.isEmpty()) throw new IllegalArgumentException("Empty dataset.");
        Node env = lastIsEnv ? vars.get(vars.size() - 1) : null;

        // 1) Build baseline PAG (INCLUDES env if present) using FCI/RFCI/FCIT
        Graph pag = pagBuilder.search(dataAll);

        // 1a) Post-hoc safeguard: remove arrowheads INTO env (E has no parents)
        if (env != null && stripArrowheadsIntoEnv) {
            stripArrowheadsIntoEnv(pag, env);
        }

        if (env == null) return pag; // nothing to do if no environment

        // 2) Make the change oracle
        ChangeOracle oracle = new ChangeOracle(dataAll, env, alpha, changeTest);

        // 3) Apply CD-NOD-PAG orientations + propagate + legalize
        CdnodPagOrienter orienter = new CdnodPagOrienter(
                pag,
                oracle,
                propFactory.make()
        )
                .withMaxSubsetSize(maxSubsetSize)
                .withProxyGuard(useProxyGuard)
                .withExcludeEnvFromS(true) // avoid using E in S except for proxy-guard
                .withForbidArrowheadsIntoEnv(true); // never add heads into E

        orienter.run();
        return pag;
    }

    /** Remove any arrowheads INTO env: for any U *-> E, replace the endpoint at E with CIRCLE. */
    private static void stripArrowheadsIntoEnv(Graph pag, Node env) {
        for (Node u : new ArrayList<>(pag.getAdjacentNodes(env))) {
            Endpoint endAtEnv = pag.getEndpoint(u, env);
            if (endAtEnv == Endpoint.ARROW) {
                // Replace arrowhead at E with circle (U ?-o E). Do not force a tail at U.
                pag.setEndpoint(u, env, Endpoint.CIRCLE);
            }
        }
    }

    // Convenience helper
    public static Node getLastVariable(DataSet data) {
        List<Node> vars = data.getVariables();
        if (vars.isEmpty()) throw new IllegalArgumentException("Empty dataset.");
        return vars.get(vars.size() - 1);
    }
}
