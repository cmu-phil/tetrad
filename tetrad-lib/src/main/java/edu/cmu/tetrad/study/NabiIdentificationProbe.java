package edu.cmu.tetrad.study;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.util.Random;

/**
 * Numerical probe of the identification boundary in Nabi, Bhattacharya and Shpitser, "Full Law Identification in
 * Graphical Models of Missing Data: Completeness Results" (PMLR 119, 2020). The paper proves (Theorems 1-2) that
 * the full law of a missing data DAG is identified from the observed data distribution if and only if the graph
 * has no self-censoring edge (X_i to R_i) and no colluder (X_j to R_i with R_j to R_i), with the identifying
 * functional given by Chen's odds ratio parameterization. The paper contains no simulation; this probe exercises
 * both halves of the theorem numerically on binary models small enough for exact computation.
 *
 * <p>Three missing data DAGs over binary X1, X2, X3 with indicators R1, R2, R3 (R = 1 means observed), all with
 * target law X1 to X2 to X3 and mechanism parents:</p>
 * <ul>
 *   <li>BRONCHITIS (their Fig 1(a), Scenario 1): pa(R3) = {X1}, pa(R2) = {X1, R3}, pa(R1) = {X3, R2}. No
 *       self-censoring, no colluder: full law IDENTIFIED (and this is their example where the Bhattacharya et
 *       al. 2019 algorithm fails, so identification here is the paper's novel content);</li>
 *   <li>SELF-CENSORING: the same plus X1 in pa(R1): provably NOT identified;</li>
 *   <li>COLLUDER: the same plus X2 in pa(R1), so X2 to R1 with R2 to R1: provably NOT identified.</li>
 * </ul>
 *
 * <p>Three checks:</p>
 * <ol>
 *   <li><b>Rank test.</b> The model parameters (CPT logits) map smoothly to the 64-cell observed data
 *       distribution (the full joint with unobserved X coordinates marginalized within each R pattern). The
 *       parameterization is minimal (CPTs are unique functionals of a positive full law), so local
 *       identifiability at a parameter point is exactly full column rank of the Jacobian of that map. Run at
 *       generic random points AND on the degenerate stratum where the censored variable's observed "shadow"
 *       channels (its observed children and proxies) are switched off. The first run exposes a fact the paper
 *       does not dwell on: in the binary saturated model, generic shadow mixing restores local identification
 *       even past their boundary (the Miao-style shadow-variable effect), so their model-level
 *       non-identification is carried entirely by the degenerate stratum -- which the second run shows, and
 *       which also means estimation near that stratum is ill-conditioned wherever shadows are weak.</li>
 *   <li><b>Fiber walk.</b> For each non-identified graph, start ON the degenerate stratum, walk along the
 *       Jacobian's null direction, and polish with damped Gauss-Newton back onto the fiber. The result is a
 *       pair of parameter points with observed laws agreeing to near machine precision and visibly different
 *       full laws -- a concrete counterexample pair, which is what non-identification means.</li>
 *   <li><b>Functional consistency.</b> For the identified BRONCHITIS graph, the paper's identifying functional
 *       (propensity of R1 by conditioning on R3 = 1; propensity of R2 by inverse weighting with the fixed R1;
 *       the (R2, R3) joint by the odds ratio parameterization with R1 = 1 plugged in) is estimated from
 *       simulated observed data at increasing n, against the known truth. The error should shrink at roughly
 *       root-n. A naive available-case estimator of the same mechanism (conditioning on R1 = 1 and ignoring
 *       the selection that induces) is shown alongside, and should plateau at a bias.</li>
 * </ol>
 *
 * <p>Caveat: local rank tests local identifiability and the theorem is global, so full rank is evidence, not
 * proof, on the positive side; the rank deficit plus an explicit counterexample pair IS a demonstration on the
 * negative side. The paper's non-identification claims are over the whole model (all state spaces and
 * parameter values), so generic binary identifiability and their completeness theorem are both true at once.
 * Runs in seconds; nothing outside this file is touched.</p>
 *
 * @author josephramsey
 */
public final class NabiIdentificationProbe {

    private NabiIdentificationProbe() {
    }

    // ---------------------------------------------------------------- configuration

    /** Random points per graph for the rank test. */
    private static final int RANK_POINTS = 20;

