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
 * Probabilistic conditional independence test using BDeu (population only).
 * Implements Jabbari’s derivation (thesis pp. 36–37): per Z configuration j,
 * compare log P(D_x|z_j) + log P(D_y|z_j) (independent) vs log P(D_y|x,z_j) + log P(D_x|z_j) (dependent)
 * and aggregate across j with a uniform prior over parent configurations.
 */
public class IndTestProbabilisticBDeu implements IndependenceTest {

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

    private static final int MISSING = -99;

    // ============================ ctor ==============================

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

    @Override public IndependenceTest indTestSubset(List<Node> vars) { throw new UnsupportedOperationException(); }

    @Override public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) { return checkIndependence(x, y, z.toArray(new Node[0])); }

    @Override public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        final IndependenceFact key = new IndependenceFact(x, y, z);
        double pInd = cache.computeIfAbsent(key, k -> computeInd(x, y, z));
        this.posterior = pInd;
        final boolean independent = threshold ? (pInd >= cutoff) : (RandomUtil.getInstance().nextDouble() < pInd);
        return new IndependenceResult(key, independent, pInd, Double.NaN);
    }

    @Override public List<Node> getVariables() { return nodes; }

    @Override public Node getVariable(String name) { for (Node n : nodes) if (name.equals(n.getName())) return n; return null; }

    @Override public List<String> getVariableNames() { List<String> out = new ArrayList<>(nodes.size()); for (Node n : nodes) out.add(n.getName()); return out; }

    @Override public double getAlpha() { throw new UnsupportedOperationException(); }
    @Override public void setAlpha(double alpha) { throw new UnsupportedOperationException(); }

    public void setGoldStandard(Graph gs) { this.gold = GraphUtils.replaceNodes(gs, this.data.getVariables()); }

    @Override public DataModel getData() { return data; }
    @Override public ICovarianceMatrix getCov() { return null; }
    @Override public List<DataSet> getDataSets() { return Collections.singletonList(data); }
    @Override public int getSampleSize() { return data.getNumRows(); }

    public Map<IndependenceFact, Double> getH() { return Collections.unmodifiableMap(cache); }
    public double getPosterior() { return posterior; }
    @Override public boolean isVerbose() { return verbose; }
    @Override public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void setThreshold(boolean thresholdMode) { this.threshold = thresholdMode; }
    public void setCutoff(double cutoff) { this.cutoff = cutoff; }

    public void setSampleprior(double samplePrior) { this.samplePrior = samplePrior; }

    // ======================== core computation =======================

    /** Compute P(independent | D) across all parent configurations of Z. */
    public double computeInd(Node x, Node y, Node... z) {
        final int xi = requireIndex(x);
        final int yi = requireIndex(y);
        final int P = z.length;
        final int[] zIdx = new int[P];
        int r = 1; // number of Z configurations
        for (int i = 0; i < P; i++) { zIdx[i] = requireIndex(z[i]); r *= nodeDimensions[zIdx[i]]; }

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

            final BDeuScoreWOprior.CountObjects cx  = score.localCounts(xi);
            final BDeuScoreWOprior.CountObjects cy  = score.localCounts(yi);
            final BDeuScoreWOprior.CountObjects cyx = score.localCounts(yi, xi);

            final double lnPx = dirichletLogEvidenceRow(cx.n_j[0],  cx.n_jk[0],  Kx, /*r=*/1,  samplePrior);
            final double lnPy = dirichletLogEvidenceRow(cy.n_j[0],  cy.n_jk[0],  Ky, /*r=*/1,  samplePrior);

            // For y|x there are r2 = |X| rows; sum evidence across all x
            double lnPyx = 0.0;
            for (int xval = 0; xval < Kx; xval++) {
                lnPyx += dirichletLogEvidenceRow(cyx.n_j[xval], cyx.n_jk[xval], Ky, /*r=*/Kx, samplePrior);
            }

            final double indJ = lnPriorIndPerJ + lnPx + lnPy;
            final double depJ = lnPriorDepPerJ + lnPx + lnPyx; // lnPx shared both ways
            sumLnInd += indJ; sumLnDep += depJ; sumLnAll += logSumExp(indJ, depJ);
        } else {
            // Iterate all Z configurations j
            final int[] dimsZ = new int[P]; for (int i = 0; i < P; i++) dimsZ[i] = nodeDimensions[zIdx[i]];
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
                final int[] xz = Arrays.copyOf(zIdx, P + 1); xz[P] = xi;
                final BDeuScoreWOprior.CountObjects cyx = sc.localCounts(yi, xz);

                final int Kx = nodeDimensions[xi];
                final int Ky = nodeDimensions[yi];

                // Evidence for the specific Z row j (there are r rows for x|z and y|z)
                final double lnPx = dirichletLogEvidenceRow(cx.n_j[j], cx.n_jk[j], Kx, /*r=*/r, samplePrior);
                final double lnPy = dirichletLogEvidenceRow(cy.n_j[j], cy.n_jk[j], Ky, /*r=*/r, samplePrior);

                // For y|x,z there are r2 = r * |X| rows; aggregate over x values
                double lnPyx = 0.0;
                final int[] dimsXZ = Arrays.copyOf(dimsZ, P + 1); dimsXZ[P] = Kx;
                for (int xval = 0; xval < Kx; xval++) {
                    final int row = rankMixedRadix(append(zVals, xval), dimsXZ);
                    lnPyx += dirichletLogEvidenceRow(cyx.n_j[row], cyx.n_jk[row], Ky, /*r=*/r * Kx, samplePrior);
                }

                final double indJ = lnPriorIndPerJ + lnPx + lnPy;
                final double depJ = lnPriorDepPerJ + lnPx + lnPyx;

                sumLnInd += indJ; sumLnDep += depJ; sumLnAll += logSumExp(indJ, depJ);
            }
        }

        return Math.exp(sumLnInd - sumLnAll);
    }

    // ======================= helpers / math ==========================

    private int requireIndex(Node n) { Integer c = indices.get(n); if (c == null) throw new IllegalArgumentException("Unknown node: " + n); return c; }

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

    // Replace current helper with one that takes r:
    private double dirichletLogEvidenceRow(int n_j, int[] n_jk, int K, int r, double ess) {
        final double rowPrior  = ess / (double) r;
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

    private static double logSumExp(double a, double b) { if (a < b) { double t = a; a = b; b = t; } double d = b - a; return (d < -745.0) ? a : a + Math.log1p(Math.exp(d)); }

    private static int[] unrankMixedRadix(int index, int[] dims) { int[] vals = new int[dims.length]; for (int i = dims.length - 1; i >= 0; i--) { vals[i] = index % dims[i]; index /= dims[i]; } return vals; }
    private static int  rankMixedRadix(int[] vals, int[] dims) { int idx = 0; for (int i = 0; i < dims.length; i++) { idx = idx * dims[i] + vals[i]; } return idx; }
    private static int[] append(int[] a, int v) { int[] out = Arrays.copyOf(a, a.length + 1); out[a.length] = v; return out; }

    /** Keep only rows in slice where columns[zIdx[k]] == zVals[k]. */
    private void filterEquals(DataSet slice, int[] zIdx, int[] zVals) {
        final int n = data.getNumRows();
        BitSet keep = new BitSet(n);
        for (int r = 0; r < n; r++) {
            boolean ok = true;
            for (int k = 0; k < zIdx.length; k++) {
                int v = dataArray[r][zIdx[k]];
                if (v == MISSING || v != zVals[k]) { ok = false; break; }
            }
            if (ok) keep.set(r);
        }
        int[] drop = new int[n - keep.cardinality()];
        for (int r = 0, c = 0; r < n; r++) if (!keep.get(r)) drop[c++] = r;
        Arrays.sort(drop);
        slice.removeRows(drop);
    }
}
