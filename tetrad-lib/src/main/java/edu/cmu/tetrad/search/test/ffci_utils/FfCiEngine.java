package edu.cmu.tetrad.search.test.ffci_utils;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.QuadraticFormPValues;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Shared CI “engine” for FF-CI / RCIT-style tests.
 * <p>
 * This version matches your CiState exactly:
 * - state.data, state.rowsView
 * - state.featureCache (String -> SimpleMatrix)
 * - state.solverCache  (String -> Object)  // we store SimpleMatrix L in it
 * - state.sigmaCache   (String -> Double)  // optional; used only if you want it
 * - state.rng          (base rng)          // NOT mutated; we derive per-call RNG from cfg.seed + key
 */
public final class FfCiEngine {

    private static double pValue(FfCiConfig cfg, double stat, double[] eig) {
        if (eig == null || eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        // Recommended “non-rotated” mapping:
        return switch (cfg.approx()) {
            case GAMMA_SATTERTHWAITE -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            case LUGANNANI_RICE -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
            case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
            case PERMUTATION -> {
                // permutations==0 => fallback
                if (cfg.permutations() > 0) yield Double.NaN;
                yield QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            }
            default -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
        };
    }

    // ============================================================
    // Core results
    // ============================================================

    private static SimpleMatrix features(
            FfCiState state,
            FfCiConfig cfg,
            String tag,
            SimpleMatrix raw,
            List<Node> varsForKey,
            double sigma,
            int numF,
            long seed
    ) {
        String key = keyFeat(state, cfg, tag, varsForKey, numF, sigma, seed);

        return state.featureCache.computeIfAbsent(key, k -> {
            Random rr = new Random(seed);
            SimpleMatrix feat = rff(raw, numF, sigma, rr);

            if (cfg.centerFeatures()) zscoreInPlace(feat);
            else subtractColumnMeansInPlace(feat);

            return feat;
        });
    }

    private static String keyFeat(
            FfCiState state, FfCiConfig cfg, String tag,
            List<Node> varsForKey, int numF, double sigma, long seed
    ) {
        ArrayList<String> names = new ArrayList<>(varsForKey.size());
        for (Node v : varsForKey) names.add(v.getName());
        names.sort(String::compareTo);

        // NOTE: this assumes RowsView exposes rowsHash(); if not, replace with whatever you have.
        int rowsHash = state.rowsView.rowsHash(); // <-- rename if needed

        StringBuilder sb = new StringBuilder(160);
        sb.append(tag)
                .append("|n=").append(state.rowsView.nActive())
                .append("|rows=").append(rowsHash)
                .append("|F=").append(numF)
                .append("|sig=").append(Double.doubleToLongBits(sigma))
                .append("|ctr=").append(cfg.centerFeatures() ? 1 : 0)
                .append("|seed=").append(seed)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    // ============================================================
    // p-values / approximations
    // ============================================================

    private static SimpleMatrix cholCached(
            FfCiState state, FfCiConfig cfg, List<Node> Z, double sigZ, int n, SimpleMatrix A, long seedZ
    ) {
        String key = keyChol(state, cfg, Z, sigZ, n, seedZ);

        Object v = state.solverCache.computeIfAbsent(key, k -> choleskyLower(A));
        return (SimpleMatrix) v;
    }

    // ============================================================
    // Feature generation + caching
    // ============================================================

    private static String keyChol(FfCiState state, FfCiConfig cfg, List<Node> Z, double sigZ, int n, long seedZ) {
        ArrayList<String> names = new ArrayList<>(Z.size());
        for (Node v : Z) names.add(v.getName());
        names.sort(String::compareTo);

        int rowsHash = state.rowsView.rowsHash(); // <-- rename if needed

        StringBuilder sb = new StringBuilder(180);
        sb.append("cholZ")
                .append("|n=").append(n)
                .append("|rows=").append(rowsHash)
                .append("|Fz=").append(cfg.numFeatZ())
                .append("|lam=").append(Double.doubleToLongBits(cfg.lambda()))
                .append("|sigZ=").append(Double.doubleToLongBits(sigZ))
                .append("|seedZ=").append(seedZ)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    private static long seedX(FfCiConfig cfg, FfCiInput in) {
        return mix64(cfg.seed() ^ hashName(in.x().getName()) ^ ((long) in.nActive() << 1));
    }

    // ============================================================
    // Cholesky caching (solverCache: String -> Object)
    // ============================================================

    private static long seedY(FfCiConfig cfg, FfCiInput in) {
        long h = cfg.seed() ^ hashName(in.y().getName()) ^ ((long) in.nActive() << 2);
        if (cfg.doRcit() && !in.zSorted().isEmpty()) {
            ArrayList<String> names = new ArrayList<>(in.zSorted().size());
            for (Node z : in.zSorted()) names.add(z.getName());
            names.sort(String::compareTo);
            for (String s : names) h ^= hashName(s);
        }
        return mix64(h);
    }

    private static long seedZ(FfCiConfig cfg, FfCiInput in) {
        long h = cfg.seed() ^ ((long) in.nActive() << 3);
        if (!in.zSorted().isEmpty()) {
            ArrayList<String> names = new ArrayList<>(in.zSorted().size());
            for (Node z : in.zSorted()) names.add(z.getName());
            names.sort(String::compareTo);
            for (String s : names) h ^= hashName(s);
        }
        return mix64(h);
    }

    // ============================================================
    // Seeds / input helpers
    // ============================================================

    private static long hashName(String s) {
        return 0x9E3779B97F4A7C15L ^ (long) s.hashCode();
    }

    private static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    private static List<Node> sortByName(Set<Node> z) {
        if (z == null || z.isEmpty()) return new ArrayList<>();
        ArrayList<Node> out = new ArrayList<>(z);
        out.sort(Comparator.comparing(Node::getName));
        return out;
    }

    private static List<Node> hstackVarList(Node y, List<Node> Z) {
        ArrayList<Node> out = new ArrayList<>(1 + Z.size());
        out.add(y);
        out.addAll(Z);
        return out;
    }

    private static SimpleMatrix hstack(SimpleMatrix A, SimpleMatrix B) {
        if (A.getNumRows() != B.getNumRows()) throw new IllegalArgumentException("Row mismatch");
        SimpleMatrix out = new SimpleMatrix(A.getNumRows(), A.getNumCols() + B.getNumCols());
        out.insertIntoThis(0, 0, A);
        out.insertIntoThis(0, A.getNumCols(), B);
        return out;
    }

    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) return;
        for (int j = 0; j < d; j++) {
            double sum = 0, sumsq = 0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    // ============================================================
    // Linear algebra / stats helpers (same as before)
    // ============================================================

    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) s += v * v;
        return s;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static void subtractColumnMeansInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n == 0 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += M.get(i, j);
            double mean = s / n;
            for (int i = 0; i < n; i++) M.set(i, j, M.get(i, j) - mean);
        }
    }