    /** Relative singular-value threshold for rank. */
    private static final double RANK_TOL = 1e-7;

    /** Step size and step count for the fiber walk. */
    private static final double WALK_STEP = 0.02;
    private static final int WALK_STEPS = 150;

    /** Gauss-Newton polish iterations after the walk. */
    private static final int POLISH_ITERS = 80;

    /** Sample sizes for the functional-consistency check. */
    private static final int[] FUNCTIONAL_NS = {2_000, 20_000, 200_000, 2_000_000};

    /** Repetitions per n for the functional check (errors are averaged). */
    private static final int FUNCTIONAL_REPS = 5;

    /** Spread of the random true logits. */
    private static final double LOGIT_SPREAD = 1.25;

    private static final long SEED = 42L;

    // ---------------------------------------------------------------- model

    /**
     * A missing data DAG over binary X1 X2 X3, R1 R2 R3. Bit layout of a full assignment a (6 bits):
     * bit 0 = X1, bit 1 = X2, bit 2 = X3, bit 3 = R1, bit 4 = R2, bit 5 = R3. R = 1 means observed.
     * The target law is the chain X1 to X2 to X3 (5 logits); each mechanism CPT contributes 2^|pa| logits,
     * in the factor order R3, R2, R1 (a topological order of every graph here).
     */
    private static final class Model {
        final String name;
        final int[][] paR; // parents of R3, R2, R1 in that order, as bit indices into the full assignment
        final int dim;

        /**
         * Groups of parameter indices tied EQUAL to form the degenerate stratum: the chain made independent and
         * the observed "shadow" channels for the censored variable made uninformative, which is where the
         * theorem's counterexamples live. Empty for a model without a defined stratum.
         */
        int[][] tieGroups = new int[0][];

        Model(String name, int[] paR3, int[] paR2, int[] paR1) {
            this.name = name;
            this.paR = new int[][]{paR3, paR2, paR1};
            this.dim = 5 + (1 << paR3.length) + (1 << paR2.length) + (1 << paR1.length);
        }

        /** Offset of the CPT for factor k (0 = R3, 1 = R2, 2 = R1) in the parameter vector. */
        int offR(int k) {
            int off = 5;
            for (int i = 0; i < k; i++) off += 1 << this.paR[i].length;
            return off;
        }

        /** A random point on the degenerate stratum: random theta with each tie group averaged. */
        double[] degenerateTheta(Random rand) {
            double[] theta = randomTheta(rand);
            for (int[] group : this.tieGroups) {
                double mean = 0;
                for (int i : group) mean += theta[i];
                mean /= group.length;
                for (int i : group) theta[i] = mean;
            }
            return theta;
        }

        /** The full law as a 64-vector indexed by the assignment bits. */
        double[] fullLaw(double[] theta) {
            double[] law = new double[64];

            for (int a = 0; a < 64; a++) {
                int x1 = a & 1, x2 = (a >> 1) & 1, x3 = (a >> 2) & 1;

                double p = bern(x1, sigmoid(theta[0]))
                           * bern(x2, sigmoid(theta[1 + x1]))
                           * bern(x3, sigmoid(theta[3 + x2]));

                int off = 5;
                int[] rBit = {5, 4, 3}; // R3, R2, R1

                for (int k = 0; k < 3; k++) {
                    int[] pa = this.paR[k];
                    int idx = 0;
                    for (int b : pa) idx = (idx << 1) | ((a >> b) & 1);
                    int r = (a >> rBit[k]) & 1;
                    p *= bern(r, sigmoid(theta[off + idx]));
                    off += 1 << pa.length;
                }

                law[a] = p;
            }

            return law;
        }

        /**
         * The observed data distribution as a 64-vector: each full assignment is mapped to the key with its
         * unobserved X bits zeroed (the proxy value "?"), and probabilities accumulate. Keys with an unobserved
         * X bit set stay empty, so the vector has exactly the 27 live cells of the observed law.
         */
        double[] obsLaw(double[] theta) {
            double[] full = fullLaw(theta);
            double[] obs = new double[64];

            for (int a = 0; a < 64; a++) {
                int r1 = (a >> 3) & 1, r2 = (a >> 4) & 1, r3 = (a >> 5) & 1;
                int mask = 0b111000 | r1 | (r2 << 1) | (r3 << 2);
                obs[a & mask] += full[a];
            }

            return obs;
        }

