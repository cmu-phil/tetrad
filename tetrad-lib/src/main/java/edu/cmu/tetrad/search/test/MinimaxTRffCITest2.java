package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * <p><b>Minimax-t RFF Conditional Independence Test (mixed)</b></p>
 *
 * <p>
 * Tests X ⟂⟂ Y | Z by comparing two robust conditional models for Y:
 * </p>
 * <ul>
 *   <li>Null:    Y ~ f(Z)</li>
 *   <li>Alt:     Y ~ f(Z, X)</li>
 * </ul>
 *
 * <p>
 * Continuous Y: Student-t location model with RFF for continuous parents + one-hot for discrete parents,
 * fit by IRLS ridge regression (robust to heavy-tailed residuals).
 * </p>
 *
 * <p>
 * Discrete Y: Multinomial logistic ridge regression with the same feature map, fit by IRLS.
 * </p>
 *
 * <p><b>Minimax calibration.</b>
 * P-values are computed by stratified permutation:
 * stratify on Z (quantile bins for continuous Z, exact match for discrete Z),
 * and permute Y within each stratum.
 * </p>
 *
 * <p>
 * Missing rows in any of {X,Y,Z} are dropped per test.
 * </p>
 */
public final class MinimaxTRffCITest2 implements IndependenceTest, RowsSettable {

    // ---------------- data ----------------
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    // global z-scored continuous columns (NaNs preserved). Discrete vars are NaN-filled.
    private final double[][] zCols;

    // optional row restriction
    private List<Integer> rows = null;

    // cache for Z strata: (Z signature + useRows signature + knobs) -> groups of indices in useRows-space
    private final ConcurrentHashMap<StrataKey, int[][]> strataCache = new ConcurrentHashMap<>();

    // ---------------- knobs ----------------
    private double alpha = 0.01;

    // permutation
    private int permutations = 200;
    private long permSeed = 1L;

    // stratification on Z
    private int binsPerContZ = 6;
    private int minStratumSize = 6;

    // model knobs
    private double ridge = 1e-3;
    private double nu = 5.0;       // Student-t df (continuous Y)
    private double scale = 1.0;    // Student-t scale (reasonable if globally z-scored)

    // RFF
    private int rffFeatures = 256;
    private double rffSigma = 1.0;
    private long rffSeed = 1L;

    // IRLS
    private int irlsIters = 8;
    private double irlsTol = 1e-6;

    // behavior
    private boolean verbose = false;