    private static SimpleMatrix kronResCov(SimpleMatrix resX, SimpleMatrix resY) {
        int Fx = resX.getNumCols(), Fy = resY.getNumCols(), q = Fx * Fy, n = resX.getNumRows();
        SimpleMatrix Z = new SimpleMatrix(n, q);
        int idx = 0;
        for (int a = 0; a < Fx; a++) {
            for (int b = 0; b < Fy; b++) {
                for (int i = 0; i < n; i++) Z.set(i, idx, resX.get(i, a) * resY.get(i, b));
                idx++;
            }
        }
        return Z.transpose().mult(Z).scale(1.0 / (n - 1));
    }

    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        int m = evd.getNumberOfEigenvalues();
        ArrayList<Double> pos = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double lam = evd.getEigenvalue(i).getReal();
            if (lam > 1e-12 && Double.isFinite(lam)) pos.add(lam);
        }
        double[] e = new double[pos.size()];
        for (int i = 0; i < e.length; i++) e[i] = pos.get(i);
        return e;
    }

    private static SimpleMatrix rff(SimpleMatrix X, int numF, double sigma, Random rng) {
        int n = X.getNumRows(), d = X.getNumCols();
        if (sigma <= 0 || !Double.isFinite(sigma)) sigma = 1.0;

        double invSigma = 1.0 / sigma;
        double[] b = new double[numF];
        double twoPi = 2.0 * Math.PI;
        for (int i = 0; i < numF; i++) b[i] = rng.nextDouble() * twoPi;

        double[] W = new double[numF * d];
        for (int i = 0; i < numF; i++) {
            int base = i * d;
            for (int j = 0; j < d; j++) W[base + j] = rng.nextGaussian() * invSigma;
        }

        SimpleMatrix feat = new SimpleMatrix(n, numF);
        double scale = Math.sqrt(2.0 / numF);

        for (int r = 0; r < n; r++) {
            for (int f = 0; f < numF; f++) {
                int base = f * d;
                double dot = 0.0;
                for (int j = 0; j < d; j++) dot += W[base + j] * X.get(r, j);
                feat.set(r, f, scale * Math.cos(dot + b[f]));
            }
        }
        return feat;
    }

    private static int[] randomPermutation(int n, Random rng) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    private static SimpleMatrix covWithPermutedB(SimpleMatrix A, SimpleMatrix B, int[] perm) {
        int n = A.getNumRows();
        int p = A.getNumCols();
        int q = B.getNumCols();
        SimpleMatrix C = new SimpleMatrix(p, q);

        for (int i = 0; i < n; i++) {
            int bi = perm[i];
            for (int a = 0; a < p; a++) {
                double av = A.get(i, a);
                for (int b = 0; b < q; b++) {
                    C.set(a, b, C.get(a, b) + av * B.get(bi, b));
                }
            }
        }
        return C.scale(1.0 / (n - 1));
    }

    private static SimpleMatrix choleskyLower(SimpleMatrix A) {
        int n = A.getNumRows();
        SimpleMatrix L = new SimpleMatrix(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A.get(i, j);
                for (int k = 0; k < j; k++) sum -= L.get(i, k) * L.get(j, k);
                if (i == j) {
                    if (sum <= 1e-18) sum = 1e-18;
                    L.set(i, j, Math.sqrt(sum));
                } else {
                    L.set(i, j, sum / L.get(j, j));
                }
            }
        }
        return L;
    }

    private static SimpleMatrix cholSolve(SimpleMatrix L, SimpleMatrix B) {
        int n = L.getNumRows();
        int m = B.getNumCols();

        SimpleMatrix Y = new SimpleMatrix(n, m);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double sum = B.get(i, j);
                for (int k = 0; k < i; k++) sum -= L.get(i, k) * Y.get(k, j);
                Y.set(i, j, sum / L.get(i, i));
            }
        }

        SimpleMatrix X = new SimpleMatrix(n, m);
        for (int i = n - 1; i >= 0; i--) {
            for (int j = 0; j < m; j++) {
                double sum = Y.get(i, j);
                for (int k = i + 1; k < n; k++) sum -= L.get(k, i) * X.get(k, j);
                X.set(i, j, sum / L.get(i, i));
            }
        }
        return X;
    }

    public IndependenceResult test(FfCiState state, FfCiConfig cfg, Node x, Node y, Set<Node> z)
            throws InterruptedException {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        List<Node> Z = sortByName(z);
        FfCiInput in = new FfCiInput(x, y, Z, state.rowsView.nActive());

        // 1) load raw columns (active rows)
        SimpleMatrix X = state.rowsView.cols(state.data, List.of(x));
        SimpleMatrix Y = state.rowsView.cols(state.data, List.of(y));
        SimpleMatrix Zm = Z.isEmpty() ? new SimpleMatrix(in.nActive(), 0) : state.rowsView.cols(state.data, Z);

        // 2) standardize
        zscoreInPlace(X);
        zscoreInPlace(Y);
        zscoreInPlace(Zm);

        // 3) form Yaug if RCIT
        SimpleMatrix Yaug = (cfg.doRcit() && Zm.numCols() > 0) ? hstack(Y, Zm) : Y;

        // 4) bandwidths (you already have this abstraction)
        double sigX = cfg.bandwidth().sigma(X, new BandwidthContext("X", List.of(x)), state);
        double sigY = cfg.bandwidth().sigma(
                Yaug,
                new BandwidthContext(cfg.doRcit() ? "Yaug" : "Y",
                        cfg.doRcit() ? hstackVarList(y, Z) : List.of(y)),
                state
        );
        double sigZ = (Zm.numCols() == 0) ? 1.0 : cfg.bandwidth().sigma(Zm, new BandwidthContext("Z", Z), state);

        // 5) features (cached)
        SimpleMatrix fX = features(state, cfg, "fX", X, List.of(x), sigX, cfg.numFeatXY(), seedX(cfg, in));
        SimpleMatrix fY = features(state, cfg, "fY", Yaug,
                cfg.doRcit() ? hstackVarList(y, Z) : List.of(y),
                sigY, cfg.numFeatXY(), seedY(cfg, in));
        SimpleMatrix fZ = (Zm.numCols() == 0) ? null :
                features(state, cfg, "fZ", Zm, Z, sigZ, cfg.numFeatZ(), seedZ(cfg, in));

        // 6) compute stat and p
        return (fZ == null || fZ.numCols() == 0)
                ? ritResult(cfg, x, y, Z, fX, fY, in)
                : rcitResult(state, cfg, x, y, Z, fX, fY, fZ, in, sigZ);
    }

    private IndependenceResult ritResult(
            FfCiConfig cfg,
            Node x, Node y, List<Node> Z,
            SimpleMatrix fX, SimpleMatrix fY,
            FfCiInput in
    ) {
        int n = in.nActive();

        SimpleMatrix Cxy = cov(fX, fY);
        double stat = n * frob2(Cxy);

        // Residual products covariance (null eigs)
        SimpleMatrix resX = fX.copy();
        SimpleMatrix resY = fY.copy();
        subtractColumnMeansInPlace(resX);
        subtractColumnMeansInPlace(resY);

        SimpleMatrix Cov = kronResCov(resX, resY);
        double[] eig = positiveEigs(Cov);

        double p = pValue(cfg, stat, eig);
        p = clamp01(p);

        if (cfg.verbose()) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " p=" + p + " stat=" + stat);
        }

        boolean indep = (p > cfg.alpha());
        return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), indep, p, cfg.alpha() - p);
    }

    private IndependenceResult rcitResult(
            FfCiState state, FfCiConfig cfg,
            Node x, Node y, List<Node> Z,
            SimpleMatrix fX, SimpleMatrix fY, SimpleMatrix fZ,
            FfCiInput in,
            double sigZ
    ) {
        int n = in.nActive();

        // Cov blocks
        SimpleMatrix Cxy = cov(fX, fY);
        SimpleMatrix Czz = cov(fZ, fZ);
        SimpleMatrix Cxz = cov(fX, fZ);
        SimpleMatrix Czy = cov(fZ, fY);

        // A = Czz + λI
        double lambda = Math.max(1e-12, cfg.lambda());
        SimpleMatrix A = Czz.plus(SimpleMatrix.identity(Czz.getNumRows()).scale(lambda));

        // Cholesky cache in state.solverCache (Object-valued)
        long seedZ = seedZ(cfg, in);
        SimpleMatrix L = cholCached(state, cfg, Z, sigZ, n, A, seedZ);

        // U = Cxz * inv(A) without forming inv
        SimpleMatrix U_T = cholSolve(L, Cxz.transpose()); // solves A * U_T = Cxz^T
        SimpleMatrix U = U_T.transpose();

        // conditional cross-cov
        SimpleMatrix Cxy_z = Cxy.minus(U.mult(Czy));
        double stat = n * frob2(Cxy_z);

        // -------- permutation on residualized features (fast) --------
        if (cfg.approx() == PValueMethod.PERMUTATION && cfg.permutations() > 0) {
            SimpleMatrix V = U_T;               // inv(A) * Cxz^T
            SimpleMatrix W = cholSolve(L, Czy); // inv(A) * Czy

            SimpleMatrix e_x_z = fZ.mult(V);
            SimpleMatrix e_y_z = fZ.mult(W);

            SimpleMatrix resX = fX.minus(e_x_z);
            SimpleMatrix resY = fY.minus(e_y_z);
            subtractColumnMeansInPlace(resX);
            subtractColumnMeansInPlace(resY);

            SimpleMatrix Cobs = cov(resX, resY);
            double statObs = n * frob2(Cobs);

            int greater = 0;
            Random rng = new Random(mix64(cfg.seed() ^ seedZ ^ 0xD1B54A32D192ED03L)); // deterministic per (x,y,Z,rows)

            for (int b = 0; b < cfg.permutations(); b++) {
                int[] perm = randomPermutation(n, rng);
                SimpleMatrix Cperm = covWithPermutedB(resX, resY, perm);
                double s = n * frob2(Cperm);
                if (s >= statObs) greater++;
            }
            double p = (greater + 1.0) / (cfg.permutations() + 1.0);
            p = clamp01(p);

            if (cfg.verbose()) {
                TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " p=" + p + " stat=" + statObs);
            }

            boolean indep = (p > cfg.alpha());
            return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), indep, p, cfg.alpha() - p);
        }

        // -------- quadratic form null via eigs of kronResCov(resX,resY) --------
        SimpleMatrix V = U_T;               // inv(A) * Cxz^T
        SimpleMatrix W = cholSolve(L, Czy); // inv(A) * Czy

        SimpleMatrix e_x_z = fZ.mult(V);
        SimpleMatrix e_y_z = fZ.mult(W);

        SimpleMatrix resX = fX.minus(e_x_z);
        SimpleMatrix resY = fY.minus(e_y_z);
        subtractColumnMeansInPlace(resX);
        subtractColumnMeansInPlace(resY);

        SimpleMatrix Cov = kronResCov(resX, resY);
        double[] eig = positiveEigs(Cov);

        double p = pValue(cfg, stat, eig);
        p = clamp01(p);

        if (cfg.verbose()) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " p=" + p + " stat=" + stat);
        }

        boolean indep = (p > cfg.alpha());
        return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), indep, p, cfg.alpha() - p);
    }
}