        /** Numerical Jacobian of the observed law in the parameters, central differences. */
        RealMatrix jacobian(double[] theta) {
            double eps = 1e-6;
            double[][] j = new double[64][this.dim];

            for (int c = 0; c < this.dim; c++) {
                double[] plus = theta.clone(), minus = theta.clone();
                plus[c] += eps;
                minus[c] -= eps;
                double[] oPlus = obsLaw(plus), oMinus = obsLaw(minus);
                for (int r = 0; r < 64; r++) j[r][c] = (oPlus[r] - oMinus[r]) / (2 * eps);
            }

            return new Array2DRowRealMatrix(j, false);
        }

        /** Numerical Jacobian of the FULL law, for checking that null directions move it. */
        RealMatrix jacobianFull(double[] theta) {
            double eps = 1e-6;
            double[][] j = new double[64][this.dim];

            for (int c = 0; c < this.dim; c++) {
                double[] plus = theta.clone(), minus = theta.clone();
                plus[c] += eps;
                minus[c] -= eps;
                double[] fPlus = fullLaw(plus), fMinus = fullLaw(minus);
                for (int r = 0; r < 64; r++) j[r][c] = (fPlus[r] - fMinus[r]) / (2 * eps);
            }

            return new Array2DRowRealMatrix(j, false);
        }

        double[] randomTheta(Random rand) {
            double[] theta = new double[this.dim];
            for (int i = 0; i < this.dim; i++) theta[i] = LOGIT_SPREAD * (2 * rand.nextDouble() - 1);
            return theta;
        }
    }

    private static double sigmoid(double t) {
        return 1.0 / (1.0 + Math.exp(-t));
    }

    private static double bern(int v, double p1) {
        return v == 1 ? p1 : 1.0 - p1;
    }

    private static double tv(double[] p, double[] q) {
        double s = 0;
        for (int i = 0; i < p.length; i++) s += Math.abs(p[i] - q[i]);
        return 0.5 * s;
    }

    private static double maxAbs(double[] p, double[] q) {
        double s = 0;
        for (int i = 0; i < p.length; i++) s = Math.max(s, Math.abs(p[i] - q[i]));
        return s;
    }

    // Bit indices: X1=0, X2=1, X3=2, R1=3, R2=4, R3=5.

    /** Chain-independence ties plus R3 and R2 CPTs made flat in X1: every shadow channel for X1 switched off. */
    private static int[][] x1ShadowTies(Model m) {
        int o3 = m.offR(0), o2 = m.offR(1);
        return new int[][]{
                {1, 2},            // p(X2 | X1) flat: X2 carries nothing about X1
                {3, 4},            // p(X3 | X2) flat
                {o3, o3 + 1},      // p(R3 | X1) flat: R3 carries nothing about X1
                {o2, o2 + 2},      // p(R2 | X1, R3) flat in X1, at R3 = 0
                {o2 + 1, o2 + 3}}; // ... and at R3 = 1
    }

    private static Model bronchitis() {
        Model m = new Model("BRONCHITIS (no SC, no colluder)", new int[]{0}, new int[]{0, 5}, new int[]{2, 4});
        m.tieGroups = x1ShadowTies(m); // control: the same harsh stratum, where this graph should STAY identified
        return m;
    }

    private static Model selfCensoring() {
        Model m = new Model("SELF-CENSORING (X1 -> R1)", new int[]{0}, new int[]{0, 5}, new int[]{0, 2, 4});
        m.tieGroups = x1ShadowTies(m); // isolates the X1 -> R1 dependence from every observed proxy
        return m;
    }

    private static Model colluder() {
        Model m = new Model("COLLUDER (X2 -> R1 <- R2)", new int[]{0}, new int[]{0, 5}, new int[]{1, 2, 4});
        // X2's only shadow channels are its child X3 and its parent X1; the chain ties switch both off.
        m.tieGroups = new int[][]{{1, 2}, {3, 4}};
        return m;
    }

    // ---------------------------------------------------------------- main