    public MinimaxTRffCITest2(DataSet data, double alpha) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);

        int p = variables.size();
        int n = data.getNumRows();

        double[][] raw = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < n; r++) raw[j][r] = data.getDouble(r, j);
            }
        }

        this.zCols = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) Arrays.fill(zCols[j], Double.NaN);
            else zscoreColumnPreserveNaN(raw[j], zCols[j]);
        }
    }

    // =========================================================
    // IndependenceTest
    // =========================================================

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p = getPValue(x, y, z);
        boolean indep = p > alpha;

        IndependenceResult r = new IndependenceResult(
                new IndependenceFact(x, y, z),
                indep,
                p,
                alpha - p
        );

        if (verbose && indep) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        return r;
    }

    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        if (x.equals(y)) return 1.0;

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        // drop rows with missing in {X,Y,Z}
        List<Integer> baseRows = listRows();
        int[] useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.length;

        if (n < 20) return 0.0; // conservative guard

        int[][] strata = getStrata(iz, useRows);
        if (strata.length == 0) return 0.0;

        long seed = permSeed ^ (long) ix * 0x9E3779B97F4A7C15L ^ (long) iy * 0xC2B2AE3D27D4EB4FL ^ Arrays.hashCode(iz);

        // build fixed design matrices for null/alt (Phi depends only on parents)
        // Null parents: Z
        // Alt parents:  Z plus X
        int[] parentsNull = iz;
        int[] parentsAlt = append(iz, ix);
        Arrays.sort(parentsAlt);

        // extract Y (observed) in useRows-space
        if (isDiscrete(iy)) {
            int K = numCategories(iy);
            if (K < 2) return 1.0;

            int[] yObs = extractDiscreteY(iy, useRows);

            DesignMap dmNull = buildDesign(parentsNull, useRows, iy);
            DesignMap dmAlt  = buildDesign(parentsAlt,  useRows, iy);

            if (dmNull == null || dmAlt == null) return 0.0;

            double ll0 = fitMultinomialLogitLL(yObs, K, dmNull.Phi);
            double ll1 = fitMultinomialLogitLL(yObs, K, dmAlt.Phi);
            if (!Double.isFinite(ll0) || !Double.isFinite(ll1)) return 0.0;

            double tObs = 2.0 * (ll1 - ll0);
            if (!Double.isFinite(tObs)) return 0.0;

            // permutation: shuffle Y within strata
            SplittableRandom rng = new SplittableRandom(seed);
            int B = Math.max(50, permutations);

            int ge = 0;
            int valid = 0;
            int[] yPerm = Arrays.copyOf(yObs, n);

            for (int b = 0; b < B; b++) {
                System.arraycopy(yObs, 0, yPerm, 0, n);
                shuffleWithinStrataInt(yPerm, strata, rng);

                double pll0 = fitMultinomialLogitLL(yPerm, K, dmNull.Phi);
                double pll1 = fitMultinomialLogitLL(yPerm, K, dmAlt.Phi);
                if (!Double.isFinite(pll0) || !Double.isFinite(pll1)) continue;

                double t = 2.0 * (pll1 - pll0);
                if (!Double.isFinite(t)) continue;

                valid++;
                if (t >= tObs) ge++;
            }

            if (valid == 0) return 1.0;
            return (ge + 1.0) / (valid + 1.0);

        } else {
            // continuous Y
            if (!(nu > 2) || !Double.isFinite(nu)) return 0.0;
            if (!(scale > 0) || !Double.isFinite(scale)) return 0.0;

            double[] yObs = extractContinuousY(iy, useRows);
            centerInPlace(yObs);

            DesignMap dmNull = buildDesign(parentsNull, useRows, iy);
            DesignMap dmAlt  = buildDesign(parentsAlt,  useRows, iy);

            if (dmNull == null || dmAlt == null) return 0.0;

            double ll0 = fitStudentTRidgeLL(yObs, dmNull.Phi);
            double ll1 = fitStudentTRidgeLL(yObs, dmAlt.Phi);
            if (!Double.isFinite(ll0) || !Double.isFinite(ll1)) return 0.0;

            double tObs = 2.0 * (ll1 - ll0);
            if (!Double.isFinite(tObs)) return 0.0;

            SplittableRandom rng = new SplittableRandom(seed);
            int B = Math.max(50, permutations);

            int ge = 0;
            int valid = 0;
            double[] yPerm = Arrays.copyOf(yObs, n);

            for (int b = 0; b < B; b++) {
                System.arraycopy(yObs, 0, yPerm, 0, n);
                shuffleWithinStrataDouble(yPerm, strata, rng);

                double pll0 = fitStudentTRidgeLL(yPerm, dmNull.Phi);
                double pll1 = fitStudentTRidgeLL(yPerm, dmAlt.Phi);
                if (!Double.isFinite(pll0) || !Double.isFinite(pll1)) continue;

                double t = 2.0 * (pll1 - pll0);
                if (!Double.isFinite(t)) continue;

                valid++;
                if (t >= tObs) ge++;
            }

            if (valid == 0) return 1.0;
            return (ge + 1.0) / (valid + 1.0);
        }
    }

    // =========================================================
    // Design map: Phi matrix for parents (RFF(cont) + OneHot(disc) + intercept)
    // =========================================================

    private DesignMap buildDesign(int[] parents, int[] useRows, int childIndexForSeed) {
        // split parents
        int[] cont = filterContinuous(parents);
        int[] disc = filterDiscrete(parents);

        OneHotSpec oh = buildOneHotSpec(disc);

        int n = useRows.length;
        int D = rffFeatures;
        int Q = oh.totalCols;
        int M = 1 + D + Q;

        // extract Zc (standardized)
        double[][] Zc = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            int row = useRows[i];
            for (int j = 0; j < cont.length; j++) Zc[i][j] = zCols[cont[j]][row];
        }

        // deterministic seed per (child + parents)
        long seed = rffSeed ^ (long) childIndexForSeed * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

        // RFF params
        Random rng = new Random(seed);
        double[][] W = new double[D][Math.max(1, cont.length)];
        for (int k = 0; k < D; k++) for (int j = 0; j < W[k].length; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        double phiScale = sqrt(2.0 / D);

        // build Phi
        double[][] Phi = new double[n][M];
        double[] rowPhi = new double[M];

        for (int i = 0; i < n; i++) {
            buildPhiRow(rowPhi, i, useRows[i], Zc, cont.length, W, phase, phiScale, oh, disc);
            System.arraycopy(rowPhi, 0, Phi[i], 0, M);
        }

        return new DesignMap(Phi);
    }

    private void buildPhiRow(double[] out,
                             int i, int dataRow,
                             double[][] Zc, int dCont,
                             double[][] W, double[] phase, double phiScale,
                             OneHotSpec oh, int[] discParents) {

        out[0] = 1.0;

        // RFF block at [1 .. 1 + D - 1]
        int rffOff = 1;
        if (dCont == 0) {
            Arrays.fill(out, rffOff, rffOff + rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dCont; j++) dot += wk[j] * Zc[i][j];
                out[rffOff + k] = phiScale * cos(dot + phase[k]);
            }
        }

        // One-hot block at [1 + D .. M-1]
        int ohOff = 1 + rffFeatures;
        Arrays.fill(out, ohOff, out.length, 0.0);

        if (discParents.length == 0) return;

        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int lev = data.getInt(dataRow, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;

            // baseline dropped
            if (lev <= 0) continue;

            int col = oh.offsets[t] + (lev - 1);
            // sizes[t] = K, columns are K-1
            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
    }

    private OneHotSpec buildOneHotSpec(int[] discParents) {
        int m = discParents.length;
        int[] sizes = new int[m];
        int[] offsets = new int[m];
        int off = 0;
        for (int t = 0; t < m; t++) {
            int var = discParents[t];
            int K = numCategories(var);
            sizes[t] = K;
            offsets[t] = off;
            off += max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    private record DesignMap(double[][] Phi) {}

    // =========================================================
    // Robust fitting: Continuous Y (Student-t IRLS ridge)
    // =========================================================

    private double fitStudentTRidgeLL(double[] yCentered, double[][] Phi) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;

        final int n = yCentered.length;
        final int M = Phi[0].length;

        double[] w = new double[n];
        Arrays.fill(w, 1.0);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < irlsIters; iter++) {

            DMatrixRMaj G = new DMatrixRMaj(M, M);
            double[] v = new double[M];

            for (int i = 0; i < n; i++) {
                double[] phi = Phi[i];
                double wi = w[i];
                double yi = yCentered[i];

                for (int a = 0; a < M; a++) v[a] += wi * phi[a] * yi;
                for (int a = 0; a < M; a++) {
                    double pa = wi * phi[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * phi[b]);
                }
            }

            // sym + ridge (no intercept penalty)
            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 1; a < M; a++) G.add(a, a, ridge);

            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return Double.NaN;
            DMatrixRMaj L = chol.getT(null);

            beta = solveFromCholeskyLower(L, v);

            // update weights + objective surrogate
            double obj = 0.0;
            for (int i = 0; i < n; i++) {
                double[] phi = Phi[i];
                double yhat = 0.0;
                for (int a = 0; a < M; a++) yhat += phi[a] * beta[a];

                double r = yCentered[i] - yhat;
                double u2 = (r / scale) * (r / scale);

                w[i] = (nu + 1.0) / (nu + u2);
                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // final log-likelihood
        double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            double[] phi = Phi[i];
            double yh = 0.0;
            for (int a = 0; a < M; a++) yh += phi[a] * beta[a];
            yhat[i] = yh;
        }

        return studentTLogLik(yCentered, yhat, nu, scale);
    }

    // =========================================================
    // Robust fitting: Discrete Y (Multinomial logistic IRLS ridge)
    // =========================================================

    private double fitMultinomialLogitLL(int[] y, int K, double[][] Phi) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;

        final int n = y.length;
        final int M = Phi[0].length;
        final int C = K - 1;

        // beta: M x C
        final double[][] beta = new double[M][C];
        final double[] logits = new double[K];

        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < irlsIters; iter++) {

            // probs from current beta
            final double[][] probs = softmaxProbsFromPhi(K, n, beta, Phi, logits);

            // update each class block with frozen probs
            for (int c = 0; c < C; c++) {
                final DMatrixRMaj G = new DMatrixRMaj(M, M);
                final double[] v = new double[M];

                for (int i = 0; i < n; i++) {
                    final double[] phi = Phi[i];

                    final double pc = probs[i][c + 1];
                    double wc = pc * (1.0 - pc);
                    wc = Math.max(wc, 1e-10);

                    double eta = 0.0;
                    for (int a = 0; a < M; a++) eta += phi[a] * beta[a][c];

                    final double yc = (y[i] == (c + 1)) ? 1.0 : 0.0;
                    final double z = eta + (yc - pc) / wc;

                    final double wz = wc * z;
                    for (int a = 0; a < M; a++) v[a] += wz * phi[a];

                    for (int a = 0; a < M; a++) {
                        final double pa = wc * phi[a];
                        for (int b = 0; b <= a; b++) G.add(a, b, pa * phi[b]);
                    }
                }

                // sym + ridge (no intercept penalty)
                for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
                for (int a = 1; a < M; a++) G.add(a, a, ridge);

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return Double.NaN;
                final DMatrixRMaj L = chol.getT(null);

                final double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            final double ll = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
            final double obj = -ll;

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        return multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
    }

    // =========================================================
    // Z strata: quantile bins (cont Z) + exact match (disc Z)
    // groups are indices in useRows-space
    // =========================================================

    private int[][] getStrata(int[] zIdx, int[] useRows) {
        if (zIdx.length == 0) return new int[][]{range(useRows.length)};

        long rowsSig = signature(useRows);
        StrataKey key = new StrataKey(zIdx, rowsSig, binsPerContZ, minStratumSize);
        return strataCache.computeIfAbsent(key, kk -> buildStrata(zIdx, useRows, binsPerContZ, minStratumSize));
    }

    private int[][] buildStrata(int[] zIdx, int[] useRows, int binsPerCont, int minSize) {
        int n = useRows.length;

        int[] zDisc = filterDiscrete(zIdx);
        int[] zCont = filterContinuous(zIdx);

        // edges for continuous Z, computed on useRows
        double[][] edges = new double[zCont.length][];
        for (int j = 0; j < zCont.length; j++) {
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = zCols[zCont[j]][useRows[i]];
            edges[j] = quantileEdges(vals, binsPerCont);
        }

        HashMap<StratumSignature, IntArrayList> buckets = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int row = useRows[i];

            int[] parts = new int[zDisc.length + zCont.length];
            int k = 0;

            for (int v : zDisc) {
                parts[k++] = data.getInt(row, v);
            }
            for (int j = 0; j < zCont.length; j++) {
                double val = zCols[zCont[j]][row];
                parts[k++] = binIndex(edges[j], val);
            }

            StratumSignature sig = new StratumSignature(parts);
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i);
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : buckets.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    // =========================================================
    // Missingness / row filtering
    // =========================================================

    private int[] rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        int[] tmp = new int[baseRows.size()];
        int m = 0;

        outer:
        for (int r : baseRows) {
            if (!isValuePresent(r, ix)) continue;
            if (!isValuePresent(r, iy)) continue;
            for (int j : iz) if (!isValuePresent(r, j)) continue outer;
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    private boolean isValuePresent(int row, int col) {
        if (isDiscrete(col)) {
            return data.getInt(row, col) != DiscreteVariable.MISSING_VALUE;
        } else {
            return !Double.isNaN(data.getDouble(row, col));
        }
    }

    // =========================================================
    // Permutation helpers: shuffle Y within each stratum
    // =========================================================

    private static void shuffleWithinStrataDouble(double[] y, int[][] strata, SplittableRandom rng) {
        for (int[] g : strata) {
            for (int i = g.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int ii = g[i], jj = g[j];
                double tmp = y[ii];
                y[ii] = y[jj];
                y[jj] = tmp;
            }
        }
    }

    private static void shuffleWithinStrataInt(int[] y, int[][] strata, SplittableRandom rng) {
        for (int[] g : strata) {
            for (int i = g.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int ii = g[i], jj = g[j];
                int tmp = y[ii];
                y[ii] = y[jj];
                y[jj] = tmp;
            }
        }
    }

    // =========================================================
    // Extraction
    // =========================================================

    private double[] extractContinuousY(int yIndex, int[] useRows) {
        int n = useRows.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = zCols[yIndex][useRows[i]];
        return y;
    }

    private int[] extractDiscreteY(int yIndex, int[] useRows) {
        int n = useRows.length;
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = data.getInt(useRows[i], yIndex);
        return y;
    }

    // =========================================================
    // Public API / setters / interface
    // =========================================================

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setPermutations(int B) {
        this.permutations = Math.max(50, B);
    }

    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    public void setBinsPerContZ(int b) {
        this.binsPerContZ = Math.max(2, b);
        strataCache.clear();
    }

    public void setMinStratumSize(int m) {
        this.minStratumSize = Math.max(2, m);
        strataCache.clear();
    }

    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
    }

    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
    }

    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
    }

    public void setRffFeatures(int d) {
        if (d < 16) throw new IllegalArgumentException("rffFeatures should be >= 16");
        this.rffFeatures = d;
        // (design depends on this; no caches besides strata)
    }

    public void setRffSigma(double sigma) {
        if (!(sigma > 0) || !Double.isFinite(sigma)) throw new IllegalArgumentException("rffSigma must be finite and > 0");
        this.rffSigma = sigma;
    }

    public void setRffSeed(long seed) {
        this.rffSeed = seed;
    }

    public void setIrlsIters(int iters) {
        this.irlsIters = Math.max(1, iters);
    }

    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
    }

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            strataCache.clear();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }
        this.rows = new ArrayList<>(rows);
        strataCache.clear();
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        return this;
    }

    @Override
    public String toString() {
        return "Minimax-t RFF CI test (mixed)";
    }

    // =========================================================
    // Type utilities
    // =========================================================

    private int idx(Node v) {
        Integer i = indexMap.get(v.getName());
        if (i == null) throw new IllegalArgumentException("Unknown variable: " + v);
        return i;
    }

    private int[] idxSorted(Set<Node> z) {
        int[] out = new int[z.size()];
        int k = 0;
        for (Node v : z) out[k++] = idx(v);
        Arrays.sort(out);
        return out;
    }

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        ArrayList<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private boolean isDiscrete(int col) {
        return variables.get(col) instanceof DiscreteVariable;
    }

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int v : cols) if (!isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (!isDiscrete(v)) out[k++] = v;
        return out;
    }

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
    }

    private int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    // =========================================================
    // Basic math + linear algebra helpers
    // =========================================================

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    private static void zscoreColumnPreserveNaN(double[] in, double[] out) {
        double sum = 0.0, sum2 = 0.0;
        int n = 0;
        for (double v : in) {
            if (Double.isNaN(v)) continue;
            sum += v;
            sum2 += v * v;
            n++;
        }
        if (n < 2) {
            System.arraycopy(in, 0, out, 0, in.length);
            return;
        }
        double mean = sum / n;
        double var = (sum2 - n * mean * mean) / (n - 1.0);
        double sd = sqrt(max(1e-12, var));
        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    private static double studentTLogLik(double[] y, double[] yhat, double nu, double scale) {
        int n = y.length;
        double c = logGamma(0.5 * (nu + 1.0)) - logGamma(0.5 * nu)
                - 0.5 * log(nu * PI) - log(scale);

        double sum = 0.0;
        double inv = 1.0 / (nu * scale * scale);

        for (int i = 0; i < n; i++) {
            double r = y[i] - yhat[i];
            double v = 1.0 + (r * r) * inv;
            sum += c - 0.5 * (nu + 1.0) * log(v);
        }
        return sum;
    }

    private static double logGamma(double x) {
        double[] p = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };
        int g = 7;

        if (x < 0.5) {
            return log(PI) - log(sin(PI * x)) - logGamma(1.0 - x);
        }

        x -= 1.0;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) a += p[i] / (x + i + 1.0);

        double t = x + g + 0.5;
        return 0.5 * log(2.0 * PI) + (x + 0.5) * log(t) - t + log(a);
    }

    private static double[] solveFromCholeskyLower(DMatrixRMaj L, double[] b) {
        int n = b.length;
        double[] x = Arrays.copyOf(b, n);

        // forward solve L u = b
        for (int i = 0; i < n; i++) {
            double sum = x[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * x[j];
            x[i] = sum / L.get(i, i);
        }

        // back solve L^T x = u
        for (int i = n - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * x[j];
            x[i] = sum / L.get(i, i);
        }
        return x;
    }

    // =========================================================
    // Multinomial helpers
    // =========================================================

    private static double[][] softmaxProbsFromPhi(int K, int n,
                                                  double[][] beta,
                                                  double[][] Phi,
                                                  double[] logitsScratch) {
        final int C = K - 1;
        final double[][] p = new double[n][K];

        for (int i = 0; i < n; i++) {
            final double[] phi = Phi[i];

            logitsScratch[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < phi.length; a++) s += phi[a] * beta[a][c];
                logitsScratch[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) {
                final double e = exp(logitsScratch[k] - maxLog);
                p[i][k] = e;
                sum += e;
            }

            final double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) p[i][k] *= inv;
        }

        return p;
    }

    private static double multinomialLogLikFromPhi(int[] y, int K, int n,
                                                   double[][] beta,
                                                   double[][] Phi,
                                                   double[] logitsScratch) {
        final int C = K - 1;
        double ll = 0.0;

        for (int i = 0; i < n; i++) {
            final double[] phi = Phi[i];

            logitsScratch[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < phi.length; a++) s += phi[a] * beta[a][c];
                logitsScratch[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += exp(logitsScratch[k] - maxLog);

            int yi = y[i];
            if (yi < 0 || yi >= K) return Double.NaN;

            ll += (logitsScratch[yi] - maxLog) - log(sum);
        }

        return ll;
    }

    // =========================================================
    // Stratification helpers
    // =========================================================

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private static long signature(int[] rows) {
        long h = 1469598103934665603L;
        for (int r : rows) {
            h ^= r;
            h *= 1099511628211L;
        }
        return h;
    }

    private static int binIndex(double[] edges, double v) {
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static double[] quantileEdges(double[] x, int bins) {
        bins = Math.max(2, bins);
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);

        int n = a.length;
        double[] edges = new double[bins - 1];

        double last = Double.NEGATIVE_INFINITY;
        for (int b = 1; b < bins; b++) {
            double q = b / (double) bins;
            int idx = (int) Math.floor(q * (n - 1));
            idx = Math.min(Math.max(0, idx), n - 1);

            double e = a[idx];
            if (e <= last) {
                int j = idx;
                while (j + 1 < n && a[j] <= last) j++;
                e = a[j];
            }

            edges[b - 1] = e;
            last = e;
        }

        return edges;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // =========================================================
    // Small utility classes (strata cache + one-hot spec)
    // =========================================================

    private record StrataKey(int[] zIdx, long rowsSig, int bins, int minSize) {
        StrataKey {
            zIdx = (zIdx == null) ? new int[0] : Arrays.copyOf(zIdx, zIdx.length);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(zIdx);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(bins);
            h = 31 * h + Integer.hashCode(minSize);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StrataKey k)) return false;
            return rowsSig == k.rowsSig && bins == k.bins && minSize == k.minSize && Arrays.equals(zIdx, k.zIdx);
        }
    }

    private static final class StratumSignature {
        final int[] parts;
        final int hash;

        StratumSignature(int[] parts) {
            this.parts = parts;
            this.hash = Arrays.hashCode(parts);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            return (o instanceof StratumSignature s) && Arrays.equals(parts, s.parts);
        }
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        int size() { return n; }

        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private static final class OneHotSpec {
        final int[] sizes;     // num categories per disc parent
        final int[] offsets;   // offsets into the one-hot block (baseline dropped)
        final int totalCols;

        OneHotSpec(int[] sizes, int[] offsets, int totalCols) {
            this.sizes = sizes;
            this.offsets = offsets;
            this.totalCols = totalCols;
        }
    }
}