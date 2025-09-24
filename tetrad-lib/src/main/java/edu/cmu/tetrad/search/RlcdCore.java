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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Encapsulates RLCD’s heavy lifting:</p>
 * <ul>
 *   <li>Pull sub-blocks from the covariance matrix.</li>
 *   <li>Test block rank &le; <em>r</em> (fast SVD precheck; hook available for Wilks/CCA).</li>
 *   <li>Accumulate must-have / must-not-have edges and collider / non-collider constraints.</li>
 *   <li>Build the CPDAG under Zhang rules; respect background knowledge.</li>
 * </ul>
 */
public class RlcdCore {
    /**
     * Default constructor for the {@code RlcdCore} class.
     *
     * Initializes a new instance of the {@code RlcdCore} class without any
     * pre-configured parameters. This constructor does not set any values
     * or state and acts as a placeholder for creating an empty object.
     */
    public RlcdCore() {}

    /**
     * Encapsulates the configuration parameters for the RLCD algorithm. This variable is an instance of
     * {@code RlcdParams}, which serves as a container for key values such as alpha level, stage 1 method, maximum
     * number of samples, rank test configurations, and GIN usage.
     * <p>
     * It is used to parameterize the behavior of the RLCD algorithm, ensuring that all essential settings are
     * centralized and accessible.
     */
    private RlcdParams p;
    /**
     * Represents knowledge pertaining to constraints and causal relations used within the RLCD (Ranking Linear
     * Causality Discovery) algorithm. This may include known causal relationships, prohibited edges, or any
     * domain-specific structural constraints.
     * <p>
     * The {@code Knowledge} instance enables the algorithm to incorporate prior information about the causal structure,
     * influencing the learning process and results.
     */
    private Knowledge K;
    /**
     * A boolean flag indicating whether verbose logging is enabled for the {@code RlcdCore} class. When set to
     * {@code true}, detailed log messages are output during the execution of algorithms, which can be helpful for
     * debugging or gaining insights into the processing steps. When set to {@code false}, logging is kept minimal.
     */
    private boolean verbose;

    /**
     * Constructs a deep copy of the given {@code RlcdCore} instance.
     *
     * @param core The {@code RlcdCore} instance to be copied. It serves as the source from which the new
     *             {@code RlcdCore} object is created, replicating its state and configuration.
     */
    public RlcdCore(RlcdCore core) {

    }

    /**
     * Constructs an instance of the RlcdCore algorithm.
     *
     * @param p         An instance of {@code RlcdParams} encapsulating all the essential parameters for the RLCD
     *                  algorithm, such as alpha level, stage 1 method, and rank test configurations.
     * @param knowledge An optional {@code Knowledge} object that allows specifying known causal relations or
     *                  constraints. If {@code null}, a new empty {@code Knowledge} instance is created.
     * @param verbose   A boolean flag indicating whether to enable verbose logging during the execution of the
     *                  algorithm.
     */
    public RlcdCore(RlcdParams p, Knowledge knowledge, boolean verbose) {
        this.p = p;
        this.K = knowledge != null ? knowledge.copy() : new Knowledge();
        this.verbose = verbose;
    }

    /**
     * Executes the first stage of the RLCD structure learning algorithm. This method processes a covariance matrix to
     * generate constraints that can be used for subsequent stages of causal structure learning. It identifies required
     * and prohibited edges in the graph, and sets up collider and non-collider constraints.
     *
     * @param cov The covariance matrix containing the variables and their relationships, used for generating structure
     *            constraints.
     * @param N   The sample size corresponding to the covariance matrix, required for statistical tests during
     *            constraint generation.
     * @return An instance of {@code Stage1Output} containing the generated structure constraints, including required
     * edges, prohibited edges, and collider/non-collider information.
     */
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

    /**
     * Constructs a CPDAG (Completed Partially Directed Acyclic Graph) from the given variables and constraints. The
     * method enforces must-have edges, removes must-not-have edges, applies collider constraints, and orients remaining
     * edges using standard orientation rules as described in Zhang 2008.
     *
     * @param vars A list of nodes representing the variables in the graph.
     * @param s1   An instance of {@code Stage1Output} containing constraints on edges (must-have, must-not-have) and
     *             collider/non-collider constraints.
     * @return An instance of {@code Graph} representing the constructed CPDAG after incorporating the given
     * constraints.
     */
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

