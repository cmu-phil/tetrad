package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.special.Gamma;

import java.util.*;

/**
 * Probabilistic conditional independence test using BDeu (population only). Implements Jabbari’s derivation (thesis pp.
 * 36–37): per Z configuration j, compare log P(D_x|z_j) + log P(D_y|z_j) (independent) vs log P(D_y|x,z_j) + log
 * P(D_x|z_j) (dependent) and aggregate across j with a uniform prior over parent configurations.
 */
public class IndTestProbabilisticBDeu implements IndependenceTest {

    private static final int MISSING = -99;
    private final BDeuScoreWOprior score;   // BDeu with tunable sample prior, no structure prior
    private final int[] nodeDimensions;     // arities per column
    private final DataSet data;             // discrete training data
    private final int[][] dataArray;        // [row][col]
    private final List<Node> nodes;
    private final Map<Node, Integer> indices;  // node → column index
    private final Map<IndependenceFact, Double> cache = new HashMap<>();
    private Graph gold;
    private double posterior;               // last P(ind | D)
    private boolean verbose = false;
    private boolean threshold = false;      // if true, compare posterior to cutoff; else Bernoulli draw
    private double cutoff = 0.5;
    private double prior = 0.5;             // global prior P(independent)
    private double samplePrior = 1.0;       // Dirichlet ESS (delegated to BDeuScoreWOprior)

    // ============================ ctor ==============================

    /**
     * Constructs an independence test object using a probabilistic BDeu (Bayesian Dirichlet equivalent uniform) score.
     * This object is initialized with a discrete dataset and a prior value for the BDeu scoring method.
     *
     * @param dataSet The discrete dataset upon which the independence test operates. Must not be null and must only
     *                contain discrete variables.
     * @param prior   The prior equivalent sample size used for the BDeu scoring method. Controls the strength of the
     *                prior information in the score.
     * @throws IllegalArgumentException If the provided dataset is null or not discrete.
     */
    public IndTestProbabilisticBDeu(final DataSet dataSet, final double prior) {
        if (dataSet == null || !dataSet.isDiscrete()) throw new IllegalArgumentException("Not a discrete data set.");
        this.prior = prior;
        this.data = dataSet;

        // arrays
        this.dataArray = new int[data.getNumRows()][data.getNumColumns()];
        for (int r = 0; r < data.getNumRows(); r++)
            for (int c = 0; c < data.getNumColumns(); c++)
                this.dataArray[r][c] = data.getInt(r, c);

        // arities
        this.nodeDimensions = new int[data.getNumColumns()];
        for (int c = 0; c < data.getNumColumns(); c++)
            this.nodeDimensions[c] = ((DiscreteVariable) data.getVariable(c)).getNumCategories();

        this.score = new BDeuScoreWOprior(this.data);

        this.nodes = data.getVariables();
        this.indices = new HashMap<>(nodes.size() * 2);
        for (int i = 0; i < nodes.size(); i++) indices.put(nodes.get(i), i);
    }

    // ============================ API ===============================

    private static double logSumExp(double a, double b) {
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        double d = b - a;
        return (d < -745.0) ? a : a + Math.log1p(Math.exp(d));
    }

    private static int[] unrankMixedRadix(int index, int[] dims) {
        int[] vals = new int[dims.length];
        for (int i = dims.length - 1; i >= 0; i--) {
            vals[i] = index % dims[i];
            index /= dims[i];
        }
        return vals;
    }

    private static int rankMixedRadix(int[] vals, int[] dims) {
        int idx = 0;
        for (int i = 0; i < dims.length; i++) {
            idx = idx * dims[i] + vals[i];
        }
        return idx;
    }

    private static int[] append(int[] a, int v) {
        int[] out = Arrays.copyOf(a, a.length + 1);
        out[a.length] = v;
        return out;
    }

