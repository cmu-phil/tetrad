package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.sqrt;

/**
 * GIN (Matlab-style): cluster -> merge overlaps -> orient latents by GIN test (IT-mode).
 * Lightweight anti-clique controls:
 *  - asymmetry gate (p_ij - p_ji >= delta)
 *  - max in-degree cap
 *  - small margin above alpha
 *  - singular-gap check on the projection SVD
 *
 * Added:
 *  - held-out projection: fit ω on a train split; test e ⟂ Z on a disjoint test split
 *  - light consensus over K repeats (default 3), Fisher or median combine
 */
public class Gin {

    // ----------------------------- Modes/params -----------------------------
    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final OrderMode orderMode;
    private final List<Node> nodes;

    private boolean verbose = false;

    // SVD / whitening
    private boolean whitenBeforeSVD = true; // numerical guard
    private double ridge = 1e-8;            // ridge for Σ_YY/Σ_ZZ when whitening

    // Anti-clique knobs (cheap):
    private double addMargin = 1e-3;        // require p >= alpha + margin
    private double asymmetryDelta = 0.05;   // require p_ij - p_ji >= delta
    private int maxInDegree = 0;            // 0 or negative disables capping
    private double gapThreshold = 0.90;     // accept if (σ_min / σ_next) <= gapThreshold

    // Held-out & consensus
    private boolean useHoldout = true;
    private double trainFrac = 0.70;        // fraction of rows to fit ω
    private int consensusRepeats = 3;       // K small repeats
    private ConsensusMode consensusMode = ConsensusMode.FISHER;
    private long randomSeed = 17L;
    private Random rng = new Random(randomSeed);

    // ----------------------------- State -----------------------------
    private DataSet data;
    private SimpleMatrix cov;               // Σ of observed (full-sample)
    private List<Node> vars;                // observed nodes