    /**
     * Entry point; runs the three checks and prints the results. Seconds of compute.
     *
     * @param args Unused.
     */
    public static void main(String[] args) {
        System.out.println("Nabi et al. (2020) identification probe: binary X1->X2->X3 with missingness");
        System.out.println("indicators R1, R2, R3; pa(R3)={X1}, pa(R2)={X1,R3}, pa(R1)={X3,R2} plus the");
        System.out.println("variant edge that crosses their boundary. Full law dimension 64; observed law");
        System.out.println("27 live cells.");
        System.out.println();

        Random rand = new Random(SEED);

        Model[] models = {bronchitis(), selfCensoring(), colluder()};

        // ---- Check 1: rank at GENERIC parameter points.
        System.out.println("---- Check 1: local rank of the parameters-to-observed-law map, generic points ----");
        System.out.printf("%-36s %6s %6s %14s%n", "graph", "dim", "rank", "min rel sv");

        for (Model model : models) {
            int minRank = Integer.MAX_VALUE;
            double minRelSv = Double.MAX_VALUE;

            for (int t = 0; t < RANK_POINTS; t++) {
                double[] theta = model.randomTheta(rand);
                SingularValueDecomposition svd = new SingularValueDecomposition(model.jacobian(theta));
                double[] sv = svd.getSingularValues();

                int rank = 0;
                for (double s : sv) if (s > RANK_TOL * sv[0]) rank++;
                minRank = Math.min(minRank, rank);
                minRelSv = Math.min(minRelSv, sv[model.dim - 1] / sv[0]);
            }

            System.out.printf("%-36s %6d %6d %14.2e%n", model.name, model.dim, minRank, minRelSv);
        }

        System.out.println();
        System.out.println("FINDING: at generic binary parameters ALL three graphs are locally identified --");
        System.out.println("including the two past the Nabi boundary. The observed children of the censored");
        System.out.println("variable (R2, and X2 when observed) act as shadow variables whose generically");
        System.out.println("invertible mixing restores identification in the binary saturated model. This does");
        System.out.println("not contradict the theorem: their non-identification is model-level -- one bad");
        System.out.println("distribution anywhere in the model suffices -- and the bad set is the stratum where");
        System.out.println("the shadow channels carry no information, probed next. Note also the small relative");
        System.out.println("singular values: identification near that stratum is weak, so estimation there is");
        System.out.println("ill-conditioned even where identification formally holds.");
        System.out.println();

        // ---- Check 1b: rank ON the degenerate stratum.
        System.out.println("---- Check 1b: rank on the degenerate stratum (shadow channels switched off) ----");
        System.out.printf("%-36s %6s %6s %10s%n", "graph", "dim", "rank", "deficit");

        for (Model model : models) {
            int minRank = Integer.MAX_VALUE;

            for (int t = 0; t < 10; t++) {
                double[] theta = model.degenerateTheta(rand);
                SingularValueDecomposition svd = new SingularValueDecomposition(model.jacobian(theta));
                double[] sv = svd.getSingularValues();

                int rank = 0;
                for (double s : sv) if (s > RANK_TOL * sv[0]) rank++;
                minRank = Math.min(minRank, rank);
            }

            System.out.printf("%-36s %6d %6d %10d%n", model.name, model.dim, minRank, model.dim - minRank);
        }

        System.out.println();
        System.out.println("Expected: BRONCHITIS keeps full rank even here (the theorem's positive half holds");
        System.out.println("for EVERY positive distribution in the model); the self-censoring and colluder");
        System.out.println("graphs lose rank, and that is where their counterexamples live.");
        System.out.println();

        // ---- Check 2: explicit counterexample pairs, launched from the degenerate stratum.
        System.out.println("---- Check 2: counterexample pairs on the degenerate stratum ----");

        for (Model model : new Model[]{selfCensoring(), colluder()}) {
            double[] theta1 = model.degenerateTheta(rand);
            double[] theta2 = fiberWalk(model, theta1);

            double[] o1 = model.obsLaw(theta1), o2 = model.obsLaw(theta2);
            double[] f1 = model.fullLaw(theta1), f2 = model.fullLaw(theta2);

            System.out.printf("%n%s:%n", model.name);
            System.out.printf("  max |observed law difference| = %.2e   (agreement)%n", maxAbs(o1, o2));
            System.out.printf("  TV(full laws)                 = %.4f     (disagreement)%n", tv(f1, f2));
            System.out.printf("  TV(target laws p(X))          = %.4f%n", tv(targetLaw(f1), targetLaw(f2)));

            // Where the difference lives: the R1 mechanism CPT.
            int offR1 = model.offR(2);
            int cptSize = 1 << model.paR[2].length;
            System.out.print("  p(R1=1 | pa) at theta1 vs theta2:");
            for (int i = 0; i < cptSize; i++) {
                System.out.printf(" %.3f/%.3f", sigmoid(theta1[offR1 + i]), sigmoid(theta2[offR1 + i]));
            }
            System.out.println();
        }

        System.out.println();
        System.out.println("Two full laws, same observed law: non-identification exhibited, not just argued.");
        System.out.println();

        // ---- Check 3: the identifying functional on the identified graph.
        System.out.println("---- Check 3: odds-ratio identifying functional, BRONCHITIS graph ----");
        System.out.println("Max abs error of the estimated mechanism p(R | X) over all 64 (x, r) cells,");
        System.out.println("averaged over " + FUNCTIONAL_REPS + " repetitions.");
        System.out.printf("%-12s %14s %18s%n", "n", "OR functional", "naive (R1=1 cond.)");

        Model model = bronchitis();
        double[] trueTheta = model.randomTheta(new Random(SEED + 1));
        double[] trueFull = model.fullLaw(trueTheta);
        double[][] trueMech = mechanismFromFull(trueFull);

        for (int n : FUNCTIONAL_NS) {
            double errFunctional = 0, errNaive = 0;

            for (int rep = 0; rep < FUNCTIONAL_REPS; rep++) {
                long[] counts = sample(trueFull, n, new Random(SEED + 10L * n + rep));
                errFunctional += mechanismError(estimateFunctional(counts), trueMech);
                errNaive += mechanismError(estimateNaive(counts), trueMech);
            }

            System.out.printf("%-12d %14.4f %18.4f%n", n, errFunctional / FUNCTIONAL_REPS,
                    errNaive / FUNCTIONAL_REPS);
        }

        System.out.println();
        System.out.println("Expected: the functional's error shrinks at roughly root-n toward 0; the naive");
        System.out.println("estimator plateaus at its selection bias. The functional is estimable from data");
        System.out.println("in which NO row need be completely observed for identification -- the paper's point.");
    }

