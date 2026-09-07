package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.search.MarkovAuditUtils;
import edu.cmu.tetrad.search.PcAR;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Clark's Figure 4, both components in one run, against the current PcAR + seeded-repair stack.
 *
 * Component 1 (triangle with cancelled edge + sink-rescuer structure):
 *   X ~ N(0,1), V ~ N(0,1) exogenous
 *   Y = a*X + eY
 *   Z = b*Y + c*X + d*V + eZ, with c = -a*b EXACTLY (population cancellation of rho_XZ)
 *   True edges: X->Y, Y->Z, X->Z, V->Z.
 *
 * Component 2 (determinism):
 *   A, B ~ N(0,1); C = A + B EXACTLY; D = e*C + eD.
 *   True edges: A->C, B->C, C->D.
 *
 * Runs: PcAR under MARK / RECOVER / RECOVER_CORROBORATED (with the same auditor and estimator
 * wiring the PcAr wrapper uses), with and without a Fisher-Z-style determinism guard, then the
 * PcAR(MARK) -> audit-seeded VertexRepairSearch pipeline. Reports which true edges survive/return.
 */
public final class Figure4Harness {

    static final long SEED = 77L;
    static final int N = 10_000;
    static final double ALPHA = 0.01;

    /**
     * Default constructor for the Figure4Harness class.
     */
    public Figure4Harness() {
    }

    /**
     * Entry point for the program, which generates synthetic data, runs analysis pipelines,
     * and prints relevant results.
     *
     * @param args command-line arguments (not used in this method).
     * @throws Exception if an error occurs during the execution of data processing or analysis methods.
     */
    public static void main(String[] args) throws Exception {
        double a = 0.7, b = 0.7, c = -a * b, d = 0.6, e = 0.7;

        Random rng = new Random(SEED);
        int p = 8; // X, V, Y, Z, A, B, C, D
        double[][] data = new double[p][N];
        int X = 0, V = 1, Y = 2, Z = 3, A = 4, B = 5, C = 6, D = 7;

        for (int i = 0; i < N; i++) {
            double x = rng.nextGaussian(), v = rng.nextGaussian();
            double y = a * x + rng.nextGaussian();
            double z = b * y + c * x + d * v + rng.nextGaussian();
            double aa = rng.nextGaussian(), bb = rng.nextGaussian();
            double cc = aa + bb;                 // exact determinism
            double dd = e * cc + rng.nextGaussian();
            data[X][i] = x; data[V][i] = v; data[Y][i] = y; data[Z][i] = z;
            data[A][i] = aa; data[B][i] = bb; data[C][i] = cc; data[D][i] = dd;
        }

        List<Node> vars = new ArrayList<>();
        for (String name : new String[]{"X", "V", "Y", "Z", "A", "B", "C", "D"}) {
            vars.add(new ContinuousVariable(name));
        }
        DataSet ds = new BoxDataSet(new VerticalDoubleDataBox(data), vars);

        System.out.println("=== Figure 4 harness: n=" + N + ", alpha=" + ALPHA
                + ", a=b=0.7, c=-ab, d=0.6 ===");
        System.out.println("True edges: X->Y, Y->Z, X->Z (cancelled), V->Z | A->C, B->C, C->D (C=A+B)\n");

        // Sample check of the cancellation.
        double rXZ = corr(data[X], data[Z]);
        System.out.printf("sample corr(X,Z) = %.4f (population 0 by construction)%n%n", rXZ);

        for (boolean guard : new boolean[]{false, true}) {
            for (PcAR.RescueAction mode : new PcAR.RescueAction[]{
                    PcAR.RescueAction.MARK, PcAR.RescueAction.RECOVER,
                    PcAR.RescueAction.RECOVER_CORROBORATED}) {
                runPcAR(ds, mode, guard);
            }
        }

        runPipeline(ds);
    }