    // ----------------------------- Ctors -----------------------------
    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this(alpha, test, OrderMode.IT);
    }

    public Gin(double alpha, RawMarginalIndependenceTest test, OrderMode orderMode) {
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
        this.orderMode = (orderMode == null) ? OrderMode.IT : orderMode;
        this.nodes = ((IndependenceTest) test).getVariables();
    }

    // ----------------------------- Config -----------------------------
    public void setVerbose(boolean v) { this.verbose = v; }
    public void setWhitenBeforeSVD(boolean w) { this.whitenBeforeSVD = w; }
    public void setRidge(double r) { this.ridge = Math.max(0.0, r); }
    /** Set to 0.0 to revert to “p ≥ alpha” without cushion. */
    public void setAddMargin(double m) { this.addMargin = Math.max(0.0, m); }
    /** Asymmetry cushion: require p_ij - p_ji ≥ delta to add i->j. */
    public void setAsymmetryDelta(double d) { this.asymmetryDelta = Math.max(0.0, d); }
    /** Max number of parents per latent; ≤0 disables the cap. */
    public void setMaxInDegree(int k) { this.maxInDegree = k; }
    /** Require σ_min / σ_next ≤ a threshold to accept the projection direction. */
    public void setGapThreshold(double t) { this.gapThreshold = Math.min(1.0, Math.max(0.0, t)); }

    /** Enable/disable held-out ω. */
    public void setUseHoldout(boolean useHoldout) { this.useHoldout = useHoldout; }
    /** Train fraction for held-out ω (0<frac<1). */
    public void setTrainFrac(double trainFrac) {
        this.trainFrac = Math.min(0.95, Math.max(0.5, trainFrac));
    }
    /** Number of consensus repeats (small, like 3–5). */
    public void setConsensusRepeats(int k) { this.consensusRepeats = Math.max(1, k); }
    /** Fisher or median combine. */
    public void setConsensusMode(ConsensusMode mode) { this.consensusMode = (mode==null?ConsensusMode.FISHER:mode); }
    /** RNG seed for splits. */
    public void setRandomSeed(long seed) { this.randomSeed = seed; this.rng = new Random(seed); }

    // ----------------------------- API -------------------------------
    public Graph search(DataSet data) {
        this.data = data;
        CorrelationMatrix corr = new CorrelationMatrix(data);
        this.cov = new SimpleMatrix(corr.getMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        // 1) Find causal clusters (you’re using TSC outside; keep as-is here)
        List<List<Integer>> clusters = findCausalClusters();

        // Fallback to singletons if nothing survived
        if (clusters.isEmpty()) {
            List<List<Integer>> singletons = new ArrayList<>();
            for (int i = 0; i < vars.size(); i++) singletons.add(List.of(i));
            clusters = singletons;
            if (verbose) TetradLogger.getInstance().log("[GIN] No seed clusters found; falling back to singletons.");
        }

        if (verbose) TetradLogger.getInstance().log("[GIN] clusters=" + clustersAsNames(clusters));

        // 2) Build graph with a latent per cluster
        Graph g = new EdgeListGraph();
        for (Node v : vars) g.addNode(v);

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node L = new GraphNode("L" + (i + 1));
            L.setNodeType(NodeType.LATENT);
            g.addNode(L);
            latents.add(L);
            for (int idx : clusters.get(i)) g.addDirectedEdge(L, vars.get(idx));
        }

        // 3) Orient latent-latent edges (Find_CO_by_IT / GIN_Condition_Test)
        if (orderMode == OrderMode.IT) {
            orientByIndependenceFast(g, clusters, latents);
        } else {
            if (verbose) TetradLogger.getInstance().log("[GIN] MI mode requested but not implemented; using IT.");
            orientByIndependenceFast(g, clusters, latents);
        }

        return g;
    }

    // ---------------------- Clusters (plug-in) ------------------------
    private List<List<Integer>> findCausalClusters() {
        Tsc tsc = new Tsc(data.getVariables(), new CorrelationMatrix(data));
        tsc.setAlpha(alpha);
        tsc.setMinRedundancy(0);

        List<List<Integer>> seeds = new ArrayList<>();
        for (Set<Integer> seed : tsc.findClusters().keySet()) {
            seeds.add(new ArrayList<>(seed));
        }
        if (verbose) System.out.println(seeds);
        return seeds;
    }

    // ---------------------- Orientation (fast IT-mode) --------------------
    /**
     * Compute both directions once per pair (i<j) to halve work.
     * Apply: gap guard, asymmetry gate, in-degree cap, margin above alpha.
     * Held-out ω + K-repeat consensus lives in consensusPDir().
     */
    private void orientByIndependenceFast(Graph g, List<List<Integer>> clusters, List<Node> latents) {
        int m = clusters.size();
        List<EdgeCand> cands = new ArrayList<>(2 * m * (m - 1) / 2);

        // Precompute both directions once per unordered pair
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (Thread.currentThread().isInterrupted()) return;

                List<Integer> Zi = clusters.get(i);
                List<Integer> Yj = clusters.get(j);
                List<Integer> Zj = clusters.get(j);
                List<Integer> Yi = clusters.get(i);

                if (verbose) {
                    TetradLogger.getInstance().log("Checking <" + names(Yj) + ">; <" + names(Zi)
                                                   + "> and <" + names(Yi) + ">; <" + names(Zj) + ">");
                }

                // consensus on i -> j and j -> i
                DirResult dir_ij = consensusPDir(Yj, Zi);
                DirResult dir_ji = consensusPDir(Yi, Zj);

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[GIN] pair L%d↔L%d : p_ij=%.4g (gapOK=%s) | p_ji=%.4g (gapOK=%s)",
                            i + 1, j + 1, dir_ij.p, dir_ij.gapOk ? "Y" : "N",
                            dir_ji.p, dir_ji.gapOk ? "Y" : "N"));
                }

                // If gap fails, treat as very small p so it won’t pass gates
                double p_ij = dir_ij.gapOk ? dir_ij.p : 0.0;
                double p_ji = dir_ji.gapOk ? dir_ji.p : 0.0;

                cands.add(new EdgeCand(i, j, p_ij, p_ji));
                cands.add(new EdgeCand(j, i, p_ji, p_ij));
            }
        }

        // Greedy add edges with anti-clique gates
        cands.sort(Comparator.comparingDouble((EdgeCand c) -> c.p).reversed());
        int[] inDeg = new int[m];

        for (EdgeCand c : cands) {
            if (Thread.currentThread().isInterrupted()) return;

            // basic acceptance
            if (c.p < alpha + addMargin) continue;

            // asymmetry gate
            if (c.p - c.pOpp < asymmetryDelta) continue;

            Node from = latents.get(c.i);
            Node to = latents.get(c.j);

            // acyclicity + in-degree cap
            if (!g.isAncestorOf(to, from) && (maxInDegree <= 0 || inDeg[c.j] < maxInDegree)) {
                g.addDirectedEdge(from, to);
                inDeg[c.j]++;
            }
        }
    }

    // ---------------------- Consensus & held-out core -----------------
    private DirResult consensusPDir(List<Integer> Y, List<Integer> Z) {
        if (!useHoldout) {
            // Fall back to your old single-sample projection on full data
            ProjResult pr = computeProjectionFull(Y, Z);
            if (!pr.acceptable) return new DirResult(0.0, false);
            double p = pValueEvsZFull(pr.e, Z);
            return new DirResult(p, true);
        }

        int n = data.getNumRows();
        int nTrain = Math.max(2, (int) Math.floor(trainFrac * n));
        int nTest = Math.max(1, n - nTrain);
        if (nTest < 2) {
            // not enough to test; revert to full
            ProjResult pr = computeProjectionFull(Y, Z);
            if (!pr.acceptable) return new DirResult(0.0, false);
            double p = pValueEvsZFull(pr.e, Z);
            return new DirResult(p, true);
        }

        List<Double> pvals = new ArrayList<>(consensusRepeats);
        boolean allGapOk = true;

        for (int rep = 0; rep < consensusRepeats; rep++) {
            if (Thread.currentThread().isInterrupted()) break;

            // split rows
            int[] train = sampleWithoutReplacement(n, nTrain, rng);
            boolean[] isTrain = new boolean[n];
            for (int r : train) isTrain[r] = true;
            int[] test = new int[nTest];
            for (int i = 0, t = 0; i < n; i++) if (!isTrain[i]) test[t++] = i;

            // projection on TRAIN (gap check on train SVD)
            ProjSplitResult psr = computeProjectionOnTrainAndProjectTest(Y, Z, train, test);
            allGapOk &= psr.acceptable;

            // independence on TEST: e_test ⟂ Z_test
            double p = pValueEvsZSubset(psr.eTest, Z, test);
            pvals.add(safe01(p));
        }

        double pConsensus;
        if (consensusMode == ConsensusMode.MEDIAN) {
            Collections.sort(pvals);
            pConsensus = pvals.get(pvals.size() / 2);
        } else {
            // Fisher combine
            double stat = 0.0;
            int k = 0;
            for (double p : pvals) {
                double pc = Math.max(Math.min(p, 1.0), 1e-300);
                stat += -2.0 * Math.log(pc);
                k++;
            }
            int df = 2 * Math.max(1, k);
            pConsensus = chisqUpperTail(stat, df);
        }

        return new DirResult(pConsensus, allGapOk);
    }

    private static double safe01(double p) {
        if (!Double.isFinite(p)) return 1.0;
        if (p < 0) return 0.0;
        if (p > 1) return 1.0;
        return p;
    }

    // ---------------------- Math helpers -----------------------------
    /**
     * Projection on the full sample (your previous path).
     */
    private ProjResult computeProjectionFull(List<Integer> Y, List<Integer> Z) {
        final int n = data.getNumRows();
        if (Y == null || Z == null || Y.isEmpty() || Z.isEmpty()) {
            // omega won't be used when acceptable=false, but keep shapes sane if Y is known
            SimpleMatrix omegaDummy = (Y == null) ? new SimpleMatrix(0, 1) : new SimpleMatrix(Y.size(), 1);
            return new ProjResult(new double[n], omegaDummy, Double.POSITIVE_INFINITY, false);
        }

        // Σ blocks from full-sample covariance
        SimpleMatrix Syy = subCov(cov, Y, Y);
        SimpleMatrix Szz = subCov(cov, Z, Z);
        SimpleMatrix Szy = subCov(cov, Z, Y);

        // Yc (centered on full sample)
        SimpleMatrix Yc = new SimpleMatrix(n, Y.size());
        for (int j = 0; j < Y.size(); j++) {
            int col = Y.get(j);
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += data.getDouble(i, col);
            mean /= n;
            for (int i = 0; i < n; i++) Yc.set(i, j, data.getDouble(i, col) - mean);
        }

        return computeOmegaAndE(Yc, Syy, Szz, Szy);
    }

    /**
     * Projection trained on TRAIN rows; e evaluated on TEST rows.
     * Centering uses TRAIN means for both train and test (to avoid leakage).
     */
    private ProjSplitResult computeProjectionOnTrainAndProjectTest(List<Integer> Y, List<Integer> Z,
                                                                   int[] trainRows, int[] testRows) {
        int nTrain = trainRows.length;
        int nTest = testRows.length;

        // Build Yc_train, Zc_train (centered by TRAIN means)
        SimpleMatrix YcTrain = new SimpleMatrix(nTrain, Y.size());
        SimpleMatrix ZcTrain = new SimpleMatrix(nTrain, Z.size());

        double[] meanY = new double[Y.size()];
        double[] meanZ = new double[Z.size()];

        // means on TRAIN
        for (int j = 0; j < Y.size(); j++) {
            double s = 0.0;
            int col = Y.get(j);
            for (int r : trainRows) s += data.getDouble(r, col);
            meanY[j] = s / nTrain;
        }
        for (int j = 0; j < Z.size(); j++) {
            double s = 0.0;
            int col = Z.get(j);
            for (int r : trainRows) s += data.getDouble(r, col);
            meanZ[j] = s / nTrain;
        }

        for (int i = 0; i < nTrain; i++) {
            int r = trainRows[i];
            for (int j = 0; j < Y.size(); j++) {
                YcTrain.set(i, j, data.getDouble(r, Y.get(j)) - meanY[j]);
            }
            for (int j = 0; j < Z.size(); j++) {
                ZcTrain.set(i, j, data.getDouble(r, Z.get(j)) - meanZ[j]);
            }
        }

        // Cov blocks from TRAIN: Syy = (1/n) Y^T Y; Szz = (1/n) Z^T Z; Szy = (1/n) Z^T Y
        SimpleMatrix Syy = YcTrain.transpose().mult(YcTrain).scale(1.0 / nTrain);
        SimpleMatrix Szz = ZcTrain.transpose().mult(ZcTrain).scale(1.0 / nTrain);
        SimpleMatrix Szy = ZcTrain.transpose().mult(YcTrain).scale(1.0 / nTrain);

        // Solve ω and compute gap on TRAIN
        ProjResult trainPR = computeOmegaAndE(YcTrain, Syy, Szz, Szy);

        // Project TEST using TRAIN means & ω
        double[] eTest = new double[nTest];
        for (int i = 0; i < nTest; i++) {
            int r = testRows[i];
            double acc = 0.0;
            for (int j = 0; j < Y.size(); j++) {
                double y = data.getDouble(r, Y.get(j)) - meanY[j];
                acc += y * trainPR.omega.get(j, 0);
            }
            eTest[i] = acc;
        }

        return new ProjSplitResult(eTest, trainPR.gapRatio, trainPR.acceptable);
    }

    /**
     * From (Yc, Syy, Szz, Szy) compute ω (smallest σ right singular vector) with whitening if enabled,
     * and return e = Yc * ω along with the gap ratio and acceptability.
     */
    private ProjResult computeOmegaAndE(SimpleMatrix Yc,
                                        SimpleMatrix Syy,
                                        SimpleMatrix Szz,
                                        SimpleMatrix Szy) {
        SimpleMatrix omega;
        double gapRatio;

        if (whitenBeforeSVD) {
            // A = Σ_ZZ^{-1/2} Σ_ZY Σ_YY^{-1/2}
            SimpleMatrix SzzInvH = invSqrtSym(Szz, ridge);
            SimpleMatrix SyyInvH = invSqrtSym(Syy, ridge);
            SimpleMatrix A = SzzInvH.mult(Szy).mult(SyyInvH);

            SimpleSVD<SimpleMatrix> svd = A.svd();
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            int r = Math.min(W.getNumRows(), W.getNumCols());
            double minSv = Double.POSITIVE_INFINITY, nextSv = Double.POSITIVE_INFINITY;
            int minIdx = -1;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) {
                    nextSv = minSv;
                    minSv = sv;
                    minIdx = i;
                } else if (sv < nextSv) {
                    nextSv = sv;
                }
            }
            gapRatio = (nextSv == 0.0) ? 0.0 : (minSv / nextSv);
            if (minIdx < 0) minIdx = r - 1;

            SimpleMatrix vmin = V.extractVector(false, minIdx);
            omega = SyyInvH.mult(vmin); // map back

        } else {
            SimpleSVD<SimpleMatrix> svd = Szy.svd();
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            int r = Math.min(W.getNumRows(), W.getNumCols());
            double minSv = Double.POSITIVE_INFINITY, nextSv = Double.POSITIVE_INFINITY;
            int minIdx = -1;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) {
                    nextSv = minSv;
                    minSv = sv;
                    minIdx = i;
                } else if (sv < nextSv) {
                    nextSv = sv;
                }
            }
            gapRatio = (nextSv == 0.0) ? 0.0 : (minSv / nextSv);
            if (minIdx < 0) minIdx = r - 1;

            omega = V.extractVector(false, minIdx);
        }

        // e over the rows in Yc
        double[] e = Yc.mult(omega).getDDRM().getData();
        boolean ok = (gapRatio <= gapThreshold) || !Double.isFinite(gapRatio);
        return new ProjResult(e, omega, gapRatio, ok);
    }

    /**
     * Symmetric inverse square-root via EVD with ridge.
     */
    private static SimpleMatrix invSqrtSym(SimpleMatrix A, double ridge) {
        SimpleMatrix Ar = A.copy();
        if (ridge > 0) {
            DMatrixRMaj d = Ar.getDDRM();
            int n = d.getNumRows();
            for (int i = 0; i < n; i++) d.add(i, i, ridge);
        }
        SimpleEVD<SimpleMatrix> evd = Ar.eig();
        int n = Ar.getNumRows();
        SimpleMatrix U = new SimpleMatrix(n, n);
        SimpleMatrix Dm = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            double ev = evd.getEigenvalue(i).getReal();
            SimpleMatrix ui = evd.getEigenVector(i);
            if (ui == null) {
                ui = new SimpleMatrix(n, 1);
                ui.set(i, 0, 1.0);
            }
            double norm = ui.normF();
            if (norm > 0) ui = ui.divide(norm);
            U.insertIntoThis(0, i, ui);
            double v = (ev > 1e-12) ? 1.0 / sqrt(ev) : 0.0;
            Dm.set(i, i, v);
        }
        return U.mult(Dm).mult(U.transpose());
    }

    // ---------------------- p-values ---------------------------
    /** e ⟂ Z (full sample) using test.computePValue(e, Zcols). */
    private double pValueEvsZFull(double[] e, List<Integer> Z) {
        final int n = e.length;
        if (Z == null || Z.isEmpty()) return 1.0;

        double[][] Zcols = new double[n][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = Z.get(j);
            for (int i = 0; i < n; i++) Zcols[i][j] = data.getDouble(i, col);
        }

        try {
            return safe01(test.computePValue(e, Zcols));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** e_test ⟂ Z_test on a subset of rows. */
    private double pValueEvsZSubset(double[] eTest, List<Integer> Z, int[] testRows) {
        final int n = eTest.length;
        if (Z == null || Z.isEmpty()) return 1.0;

        double[][] Zcols = new double[n][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = Z.get(j);
            for (int i = 0; i < n; i++) {
                Zcols[i][j] = data.getDouble(testRows[i], col);
            }
        }

        try {
            return safe01(test.computePValue(eTest, Zcols));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---------------------- Utilities ---------------------------
    private static int[] sampleWithoutReplacement(int n, int k, Random rng) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = 0; i < k; i++) {
            int j = i + rng.nextInt(n - i);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        return Arrays.copyOf(idx, k);
    }

    private SimpleMatrix subCov(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
        SimpleMatrix out = new SimpleMatrix(rows.size(), cols.size());
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) {
                out.set(i, j, S.get(rows.get(i), cols.get(j)));
            }
        }
        return out;
    }

    private String names(List<Integer> yj) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < yj.size(); i++) {
            sb.append(nodes.get(yj.get(i)));
            if (i < yj.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    private String clustersAsNames(List<List<Integer>> clusters) {
        return clusters.stream()
                .map(cl -> cl.stream().map(i -> vars.get(i).getName()).toList().toString())
                .collect(Collectors.joining(" | "));
    }

    // ---------------------- Small stats helpers ---------------------
    private static double chisqUpperTail(double x, int df) {
        // very small, robust upper-tail via regularized gamma Q(df/2, x/2)
        return regularizedGammaQ(df / 2.0, x / 2.0);
    }

    private static double regularizedGammaQ(double s, double x) {
        if (x < 0 || s <= 0) return 1.0;
        if (x == 0) return 1.0;
        if (x < s + 1.0) {
            double sum = 1.0 / s;
            double term = sum;
            double ap = s;
            for (int n = 1; n < 200; n++) {
                ap += 1.0;
                term *= x / ap;
                sum += term;
                if (Math.abs(term) < 1e-15) break;
            }
            double gln = logGamma(s);
            double P = sum * Math.exp(-x + s * Math.log(x) - gln);
            return Math.max(0.0, Math.min(1.0, 1.0 - P));
        } else {
            double gln = logGamma(s);
            double a0 = 1.0, a1 = x, b0 = 0.0, b1 = 1.0, fac = 1.0;
            double gOld = 0.0, g = b1;
            for (int n = 1; n < 200; n++) {
                double an = n, ana = an - s;
                a0 = (a1 + a0 * ana) * fac;
                b0 = (b1 + b0 * ana) * fac;
                double anf = an * fac;
                a1 = x * a0 + anf * a1;
                b1 = x * b0 + anf * b1;
                if (a1 != 0) {
                    fac = 1.0 / a1;
                    g = b1 * fac;
                    if (Math.abs((g - gOld) / g) < 1e-12) break;
                    gOld = g;
                }
            }
            double Q = Math.exp(-x + s * Math.log(x) - gln) * g;
            return Math.max(0.0, Math.min(1.0, Q));
        }
    }

    private static double logGamma(double x) {
        double[] c = {
                76.18009172947146, -86.50532032941677,
                24.01409824083091, -1.231739572450155,
                0.001208650973866179, -0.000005395239384953
        };
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double v : c) {
            y += 1.0;
            ser += v / y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    // --------------------------- Helper types -----------------------
    public enum OrderMode { IT, MI } // MI stubbed
    public enum ConsensusMode { FISHER, MEDIAN }

    private static final class ProjResult {
        final double[] e;            // e on provided rows (Yc rows)
        final SimpleMatrix omega;    // |Y| x 1
        final double gapRatio;       // σ_min / σ_next (<=1); +Inf if rank-1
        final boolean acceptable;

        ProjResult(double[] e, SimpleMatrix omega, double gapRatio, boolean acceptable) {
            this.e = e;
            this.omega = omega;
            this.gapRatio = gapRatio;
            this.acceptable = acceptable;
        }
    }

    private static final class ProjSplitResult {
        final double[] eTest;  // e evaluated on TEST rows
        final double gapRatio; // from TRAIN SVD
        final boolean acceptable;

        ProjSplitResult(double[] eTest, double gapRatio, boolean acceptable) {
            this.eTest = eTest;
            this.gapRatio = gapRatio;
            this.acceptable = acceptable;
        }
    }

    private static final class DirResult {
        final double p;
        final boolean gapOk;
        DirResult(double p, boolean gapOk) {
            this.p = p;
            this.gapOk = gapOk;
        }
    }

    private static final class EdgeCand {
        final int i, j;     // direction i -> j
        final double p;     // p(i->j)
        final double pOpp;  // p(j->i) for asymmetry gate
        EdgeCand(int i, int j, double p, double pOpp) {
            this.i = i; this.j = j; this.p = p; this.pOpp = pOpp;
        }
    }
}