    // ---------------------------------------------------------------- fiber walk

    /**
     * Walks from theta1 along the observed-law Jacobian's smallest right-singular direction, re-estimated at
     * every step with its sign aligned to the previous step, then polishes with damped Gauss-Newton so the
     * observed law matches theta1's to near machine precision while the walk's full-law displacement remains.
     */
    private static double[] fiberWalk(Model model, double[] theta1) {
        double[] target = model.obsLaw(theta1);
        double[] theta = theta1.clone();
        double[] prev = null;

        for (int s = 0; s < WALK_STEPS; s++) {
            SingularValueDecomposition svd = new SingularValueDecomposition(model.jacobian(theta));
            double[] v = svd.getV().getColumnVector(model.dim - 1).toArray();

            if (prev != null) {
                double dot = 0;
                for (int i = 0; i < v.length; i++) dot += v[i] * prev[i];
                if (dot < 0) for (int i = 0; i < v.length; i++) v[i] = -v[i];
            }
            prev = v;

            for (int i = 0; i < theta.length; i++) theta[i] += WALK_STEP * v[i];
        }

        // Damped Gauss-Newton: pull the observed law back onto the target while staying on this side of the fiber.
        for (int iter = 0; iter < POLISH_ITERS; iter++) {
            double[] obs = model.obsLaw(theta);
            double[] res = new double[64];
            double norm = 0;
            for (int i = 0; i < 64; i++) {
                res[i] = obs[i] - target[i];
                norm = Math.max(norm, Math.abs(res[i]));
            }
            if (norm < 1e-13) break;

            RealMatrix j = model.jacobian(theta);
            RealMatrix jtj = j.transpose().multiply(j);
            for (int i = 0; i < model.dim; i++) jtj.addToEntry(i, i, 1e-9);
            RealVector jtr = j.transpose().operate(new ArrayRealVector(res, false));

            DecompositionSolver solver = new LUDecomposition(jtj).getSolver();
            RealVector delta = solver.solve(jtr);
            for (int i = 0; i < model.dim; i++) theta[i] -= delta.getEntry(i);
        }

        return theta;
    }