    static void runPcAR(DataSet ds, PcAR.RescueAction mode, boolean guard) throws Exception {
        IndependenceTest test = new IndTestFisherZ(ds, ALPHA);
        PcAR search = new PcAR(test);
        search.setColliderOrientationStyle(PcAR.ColliderOrientationStyle.CONSERVATIVE);
        search.setFasStable(true);
        search.setRescueAction(mode);
        search.setRecoveryOddsThreshold(1.0);
        search.setMaxRescuePasses(3);
        search.setRecoveryOddsEstimator(new PcAR.DiscriminatingTestOddsEstimator(test));
        final double q = ALPHA;
        final Fas.DeterminismGuard auditGuard = guard ? new CovarianceDeterminismGuard(ds, 1e-6) : null;
        search.setMarkovAuditor((g, t) -> {
            List<PcAR.MarkovAuditFailure> out = new ArrayList<>();
            for (var r : MarkovAuditUtils.auditFailures(g, t, ConditioningSetType.LOCAL_MARKOV, q, false, auditGuard)) {
                out.add(new PcAR.MarkovAuditFailure(
                        r.getFact().getX(), r.getFact().getY(), r.getFact().getZ(), r.getPValue()));
            }
            return out;
        });
        if (guard) {
            search.setDeterminismGuard(new CovarianceDeterminismGuard(ds, 1e-6));
        }

        Graph g = search.search();

        System.out.println("---- PcAR mode=" + mode + " guard=" + (guard ? "ON" : "off") + " ----");
        System.out.println("  edges: " + edgeSummary(g));
        System.out.println("  X-Z present: " + adj(g, "X", "Z")
                + "   C-D present: " + adj(g, "C", "D"));
        System.out.println("  contested: " + search.getContestedDeletions().size()
                + "  (recovered: " + search.getContestedDeletions().stream()
                .filter(PcAR.ContestedDeletion::recovered).count() + ")");
        for (PcAR.ContestedDeletion cd : search.getContestedDeletions()) {
            System.out.println("    contested " + cd.x() + "-" + cd.y() + " pivot " + cd.z()
                    + " locus " + cd.locus() + " recovered=" + cd.recovered());
        }
        System.out.println("  orientation clashes: " + search.getOrientationClashes().size());
        for (PcAR.OrientationClash oc : search.getOrientationClashes()) {
            System.out.println("    clash edge " + oc.u() + "--" + oc.z()
                    + " witnesses " + oc.witnesses());
        }
        System.out.println("  audit failures (BH): " + search.getMarkovAuditFailures().size());
        for (PcAR.MarkovAuditFailure maf : search.getMarkovAuditFailures()) {
            System.out.println("    audit " + maf.x() + " _||_ " + maf.y()
                    + " | " + maf.conditioningSet() + " p=" + maf.pValue());
        }
        System.out.println("  blocked deletions: " + search.getBlockedDeletions().size());
        for (Fas.BlockedDeletion bd : search.getBlockedDeletions()) {
            System.out.println("    blocked " + bd.x() + "-" + bd.y() + " sepset " + bd.sepset());
        }
        System.out.println();
    }

    static void runPipeline(DataSet ds) throws Exception {
        System.out.println("---- Pipeline: PcAR(MARK, guard ON) -> audit-seeded VertexRepair ----");
        IndependenceTest test = new IndTestFisherZ(ds, ALPHA);
        PcAR search = new PcAR(test);
        search.setColliderOrientationStyle(PcAR.ColliderOrientationStyle.CONSERVATIVE);
        search.setFasStable(true);
        search.setRescueAction(PcAR.RescueAction.MARK);
        search.setDeterminismGuard(new CovarianceDeterminismGuard(ds, 1e-6));
        Graph g = search.search();
        System.out.println("  PcAR graph: " + edgeSummary(g));

        Set<Node> seeds = MarkovAuditUtils.implicatedVertices(MarkovAuditUtils.auditFailures(
                g, test, ConditioningSetType.LOCAL_MARKOV, ALPHA, false,
                new CovarianceDeterminismGuard(ds, 1e-6)));
        // Include blocked-deletion endpoints, matching the wrapper's narrow seed set.
        for (Fas.BlockedDeletion bd : search.getBlockedDeletions()) {
            seeds.add(bd.x());
            seeds.add(bd.y());
        }
        System.out.println("  seeds: " + seeds);

        VertexRepairSearch repair = new VertexRepairSearch(g, test, ConditioningSetType.LOCAL_MARKOV);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(1L);
        if (!seeds.isEmpty()) repair.setSeedVertices(seeds);
        repair.addRepairListener(new VertexRepairSearch.RepairListener() {
            @Override
            public void statusUpdated(String message) {
                System.out.println("    [repair] " + message);
            }
        });
        Graph repaired = repair.search();

        System.out.println("  repaired graph: " + edgeSummary(repaired));
        System.out.println("  X-Z present: " + adj(repaired, "X", "Z")
                + "   C-D present: " + adj(repaired, "C", "D"));
    }