    /**
     * Returns a new independence test object for the subset of variables provided. This method is intended to create a
     * test object that operates on a specified subset of variables from the original dataset.
     *
     * @param vars The list of variables (as Node objects) to include in the subset. Must not be null, and each variable
     *             must exist in the original set of variables for this independence test.
     * @return A newly created IndependenceTest object that operates on the specified subset of variables.
     * @throws UnsupportedOperationException Indicates that this method has not been implemented.
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks the independence between two variables, x and y, given a set of conditioning variables z. This method uses
     * an array conversion for the conditioning set to perform the independence check.
     *
     * @param x The first variable (Node) to test for independence. Must not be null.
     * @param y The second variable (Node) to test for independence. Must not be null.
     * @param z A set of conditioning variables (Nodes) that may influence the relationship between x and y. Must not be
     *          null.
     * @return An IndependenceResult object containing the result of the independence test, including whether x and y
     * are independent given z.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return checkIndependence(x, y, z.toArray(new Node[0]));
    }

    /**
     * Checks the independence of two variables, x and y, given a set of conditioning variables z. This method computes
     * the posterior probability of independence and determines whether x and y are independent based on the specified
     * threshold or randomness, depending on the setting of the threshold mode.
     *
     * @param x The first variable (Node) to test for independence. Must not be null.
     * @param y The second variable (Node) to test for independence. Must not be null.
     * @param z A variable-length array of conditioning variables (Nodes) that may influence the relationship between x
     *          and y. Must not be null.
     * @return An IndependenceResult object containing: - The variables and conditioning set involved in the test. - A
     * boolean indicating whether x and y are independent given z. - The posterior probability of independence.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        final IndependenceFact key = new IndependenceFact(x, y, z);
        double pInd = cache.computeIfAbsent(key, k -> computeInd(x, y, z));
        this.posterior = pInd;
        final boolean independent = threshold ? (pInd >= cutoff) : (RandomUtil.getInstance().nextDouble() < pInd);
        return new IndependenceResult(key, independent, pInd, Double.NaN);
    }

    /**
     * Retrieves the list of variables associated with this independence test.
     *
     * @return A list of Node objects representing the variables used in this independence test.
     */
    @Override
    public List<Node> getVariables() {
        return nodes;
    }

    /**
     * Retrieves a Node variable by its name from the list of nodes. This method searches through the existing nodes and
     * returns the one that matches the given name, or null if no match is found.
     *
     * @param name The name of the Node to retrieve. Must not be null.
     * @return The Node object with the specified name, or null if no such Node exists in the list.
     */
    @Override
    public Node getVariable(String name) {
        for (Node n : nodes) if (name.equals(n.getName())) return n;
        return null;
    }

    /**
     * Retrieves the names of variables associated with the nodes in this object. The method iterates through all nodes,
     * extracts their names, and returns them as a list.
     *
     * @return A list of strings representing the names of the variables in the nodes.
     */
    @Override
    public List<String> getVariableNames() {
        List<String> out = new ArrayList<>(nodes.size());
        for (Node n : nodes) out.add(n.getName());
        return out;
    }

    /**
     * Retrieves the alpha value used for threshold-based decisions in this object. Alpha is typically a significance
     * level or a threshold value that influences the behavior of certain statistical tests or procedures.
     *
     * @return The current alpha value as a double.
     * @throws UnsupportedOperationException If this method has not been implemented.
     */
    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the alpha value used for threshold-based decisions in this object. Alpha is typically a significance level
     * or a threshold value that influences the behavior of certain statistical tests or procedures.
     *
     * @param alpha The alpha value to set. Must be a non-negative double.
     * @throws UnsupportedOperationException Indicates that this method has not been implemented.
     */
    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the gold standard graph for the independence test. The method replaces the graph's nodes with the
     * corresponding nodes from the dataset's variables to ensure compatibility.
     *
     * @param gs The gold standard graph to set. Must not be null and should represent the reference structure for the
     *           test.
     */
    public void setGoldStandard(Graph gs) {
        this.gold = GraphUtils.replaceNodes(gs, this.data.getVariables());
    }

    /**
     * Retrieves the data model associated with this independence test. The data model typically represents the
     * underlying dataset or structure upon which the independence test is performed.
     *
     * @return The DataModel object representing the dataset used in this test.
     */
    @Override
    public DataModel getData() {
        return data;
    }

    /**
     * Retrieves the covariance matrix associated with this independence test. This method returns null as the test does
     * not directly compute a covariance matrix.
     *
     * @return null, indicating no covariance matrix is computed by this test.
     */
    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    /**
     * Retrieves the list of data sets associated with this independence test. This method returns a singleton list
     * containing the primary dataset used in the test.
     *
     * @return A singleton list containing the primary dataset used in the test.
     */
    @Override
    public List<DataSet> getDataSets() {
        return Collections.singletonList(data);
    }