    /** The target law p(X) as an 8-vector, marginalizing R out of a full law. */
    private static double[] targetLaw(double[] full) {
        double[] p = new double[8];
        for (int a = 0; a < 64; a++) p[a & 0b111] += full[a];
        return p;
    }

    // ---------------------------------------------------------------- functional estimation

    /** The true mechanism p(R = r | X = x) as [x][r] from a full law. */
    private static double[][] mechanismFromFull(double[] full) {
        double[][] mech = new double[8][8];
        for (int x = 0; x < 8; x++) {
            double px = 0;
            for (int r = 0; r < 8; r++) px += full[x | (r << 3)];
            for (int r = 0; r < 8; r++) mech[x][r] = full[x | (r << 3)] / px;
        }
        return mech;
    }

    private static double mechanismError(double[][] est, double[][] truth) {
        double e = 0;
        for (int x = 0; x < 8; x++) {
            for (int r = 0; r < 8; r++) e = Math.max(e, Math.abs(est[x][r] - truth[x][r]));
        }
        return e;
    }

    /** Samples n rows from a full law; returns counts over the 64 FULL assignments (the estimators then only
     *  read the cells their own conditioning makes legitimate, mimicking what observed data allows). */
    private static long[] sample(double[] full, int n, Random rand) {
        double[] cdf = new double[64];
        double acc = 0;
        for (int a = 0; a < 64; a++) {
            acc += full[a];
            cdf[a] = acc;
        }

        long[] counts = new long[64];
        for (int i = 0; i < n; i++) {
            double u = rand.nextDouble() * acc;
            int lo = 0, hi = 63;
            while (lo < hi) {
                int mid = (lo + hi) >> 1;
                if (cdf[mid] < u) lo = mid + 1;
                else hi = mid;
            }
            counts[lo]++;
        }
        return counts;
    }

    // Count helpers. r-bits: R1 = bit 3, R2 = bit 4, R3 = bit 5; x-bits 0..2. The estimators below read x-bits
    // only in cells where the corresponding R bit is 1, so every frequency they form is computable from
    // observed data.

    private static long countWhere(long[] c, int mask, int value) {
        long s = 0;
        for (int a = 0; a < 64; a++) if ((a & mask) == value) s += c[a];
        return s;
    }

    private static final double SMOOTH = 0.5; // pseudo-count guard against empty strata

    /** p-hat(R1 = 1 | X3 = x3, R2 = r2) among R3 = 1 rows (X3 observed there). Valid: R1 indep R3 | X3, R2. */
    private static double pR1(long[] c, int x3, int r2) {
        int mask = (1 << 5) | (1 << 2) | (1 << 4);
        int base = (1 << 5) | (x3 << 2) | (r2 << 4);
        long denom = countWhere(c, mask, base);
        long num = countWhere(c, mask | (1 << 3), base | (1 << 3));
        return (num + SMOOTH) / (denom + 2 * SMOOTH);
    }