    // ---- helpers ----

    static boolean adj(Graph g, String a, String b) {
        Node na = g.getNode(a), nb = g.getNode(b);
        return na != null && nb != null && g.isAdjacentTo(na, nb);
    }

    static String edgeSummary(Graph g) {
        List<String> es = new ArrayList<>();
        g.getEdges().forEach(ed -> es.add(ed.toString()));
        es.sort(String::compareTo);
        return es.toString();
    }

    static double corr(double[] u, double[] v) {
        int n = u.length;
        double mu = 0, mv = 0;
        for (int i = 0; i < n; i++) { mu += u[i]; mv += v[i]; }
        mu /= n; mv /= n;
        double suv = 0, suu = 0, svv = 0;
        for (int i = 0; i < n; i++) {
            suv += (u[i] - mu) * (v[i] - mv);
            suu += (u[i] - mu) * (u[i] - mu);
            svv += (v[i] - mv) * (v[i] - mv);
        }
        return suv / Math.sqrt(suu * svv);
    }

    /**
     * Var(v|S)=0 check from the sample covariance: residual variance of v on S below tol, i.e.
     * 1 - R^2 below tol relative to Var(v). Population-exact determinism at n=10000 lands at
     * numerical zero, so tol=1e-6 is generous without catching ordinary strong regressions.
     */
    static final class CovarianceDeterminismGuard implements Fas.DeterminismGuard {
        private final double[][] cov;
        private final List<String> names;
        private final double tol;

        CovarianceDeterminismGuard(DataSet ds, double tol) {
            this.tol = tol;
            int p = ds.getNumColumns(), n = ds.getNumRows();
            this.names = new ArrayList<>();
            for (Node v : ds.getVariables()) names.add(v.getName());
            double[] mean = new double[p];
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) mean[j] += ds.getDouble(i, j);
                mean[j] /= n;
            }
            this.cov = new double[p][p];
            for (int j = 0; j < p; j++) {
                for (int k = j; k < p; k++) {
                    double s = 0;
                    for (int i = 0; i < n; i++) {
                        s += (ds.getDouble(i, j) - mean[j]) * (ds.getDouble(i, k) - mean[k]);
                    }
                    cov[j][k] = cov[k][j] = s / (n - 1);
                }
            }
        }

        @Override
        public boolean determines(Node v, Set<Node> S) {
            if (S.isEmpty()) return false;
            int iv = names.indexOf(v.getName());
            int[] is = S.stream().mapToInt(s -> names.indexOf(s.getName())).toArray();
            for (int i : is) if (i < 0) return false;
            if (iv < 0) return false;

            int k = is.length;
            double[][] sss = new double[k][k];
            double[] ssv = new double[k];
            for (int i = 0; i < k; i++) {
                ssv[i] = cov[is[i]][iv];
                for (int j = 0; j < k; j++) sss[i][j] = cov[is[i]][is[j]];
            }
            // Solve sss * w = ssv by Gaussian elimination with partial pivoting; on a singular
            // conditioning set (itself degenerate), report determined -- the test is inadmissible
            // either way.
            double[] w = solve(sss, ssv);
            if (w == null) return true;
            double explained = 0;
            for (int i = 0; i < k; i++) explained += w[i] * ssv[i];
            double resid = cov[iv][iv] - explained;
            return resid <= tol * Math.max(1.0, cov[iv][iv]);
        }

        private static double[] solve(double[][] m, double[] b) {
            int k = b.length;
            double[][] a = new double[k][k + 1];
            for (int i = 0; i < k; i++) {
                System.arraycopy(m[i], 0, a[i], 0, k);
                a[i][k] = b[i];
            }
            for (int col = 0; col < k; col++) {
                int piv = col;
                for (int r = col + 1; r < k; r++) {
                    if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
                }
                if (Math.abs(a[piv][col]) < 1e-12) return null;
                double[] tmp = a[col]; a[col] = a[piv]; a[piv] = tmp;
                for (int r = 0; r < k; r++) {
                    if (r == col) continue;
                    double f = a[r][col] / a[col][col];
                    for (int cc = col; cc <= k; cc++) a[r][cc] -= f * a[col][cc];
                }
            }
            double[] x = new double[k];
            for (int i = 0; i < k; i++) x[i] = a[i][k] / a[i][i];
            return x;
        }
    }
}

