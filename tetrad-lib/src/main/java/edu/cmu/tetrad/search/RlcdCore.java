package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.other.RlcdParams;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;

import java.util.*;

/**
 * Encapsulates RLCD’s heavy lifting:
 *  - Pull sub-blocks from covariance.
 *  - Test block-rank &lt;= r (SVD quick test; hook for Wilks/CCA).
 *  - Accumulate must-have/must-not-have edges and collider/non-collider constraints.
 *  - Build CPDAG under Zhang rules; respect knowledge.
 */
public class RlcdCore {

    public static final class Stage1Output {
        final Set<Edge> mustHave = new LinkedHashSet<>();
        final Set<Edge> mustNotHave = new LinkedHashSet<>();
        final List<Triple> colliderConstraints = new ArrayList<>();
        final List<Triple> nonColliderConstraints = new ArrayList<>();
    }

    private final RlcdParams p;
    private final Knowledge K;
    private final boolean verbose;

    public RlcdCore(RlcdParams p, Knowledge knowledge, boolean verbose) {
        this.p = p;
        this.K = knowledge != null ? knowledge.copy() : new Knowledge();
        this.verbose = verbose;
    }

    public Stage1Output stage1Structure(CovarianceMatrix cov, int N) {
        if (verbose) TetradLogger.getInstance().log("RLCD.stage1: begin");

        Stage1Output out = new Stage1Output();

        // ===== TODO (Port exact RLCD logic):
        // The Python code runs specific loops over node pairs and partitions,
        // applying rank tests on cross-covariance blocks to accumulate:
        //   - mustHave / mustNotHave edges on observables
        //   - collider/non-collider constraints on triples
        //
        // Below is a conservative scaffold you can replace with the precise loops.
        // For now we start from a fully connected undirected graph and only apply
        // knowledge constraints; plug your RLCD tests where indicated.

        List<Node> V = cov.getVariables();
        // Respect forbidden edges from knowledge immediately
        for (int i = 0; i < V.size(); i++) {
            for (int j = i + 1; j < V.size(); j++) {
                Node X = V.get(i), Y = V.get(j);
                if (isForbidden(X, Y)) out.mustNotHave.add(Edges.undirectedEdge(X, Y));
                else out.mustHave.add(Edges.undirectedEdge(X, Y));
            }
        }

        // >>> Replace this block with RLCD’s real test loops <<<
        // Example of how you’ll call rank tests on C_{A,B}:
        //   boolean ok = testRankLE(cov, Aidx, Bidx, r, p.rankTestMethod(), p.svdTau(), N, p.alpha());
        // and then add/remove constraints accordingly.

        if (verbose) TetradLogger.getInstance().log("RLCD.stage1: done (scaffold)");
        return out;
    }

    public Graph buildCpdagFromConstraints(List<Node> vars, Stage1Output s1) {
        EdgeListGraph g = new EdgeListGraph(vars);

        // mustHave: add undirected if not present
        for (Edge e : s1.mustHave) {
            Node a = e.getNode1(), b = e.getNode2();
            if (!g.isAdjacentTo(a, b)) g.addUndirectedEdge(a, b);
        }
        // mustNotHave: remove if present
        for (Edge e : s1.mustNotHave) {
            Node a = e.getNode1(), b = e.getNode2();
            if (g.isAdjacentTo(a, b)) g.removeEdge(a, b);
        }

        // Apply collider/non-collider constraints (orientations on triples)
        for (Triple t : s1.colliderConstraints) {
            orientColliderIfPossible(g, t.getX(), t.getY(), t.getZ());
        }
        // Non-collider constraints could be enforced by blocking collider orientation at Y

        // Close under standard orientation rules (Zhang 2008)
//        return GraphTransforms.undirectedToCpdag(g);

        new MeekRules().orientImplied(g);
        return g;
    }

    public Graph applyGinOrientations(CovarianceMatrix cov, Graph cpdag, int N) {
        // Hook to your in-tree GIN algorithm:
        // new GinAlgorithm(...).orient(cpdag, cov, N, p.alpha(), K);
        if (verbose) TetradLogger.getInstance().log("RLCD: GIN orientation hook (TODO)");
        return cpdag;
    }

    // ---------- Rank test utilities ----------

    /**
     * Test whether rank(C_{A,B}) &lt;= r using either:
     *  - "svd": numerical rank with threshold tau
     *  - "wilks": placeholder for a Wilks/CCA p-value test (hook to RankTests)
     */
    public boolean testRankLE(CovarianceMatrix cov,
                       int[] A, int[] B, int r,
                       String method, double tau,
                       int N, double alpha) {
        if ("svd".equalsIgnoreCase(method)) {
            DMatrixRMaj C = crossCov(cov, A, B);
            int rank = numericalRank(C, tau);
            return rank <= r;
        } else if ("wilks".equalsIgnoreCase(method)) {
            // TODO: wire to your existing CCA/Wilks implementation returning p-value
            // double pval = RankTests.wilksBlockRankPValue(cov, A, B, r, N);
            // return pval > alpha;
            throw new UnsupportedOperationException("wilks rank test not wired yet");
        } else {
            throw new IllegalArgumentException("Unknown rank test method: " + method);
        }
    }

    /**
     * Extract cross-covariance block C_{A,B} from full covariance.
     */
    public DMatrixRMaj crossCov(CovarianceMatrix cov, int[] A, int[] B) {
        double[][] full = cov.getMatrix().toArray();
        DMatrixRMaj M = new DMatrixRMaj(A.length, B.length);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < B.length; j++) {
                M.set(i, j, full[A[i]][B[j]]);
            }
        }
        return M;
    }

    /**
     * Numerical rank via SVD with singular-value threshold tau.
     */
    public int numericalRank(DMatrixRMaj M, double tau) {
        SingularValueDecomposition_F64<DMatrixRMaj> svd =
                DecompositionFactory_DDRM.svd(M.numRows, M.numCols, true, true, true);
        if (!svd.decompose(M)) throw new RuntimeException("SVD failed");
        double[] s = svd.getSingularValues();
        int rank = 0;
        for (double v : s) if (v > tau) rank++;
        return rank;
    }

    // ---------- Helpers ----------

    private boolean isForbidden(Node x, Node y) {
        return K.isForbidden(x.getName(), y.getName()) && K.isForbidden(y.getName(), x.getName());
    }

    private void orientColliderIfPossible(EdgeListGraph g, Node x, Node y, Node z) {
        // x *-> y <-* z if x - y - z and x !-- z (unshielded triple) & RLCD says collider at y
        if (g.isAdjacentTo(x, y) && g.isAdjacentTo(y, z) && !g.isAdjacentTo(x, z)) {
            g.setEndpoint(x, y, Endpoint.ARROW);
            g.setEndpoint(z, y, Endpoint.ARROW);
            g.setEndpoint(y, x, Endpoint.TAIL);
            g.setEndpoint(y, z, Endpoint.TAIL);
        }
    }
}