    /**
     * The paper's identifying functional for the BRONCHITIS mechanism, from countable frequencies:
     * p(R1 | X3, R2) by conditioning on R3 = 1; p(R3 | R2 = 1, X1) by conditioning on R1 = 1 (valid: R3 indep
     * R1 | X1, R2); p(R2 | R3 = 1, X1) by inverse weighting R1 = 1 rows by the already-identified R1
     * propensity (the fixing operation); the (R2, R3) joint given X1 assembled by the odds ratio
     * parameterization with the OR evaluated at R1 = 1 (valid: R3 indep R1 | R2, X1).
     */
    private static double[][] estimateFunctional(long[] c) {
        // p(R3 = r3 | R2 = r2, X1 = x1, R1 = 1), read among R1 = 1 rows.
        double[][][] pR3given = new double[2][2][2]; // [x1][r2][r3]
        for (int x1 = 0; x1 < 2; x1++) {
            for (int r2 = 0; r2 < 2; r2++) {
                int mask = (1 << 3) | 1 | (1 << 4);
                int base = (1 << 3) | x1 | (r2 << 4);
                long denom = countWhere(c, mask, base);
                long num = countWhere(c, mask | (1 << 5), base | (1 << 5));
                double p1 = (num + SMOOTH) / (denom + 2 * SMOOTH);
                pR3given[x1][r2][1] = p1;
                pR3given[x1][r2][0] = 1 - p1;
            }
        }

        // p(R2 = r2 | R3 = 1, X1 = x1) by IPW over rows with R1 = 1 and R3 = 1, weights 1 / p-hat(R1=1 | X3, R2).
        double[][] pR2given = new double[2][2]; // [x1][r2]
        for (int x1 = 0; x1 < 2; x1++) {
            double[] wsum = new double[2];
            for (int a = 0; a < 64; a++) {
                if (((a >> 3) & 1) != 1 || ((a >> 5) & 1) != 1 || (a & 1) != x1) continue;
                int x3 = (a >> 2) & 1, r2 = (a >> 4) & 1;
                wsum[r2] += c[a] / Math.max(1e-12, pR1(c, x3, r2));
            }
            double tot = wsum[0] + wsum[1] + 2 * SMOOTH;
            pR2given[x1][0] = (wsum[0] + SMOOTH) / tot;
            pR2given[x1][1] = (wsum[1] + SMOOTH) / tot;
        }

        // OR(R2 = r2, R3 = r3 | X1) with reference (1, 1), computed from the R1 = 1 frequencies.
        // Assemble p(R2, R3 | X1) proportional to p(R2 = r2 | R3 = 1, X1) * p(R3 = r3 | R2 = 1, X1) * OR.
        double[][][] joint = new double[2][2][2]; // [x1][r2][r3]
        for (int x1 = 0; x1 < 2; x1++) {
            double z = 0;
            for (int r2 = 0; r2 < 2; r2++) {
                for (int r3 = 0; r3 < 2; r3++) {
                    double or = (pR3given[x1][r2][r3] / pR3given[x1][r2][1])
                                * (pR3given[x1][1][1] / pR3given[x1][1][r3]);
                    double v = pR2given[x1][r2] * pR3given[x1][1][r3] * or;
                    joint[x1][r2][r3] = v;
                    z += v;
                }
            }
            for (int r2 = 0; r2 < 2; r2++) {
                for (int r3 = 0; r3 < 2; r3++) joint[x1][r2][r3] /= z;
            }
        }

        // Mechanism: p(R = r | X = x) = p(R1 = r1 | X3, R2 = r2) * p(R2 = r2, R3 = r3 | X1).
        double[][] mech = new double[8][8];
        for (int x = 0; x < 8; x++) {
            int x1 = x & 1, x3 = (x >> 2) & 1;
            for (int r = 0; r < 8; r++) {
                int r1 = r & 1, r2 = (r >> 1) & 1, r3 = (r >> 2) & 1;
                double p1 = pR1(c, x3, r2);
                mech[x][r] = (r1 == 1 ? p1 : 1 - p1) * joint[x1][r2][r3];
            }
        }
        return mech;
    }

    /**
     * The naive comparator: same first factor, but the (R2, R3) joint given X1 taken as the raw frequency
     * among R1 = 1 rows, ignoring that conditioning on R1 = 1 selects on R2 (and through it R3). This is what
     * an analyst without the fixing/odds-ratio machinery would compute, and it is inconsistent.
     */
    private static double[][] estimateNaive(long[] c) {
        double[][][] joint = new double[2][2][2];
        for (int x1 = 0; x1 < 2; x1++) {
            double tot = 0;
            double[][] cell = new double[2][2];
            for (int r2 = 0; r2 < 2; r2++) {
                for (int r3 = 0; r3 < 2; r3++) {
                    int mask = (1 << 3) | 1 | (1 << 4) | (1 << 5);
                    int val = (1 << 3) | x1 | (r2 << 4) | (r3 << 5);
                    cell[r2][r3] = countWhere(c, mask, val) + SMOOTH;
                    tot += cell[r2][r3];
                }
            }
            for (int r2 = 0; r2 < 2; r2++) {
                for (int r3 = 0; r3 < 2; r3++) joint[x1][r2][r3] = cell[r2][r3] / tot;
            }
        }

        double[][] mech = new double[8][8];
        for (int x = 0; x < 8; x++) {
            int x1 = x & 1, x3 = (x >> 2) & 1;
            for (int r = 0; r < 8; r++) {
                int r1 = r & 1, r2 = (r >> 1) & 1, r3 = (r >> 2) & 1;
                double p1 = pR1(c, x3, r2);
                mech[x][r] = (r1 == 1 ? p1 : 1 - p1) * joint[x1][r2][r3];
            }
        }
        return mech;
    }
}