    /**
     * Retrieves the sample size of the dataset used in this independence test.
     *
     * @return The number of rows in the dataset.
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Retrieves the cache of independence facts and their corresponding posterior probabilities.
     *
     * @return An unmodifiable map containing independence facts and their posterior probabilities.
     */
    public Map<IndependenceFact, Double> getH() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Retrieves the posterior probability of the independence test.
     *
     * @return The posterior probability of the test.
     */
    public double getPosterior() {
        return posterior;
    }

    /**
     * Retrieves the verbosity setting for the independence test.
     *
     * @return True if verbose output is enabled, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    // ======================== core computation =======================

    /**
     * Sets the verbosity setting for the independence test.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ======================= helpers / math ==========================

    /**
     * Sets the threshold mode for the system.
     *
     * @param thresholdMode a boolean indicating the desired threshold mode. If true, the threshold mode is enabled; if
     *                      false, it is disabled.
     */
    public void setThreshold(boolean thresholdMode) {
        this.threshold = thresholdMode;
    }

//    /** log evidence under Dirichlet‑multinomial for a single parent row: P(D_y|row) up to a constant. */
//    private double dirichletLogEvidence(int n_j, int[] n_jk, int K) {
//        double rowPrior = samplePrior / Math.max(1, K == 0 ? 1 : 1); // unused guard; K>0 for discrete
//        rowPrior = samplePrior; // ESS is spread across rows by caller via localCounts indices
//        final double cellPrior = rowPrior / K;
//        double s = 0.0;
//        s -= Gamma.logGamma(rowPrior + n_j);
//        s += Gamma.logGamma(rowPrior);
//        for (int k = 0; k < K; k++) { s += Gamma.logGamma(cellPrior + n_jk[k]); s -= Gamma.logGamma(cellPrior); }
//        return s;
//    }