    /**
     * Applies GIN (General Independence Network) orientation rules to the given CPDAG (Completed Partially Directed
     * Acyclic Graph) using the specified covariance matrix and sample size. This method serves as a hook for the GIN
     * algorithm to further orient the edges in the CPDAG based on the input parameters and configurations.
     *
     * @param cov   The covariance matrix containing information about the variables and their relationships, used to
     *              inform edge orientations.
     * @param cpdag The initial CPDAG (Completed Partially Directed Acyclic Graph) to which GIN orientation rules will
     *              be applied.
     * @param N     The sample size associated with the covariance matrix, influencing statistical tests during the GIN
     *              orientation process.
     * @return The CPDAG after applying the GIN orientation rules, potentially with additional edge orientations
     * performed by the GIN algorithm.
     */
    public Graph applyGinOrientations(CovarianceMatrix cov, Graph cpdag, int N) {
        // Hook to your in-tree GIN algorithm:
        // new GinAlgorithm(...).orient(cpdag, cov, N, p.alpha(), K);
        if (verbose) TetradLogger.getInstance().log("RLCD: GIN orientation hook (TODO)");
        return cpdag;
    }

    /**
     * Tests whether the rank of the cross-covariance matrix extracted from the given covariance matrix is less than or
     * equal to the specified rank threshold using one of the supported methods. The method supports rank determination
     * using either the singular value decomposition (SVD) or other statistical methods like Wilks' lambda test
     * (currently not implemented).
     *
     * @param cov    The covariance matrix containing the relationships among variables.
     * @param A      An array of indices representing the first variable subset for the cross-covariance calculation.
     * @param B      An array of indices representing the second variable subset for the cross-covariance calculation.
     * @param r      The rank threshold to test against.
     * @param method The rank testing method to use. Supported methods include "svd".
     * @param tau    The singular value threshold used for numerical rank determination in the SVD method.
     * @param N      The sample size associated with the covariance matrix, used in certain statistical methods (e.g.,
     *               Wilks).
     * @param alpha  The significance level for the rank test, relevant for statistical methods (e.g., Wilks).
     * @return A boolean value indicating whether the rank test passed. Returns true if the rank is less than or equal
     * to the specified threshold, and false otherwise.
     * @throws IllegalArgumentException      If an unknown method is specified.
     * @throws UnsupportedOperationException If the specified method is not implemented.
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

    // ---------- Rank test utilities ----------

    /**
     * Computes the cross-covariance matrix for the specified subsets of variables. The method extracts the relevant
     * entries from the covariance matrix based on the provided index arrays for the two variable subsets.
     *
     * @param cov The covariance matrix containing relationships among variables.
     * @param A   An array of indices representing the first variable subset.
     * @param B   An array of indices representing the second variable subset.
     * @return A cross-covariance matrix represented as an instance of {@code DMatrixRMaj}, containing the covariances
     * between the variables in subsets A and B.
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
     * Computes the numerical rank of a given matrix using a specified singular value threshold. The rank is determined
     * based on the number of singular values greater than the specified threshold.
     *
     * @param M   The input matrix for which the numerical rank is to be computed.
     * @param tau The threshold value for singular values. Singular values greater than this threshold are considered
     *            significant.
     * @return The numerical rank of the matrix, defined as the count of singular values greater than the threshold.
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

    private boolean isForbidden(Node x, Node y) {
        return K.isForbidden(x.getName(), y.getName()) && K.isForbidden(y.getName(), x.getName());
    }

    // ---------- Helpers ----------

    private void orientColliderIfPossible(EdgeListGraph g, Node x, Node y, Node z) {
        // x *-> y <-* z if x - y - z and x !-- z (unshielded triple) & RLCD says collider at y
        if (g.isAdjacentTo(x, y) && g.isAdjacentTo(y, z) && !g.isAdjacentTo(x, z)) {
            g.setEndpoint(x, y, Endpoint.ARROW);
            g.setEndpoint(z, y, Endpoint.ARROW);
            g.setEndpoint(y, x, Endpoint.TAIL);
            g.setEndpoint(y, z, Endpoint.TAIL);
        }
    }

    /**
     * Represents the output of the initial stage (stage 1) of a structure learning algorithm. This class is used to
     * store various sets of constraints and essential relationships in the graph being learned.
     * <p>
     * The data members of this class capture mandatory and prohibited edges in the learned graph, as well as
     * information on collider and non-collider constraints, which are essential for correctly determining causal
     * relationships.
     */
    public static final class Stage1Output {
        /**
         * Initializes a new instance of the Stage1Output class, which represents the output of the first stage
         * in a structure learning algorithm. This constructor sets up an empty representation of the various
         * constraint sets and relationships required for causal structure learning.
         */
        public Stage1Output() {
        }
        final Set<Edge> mustHave = new LinkedHashSet<>();
        final Set<Edge> mustNotHave = new LinkedHashSet<>();
        final List<Triple> colliderConstraints = new ArrayList<>();
        final List<Triple> nonColliderConstraints = new ArrayList<>();
    }
}