    /**
     * Sets the cutoff value.
     *
     * @param cutoff the cutoff value to set
     */
    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    /**
     * Sets the sample prior value.
     *
     * @param samplePrior the prior value to set
     */
    public void setSampleprior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    /**
     * Computes the independence score between two nodes (x and y) conditioned on a set of other nodes (z).
     * The method calculates the likelihood of dependence and independence between x and y for
     * each configuration of z, and outputs a probability indicating independence.
     *
     * @param x the first node for which the independence score is computed
     * @param y the second node for which the independence score is computed
     * @param z the set of conditioning nodes, which may be empty
     * @return the computed independence score as a probability in the range [0, 1]
     */
    public double computeInd(Node x, Node y, Node... z) {
        final int xi = requireIndex(x);
        final int yi = requireIndex(y);
        final int P = z.length;
        final int[] zIdx = new int[P];
        int r = 1; // number of Z configurations
        for (int i = 0; i < P; i++) {
            zIdx[i] = requireIndex(z[i]);
            r *= nodeDimensions[zIdx[i]];
        }

        // Per‑config priors: distribute mass uniformly over the r configurations
        final double lnPriorIndPerJ = Math.log(prior) - Math.log(Math.max(1, r));
        final double lnPriorDepPerJ = Math.log(1.0 - prior) - Math.log(Math.max(1, r));

        // Set BDeu ESS used by local counts
        score.setSamplePrior(samplePrior);

        double sumLnInd = 0.0;  // Σ_j ln P(D_x|z_j) + ln P(D_y|z_j) + lnPriorIndPerJ
        double sumLnDep = 0.0;  // Σ_j ln P(D_x|z_j) + ln P(D_y|x,z_j) + lnPriorDepPerJ
        double sumLnAll = 0.0;  // Σ_j logsumexp(ind_j, dep_j)

        if (P == 0) {
            // Z empty: r=1 and the whole data is one configuration
            final int Kx = nodeDimensions[xi];
            final int Ky = nodeDimensions[yi];

            final BDeuScoreWOprior.CountObjects cx = score.localCounts(xi);
            final BDeuScoreWOprior.CountObjects cy = score.localCounts(yi);
            final BDeuScoreWOprior.CountObjects cyx = score.localCounts(yi, xi);

            final double lnPx = dirichletLogEvidenceRow(cx.n_j[0], cx.n_jk[0], Kx, /*r=*/1, samplePrior);
            final double lnPy = dirichletLogEvidenceRow(cy.n_j[0], cy.n_jk[0], Ky, /*r=*/1, samplePrior);

            // For y|x there are r2 = |X| rows; sum evidence across all x
            double lnPyx = 0.0;
            for (int xval = 0; xval < Kx; xval++) {
                lnPyx += dirichletLogEvidenceRow(cyx.n_j[xval], cyx.n_jk[xval], Ky, /*r=*/Kx, samplePrior);
            }

            final double indJ = lnPriorIndPerJ + lnPx + lnPy;
            final double depJ = lnPriorDepPerJ + lnPx + lnPyx; // lnPx shared both ways
            sumLnInd += indJ;
            sumLnDep += depJ;
            sumLnAll += logSumExp(indJ, depJ);
        } else {
            // Iterate all Z configurations j
            final int[] dimsZ = new int[P];
            for (int i = 0; i < P; i++) dimsZ[i] = nodeDimensions[zIdx[i]];
            for (int j = 0; j < r; j++) {
                final int[] zVals = unrankMixedRadix(j, dimsZ);

                // Slice data where Z == zVals
                DataSet slice = new BoxDataSet((BoxDataSet) data);
                filterEquals(slice, zIdx, zVals);

                BDeuScoreWOprior sc = new BDeuScoreWOprior(slice);
                sc.setSamplePrior(samplePrior);

                // Counts conditioned on Z
                final BDeuScoreWOprior.CountObjects cx = sc.localCounts(xi, zIdx);
                final BDeuScoreWOprior.CountObjects cy = sc.localCounts(yi, zIdx);
                final int[] xz = Arrays.copyOf(zIdx, P + 1);
                xz[P] = xi;
                final BDeuScoreWOprior.CountObjects cyx = sc.localCounts(yi, xz);

                final int Kx = nodeDimensions[xi];
                final int Ky = nodeDimensions[yi];

                // Evidence for the specific Z row j (there are r rows for x|z and y|z)
                final double lnPx = dirichletLogEvidenceRow(cx.n_j[j], cx.n_jk[j], Kx, /*r=*/r, samplePrior);
                final double lnPy = dirichletLogEvidenceRow(cy.n_j[j], cy.n_jk[j], Ky, /*r=*/r, samplePrior);

                // For y|x,z there are r2 = r * |X| rows; aggregate over x values
                double lnPyx = 0.0;
                final int[] dimsXZ = Arrays.copyOf(dimsZ, P + 1);
                dimsXZ[P] = Kx;
                for (int xval = 0; xval < Kx; xval++) {
                    final int row = rankMixedRadix(append(zVals, xval), dimsXZ);
                    lnPyx += dirichletLogEvidenceRow(cyx.n_j[row], cyx.n_jk[row], Ky, /*r=*/r * Kx, samplePrior);
                }

                final double indJ = lnPriorIndPerJ + lnPx + lnPy;
                final double depJ = lnPriorDepPerJ + lnPx + lnPyx;

                sumLnInd += indJ;
                sumLnDep += depJ;
                sumLnAll += logSumExp(indJ, depJ);
            }
        }

        return Math.exp(sumLnInd - sumLnAll);
    }

    private int requireIndex(Node n) {
        Integer c = indices.get(n);
        if (c == null) throw new IllegalArgumentException("Unknown node: " + n);
        return c;
    }

    // Replace current helper with one that takes r:
    private double dirichletLogEvidenceRow(int n_j, int[] n_jk, int K, int r, double ess) {
        final double rowPrior = ess / (double) r;
        final double cellPrior = ess / (double) (K * r);
        double s = 0.0;
        s -= Gamma.logGamma(rowPrior + n_j);
        s += Gamma.logGamma(rowPrior);
        for (int k = 0; k < K; k++) {
            s += Gamma.logGamma(cellPrior + n_jk[k]);
            s -= Gamma.logGamma(cellPrior);
        }
        return s;
    }

    /**
     * Keep only rows in slice where columns[zIdx[k]] == zVals[k].
     */
    private void filterEquals(DataSet slice, int[] zIdx, int[] zVals) {
        final int n = data.getNumRows();
        BitSet keep = new BitSet(n);
        for (int r = 0; r < n; r++) {
            boolean ok = true;
            for (int k = 0; k < zIdx.length; k++) {
                int v = dataArray[r][zIdx[k]];
                if (v == MISSING || v != zVals[k]) {
                    ok = false;
                    break;
                }
            }
            if (ok) keep.set(r);
        }
        int[] drop = new int[n - keep.cardinality()];
        for (int r = 0, c = 0; r < n; r++) if (!keep.get(r)) drop[c++] = r;
        Arrays.sort(drop);
        slice.removeRows(drop);
    }
}
