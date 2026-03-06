package edu.cmu.tetrad.search.test;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

/**
 * Utilities for converting a quadratic-form statistic
 *
 *     Q = sum_i lambda_i * Z_i^2,   Z_i ~ N(0,1),
 *
 * into a p-value using fast analytic transforms that use the full eigen-spectrum.
 *
 * Intended use (FF-CI / RCIT):
 *   - compute stat = n * ||Cxy||_F^2
 *   - compute eig = positive eigenvalues of Cov( vec(RX ⊗ RY) )
 *   - call one of the transforms below: gamma / saddlepoint-LR / davies (optional)
 *
 * Notes:
 * - "Saddlepoint (Lugannani–Rice)" tends to be dramatically more reliable than
 *   HBE/LPB4 while staying very fast.
 * - Davies is more "near-exact" but more code (optionally add later).
 */
public final class QuadraticFormPValues {

    private QuadraticFormPValues() {}

    // ------------------------------ public API ------------------------------

    /**
     * Edgeworth / Cornish–Fisher tail.
     * useKurtosis = false → HBE
     * useKurtosis = true  → LPB4
     *
     * @param stat the test statistic
     * @param eig eigenvalues
     * @param useKurtosis whether to use kurtosis
     * @return the p-value
     */
    public static double edgeworthP(double stat, double[] eig, boolean useKurtosis) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double s1 = 0, s2 = 0, s3 = 0, s4 = 0;
        for (double l : eig) {
            s1 += l;
            s2 += l * l;
            s3 += l * l * l;
            s4 += l * l * l * l;
        }

        double mu = s1;
        double var = 2.0 * s2;
        if (var <= 0) return 1.0;

        double sigma = FastMath.sqrt(var);
        double t = (stat - mu) / sigma;

        double gamma1 = (8.0 * s3) / FastMath.pow(var, 1.5);
        double gamma2 = (48.0 * s4) / (var * var);

        double z = t + (gamma1 / 6.0) * (t * t - 1.0);
        if (useKurtosis) {
            z += (gamma2 / 24.0) * (t * t * t - 3.0 * t)
                    - (gamma1 * gamma1 / 36.0) * (2.0 * t * t * t - 5.0 * t);
        }

        return 1.0 - new NormalDistribution().cumulativeProbability(z);
    }

    /**
     * Gamma (Satterthwaite–Welch) approximation using first two moments.
     * Keep your existing implementation if you prefer; this is here for completeness.
     *
     * @param stat the test statistic
     * @param eig eigenvalues
     * @return the p-value
     */
    public static double gammaSatterthwaiteP(double stat, double[] eig) {
        eig = positiveFinite(eig);
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double s1 = 0.0, s2 = 0.0;
        for (double l : eig) {
            s1 += l;
            s2 += l * l;
        }

        double mu = s1;
        double var = 2.0 * s2;
        if (!(mu > 0) || !(var > 0)) return 1.0;

        double k = mu * mu / var;
        double theta = var / mu;

        // Gamma(shape=k, scale=theta) CDF tail
        org.apache.commons.math3.distribution.GammaDistribution gd =
                new org.apache.commons.math3.distribution.GammaDistribution(k, theta);
        return clamp01(1.0 - gd.cumulativeProbability(stat));
    }

    /**
     * Saddlepoint approximation (Lugannani–Rice) for Q = sum lambda_i * chi^2_1.
     *
     * Fast (O(m) per evaluation), uses the full spectrum, usually very accurate in tails.
     *
     * Returns right-tail p-value P(Q >= stat).
     *
     * @param stat the test statistic
     * @param eig eigenvalues
     * @return the p-value
     */
    public static double saddlepointLugannaniRiceP(double stat, double[] eig) {
        eig = positiveFinite(eig);
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        if (!(stat > 0)) return 1.0;

        // If stat is extremely close to mean, LR formula can suffer cancellation.
        // We'll handle the "near-mean" region with a safe normal approximation.
        final double mean = sum(eig);
        final double var = 2.0 * sumSquares(eig);
        final double sd = FastMath.sqrt(FastMath.max(0.0, var));
        if (!(sd > 0)) return 1.0;

        // relative tolerance region near mean
        final double near = 1e-8 * FastMath.max(1.0, FastMath.abs(mean));
        if (FastMath.abs(stat - mean) <= near) {
            double z = (stat - mean) / sd;
            return clamp01(1.0 - STD_NORMAL.cumulativeProbability(z));
        }

        // Solve K'(t) = stat for t, where
        // K(t)  = -1/2 sum log(1 - 2 t lambda_i)
        // K'(t) = sum lambda_i / (1 - 2 t lambda_i)
        // Domain: t < 1/(2*lambda_max)
        final double lmax = max(eig);
        final double tUpper = (1.0 / (2.0 * lmax)) * (1.0 - 1e-12); // stay inside domain

        // K'(t) is increasing; K'(0)=mean
        final boolean aboveMean = stat > mean;

        // Bracket root:
        // - if stat > mean, root t* is in (0, tUpper)
        // - if stat < mean, root t* is negative; bracket in (tLower, 0)
        double tLo, tHi;

        if (aboveMean) {
            tLo = 0.0;
            tHi = tUpper;
            // Ensure K'(tHi) >= stat (should be huge as t -> tUpper)
            // but for very ill-conditioned spectra, we still guard.
            double k1Hi = K1(tHi, eig);
            if (!(k1Hi >= stat)) {
                // If we can't reach stat, fall back to gamma.
                return gammaSatterthwaiteP(stat, eig);
            }
        } else {
            // Find a negative tLo such that K'(tLo) <= stat
            tHi = 0.0;
            tLo = -1.0;
            double k1Lo = K1(tLo, eig);
            int expand = 0;
            while (k1Lo > stat && expand < 60) {
                tLo *= 2.0; // more negative
                k1Lo = K1(tLo, eig);
                expand++;
                // K'(t) -> 0 as t -> -infty, so this should eventually drop below stat (>0).
                if (!Double.isFinite(k1Lo)) break;
            }
            if (!(K1(tLo, eig) <= stat)) {
                // Couldn't bracket; fall back.
                return gammaSatterthwaiteP(stat, eig);
            }
        }

        // Bisection (safe, monotone). 60 iters is plenty for double precision.
        double tStar = bisectForK1Equals(stat, eig, tLo, tHi, 60);

        // Compute Lugannani–Rice terms
        double Kt = K0(tStar, eig);
        double K1t = stat; // by construction
        double K2t = K2(tStar, eig);

        if (!(K2t > 0) || !Double.isFinite(Kt)) {
            // fallback
            return gammaSatterthwaiteP(stat, eig);
        }

        double w2 = 2.0 * (tStar * K1t - Kt);
        if (!(w2 > 0) || !Double.isFinite(w2)) {
            return gammaSatterthwaiteP(stat, eig);
        }

        double w = FastMath.copySign(FastMath.sqrt(w2), tStar);
        double u = tStar * FastMath.sqrt(K2t);

        // LR tail approximation:
        // p ≈ 1 - Φ(w) + φ(w) * (1/w - 1/u)
        // Guard for w ~ 0.
        double Phi_w = STD_NORMAL.cumulativeProbability(w);
        double phi_w = STD_NORMAL.density(w);

        double corr;
        if (FastMath.abs(w) < 1e-10 || FastMath.abs(u) < 1e-14) {
            // Fallback to normal approximation at saddlepoint
            // (or gamma) if extremely close to problematic region.
            double z = (stat - mean) / sd;
            return clamp01(1.0 - STD_NORMAL.cumulativeProbability(z));
        } else {
            corr = phi_w * (1.0 / w - 1.0 / u);
        }

        double p = (1.0 - Phi_w) + corr;
        return clamp01(p);
    }

    /**
     * Davies/Imhof-style inversion for Q = sum_i lambda_i * Z_i^2 (Z_i~N(0,1)).
     *
     * This is a self-contained numerical inversion of the characteristic function.
     * In practice it behaves like the “Davies” family of methods (high accuracy when the
     * eigen-spectrum is correct), and is usually much more reliable than HBE/LPB4.
     *
     * Returns right-tail p-value P(Q >= stat).
     *
     * Important notes:
     *  - Assumes lambdas are (numerically) nonnegative. Tiny negative values are truncated to 0.
     *  - For very small spectra (e.g., 1–3 eigenvalues) the integral can be more oscillatory; in that
     *    case saddlepoint-LR often wins on speed + stability.
     * @param stat the test statistic
     * @param eig eigenvalues
     * @return the p-value
     */
    public static double daviesP(double stat, double[] eig) {
        // Defaults tuned for “fast but accurate enough” in CI-testing.
        return daviesP(stat, eig, 1e-9, 1_000_000, 60);
    }

    /**
     * Calculates the right-tail p-value P(Q >= stat) using a method from
     * Davies and Imhof.
     *
     * @param stat the test statistic
     * @param eig eigenvalues
     * @param epsAbs absolute integration tolerance (cdf accuracy target)
     * @param maxEvals max integrand evaluations
     * @param maxRec max recursion depth for adaptive Simpson
     * @return the p-value
     */
    public static double daviesP(double stat, double[] eig,
                                 double epsAbs,
                                 int maxEvals,
                                 int maxRec) {
        double[] lam = positiveFiniteWithTruncation(eig, 1e-14);
        if (lam.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        if (!(stat > 0)) return 1.0;

        // Imhof inversion:
        //   F(x) = 1/2 + (1/pi) ∫_0^∞ Im( exp(-i t x) φ(t) ) / t dt
        // where φ(t) = Π (1 - 2 i t λ_j)^(-1/2)
        //
        // We evaluate integrand using magnitude/phase (no Complex allocation):
        //   (1 - 2 i t λ) has magnitude sqrt(1 + (2 t λ)^2), angle = -atan(2 t λ)
        //   raising to -1/2 gives magnitude = mag^(-1/2), phase = +0.5 atan(2 t λ)
        //
        // integrand g(t) = prodMag(t) * sin(phaseSum(t) - t x) / t

        final double x = stat;

        // Map t in [0,∞) to theta in [0, π/2):
        //   t = tan(theta), dt = sec^2(theta) dtheta
        // integral = ∫_0^{π/2} g(tanθ) sec^2θ dθ
        final double upper = FastMath.PI / 2.0 - 1e-9; // avoid tan blow-up at endpoint

        // We’ll count evaluations to cap work.
        final EvalCounter counter = new EvalCounter(maxEvals);

        final IntegrandTheta f = new IntegrandTheta(lam, x, counter);

        // Adaptive Simpson on [0, upper]
        double integral = adaptiveSimpsonTheta(f, 0.0, upper, epsAbs, maxRec);

        // If we hit eval limit, we still return best effort (clamped).
        double cdf = 0.5 - integral / FastMath.PI;
        cdf = clamp01(cdf);
        return clamp01(1.0 - cdf);
    }

// ---------------------------- Davies/Imhof helpers ----------------------------

    private static final class EvalCounter {
        final int max;
        int used = 0;
        EvalCounter(int max) { this.max = FastMath.max(1, max); }
        void bump() {
            used++;
            if (used > max) throw new TooManyEvals();
        }
    }
    private static final class TooManyEvals extends RuntimeException {}

    /** theta-integrand: h(theta) = g(tanθ) * sec^2θ */
    private interface ThetaFn { double value(double theta); }

    private static final class IntegrandTheta implements ThetaFn {
        private final double[] lam;
        private final double x;
        private final EvalCounter counter;
        private final double sumLam;

        IntegrandTheta(double[] lam, double x, EvalCounter counter) {
            this.lam = lam;
            this.x = x;
            this.counter = counter;
            this.sumLam = sum(lam);
        }

        @Override
        public double value(double theta) {
            counter.bump();

            // t = tan(theta)
            double cos = FastMath.cos(theta);
            double sin = FastMath.sin(theta);
            if (cos == 0.0) return 0.0;

            double t = sin / cos;
            double sec2 = 1.0 / (cos * cos);

            // Handle t ~ 0 using the finite limit:
            //   prodMag -> 1, phaseSum ~ t*sumLam, so g(t) ~ (sumLam - x)
            if (t < 1e-12) {
                return (sumLam - x) * sec2;
            }

            // Compute log(prodMag) and phaseSum
            double logMag = 0.0;
            double phase = 0.0;
            for (double l : lam) {
                // mag = sqrt(1 + (2 t l)^2)
                double a = 2.0 * t * l;
                double mag = FastMath.sqrt(1.0 + a * a);
                logMag += -0.5 * FastMath.log(mag);
                phase += 0.5 * FastMath.atan(a);
            }

            double prodMag = FastMath.exp(logMag);
            double arg = phase - t * x;
            double g = prodMag * FastMath.sin(arg) / t;

            return g * sec2;
        }
    }

    private static double adaptiveSimpsonTheta(ThetaFn f, double a, double b,
                                               double eps, int maxRec) {
        try {
            double fa = f.value(a);
            double fb = f.value(b);
            double m = 0.5 * (a + b);
            double fm = f.value(m);
            double S = simpson(a, b, fa, fm, fb);
            return adaptiveSimpsonRec(f, a, b, fa, fm, fb, S, eps, maxRec);
        } catch (TooManyEvals e) {
            // Best effort: return 0 so caller falls back to ~0.5 cdf behavior.
            // (You could alternatively fall back to saddlepoint or gamma here.)
            return 0.0;
        }
    }

    private static double adaptiveSimpsonRec(ThetaFn f,
                                             double a, double b,
                                             double fa, double fm, double fb,
                                             double S, double eps, int rec) {
        double m = 0.5 * (a + b);
        double lm = 0.5 * (a + m);
        double rm = 0.5 * (m + b);

        double flm = f.value(lm);
        double frm = f.value(rm);

        double Sleft  = simpson(a, m, fa, flm, fm);
        double Sright = simpson(m, b, fm, frm, fb);

        double err = (Sleft + Sright - S) / 15.0;
        if (rec <= 0 || FastMath.abs(err) <= eps) {
            return Sleft + Sright + err;
        }

        return adaptiveSimpsonRec(f, a, m, fa, flm, fm, Sleft,  eps * 0.5, rec - 1)
                + adaptiveSimpsonRec(f, m, b, fm, frm, fb, Sright, eps * 0.5, rec - 1);
    }

    private static double simpson(double a, double b, double fa, double fm, double fb) {
        return (b - a) * (fa + 4.0 * fm + fb) / 6.0;
    }

    /**
     * Like positiveFinite(), but also truncates tiny negative eigenvalues to 0 and drops them.
     * This helps if PSD-ness is violated by numerical noise.
     */
    private static double[] positiveFiniteWithTruncation(double[] eig, double negTol) {
        if (eig == null || eig.length == 0) return new double[0];
        double[] tmp = new double[eig.length];
        int k = 0;
        for (double v : eig) {
            if (!Double.isFinite(v)) continue;
            if (v >= negTol) { // allow tiny negatives to be dropped
                if (v > 1e-12) tmp[k++] = v;
            } else if (v > -negTol) {
                // tiny negative, treat as 0 (drop)
            } else {
                // materially negative eigenvalue => not PSD; safest is to drop (or throw).
                // We drop here, but you may prefer to throw for debugging.
            }
        }
        if (k == 0) return new double[0];
        return (k == tmp.length) ? tmp : Arrays.copyOf(tmp, k);
    }

    // ------------------------------ internals ------------------------------

    private static final NormalDistribution STD_NORMAL = new NormalDistribution(0, 1);

    /**
     * K(t) = -1/2 sum log(1 - 2 t lambda_i)
     */
    private static double K0(double t, double[] lam) {
        double s = 0.0;
        for (double l : lam) {
            double x = 1.0 - 2.0 * t * l;
            // x must be > 0 in domain
            if (!(x > 0.0)) return Double.NaN;
            s += log(x);
        }
        return -0.5 * s;
    }

    /**
     * K'(t) = sum lambda_i / (1 - 2 t lambda_i)
     */
    private static double K1(double t, double[] lam) {
        double s = 0.0;
        for (double l : lam) {
            double den = 1.0 - 2.0 * t * l;
            if (!(den > 0.0)) return Double.POSITIVE_INFINITY;
            s += l / den;
        }
        return s;
    }

    /**
     * K''(t) = 2 * sum lambda_i^2 / (1 - 2 t lambda_i)^2
     */
    private static double K2(double t, double[] lam) {
        double s = 0.0;
        for (double l : lam) {
            double den = 1.0 - 2.0 * t * l;
            if (!(den > 0.0)) return Double.NaN;
            double inv = 1.0 / den;
            s += (l * l) * (inv * inv);
        }
        return 2.0 * s;
    }

    private static double bisectForK1Equals(double target, double[] lam, double lo, double hi, int iters) {
        double fLo = K1(lo, lam) - target;
        double fHi = K1(hi, lam) - target;

        // Expect fLo <= 0 <= fHi.
        if (!(fLo <= 0.0 && fHi >= 0.0)) {
            // Try to recover by swapping if needed
            if (fLo >= 0.0 && fHi <= 0.0) {
                double tmp = lo; lo = hi; hi = tmp;
                tmp = fLo; fLo = fHi; fHi = tmp;
            } else {
                // Can't bracket
                return 0.0;
            }
        }

        double mid = 0.0;
        for (int i = 0; i < iters; i++) {
            mid = 0.5 * (lo + hi);
            double fMid = K1(mid, lam) - target;
            if (fMid <= 0.0) {
                lo = mid;
                fLo = fMid;
            } else {
                hi = mid;
                fHi = fMid;
            }
        }
        return mid;
    }

    private static double[] positiveFinite(double[] eig) {
        if (eig == null || eig.length == 0) return new double[0];
        double[] tmp = new double[eig.length];
        int k = 0;
        for (double v : eig) {
            if (v > 1e-12 && Double.isFinite(v)) tmp[k++] = v;
        }
        if (k == 0) return new double[0];
        return (k == tmp.length) ? tmp : Arrays.copyOf(tmp, k);
    }

    private static double sum(double[] a) {
        double s = 0.0;
        for (double v : a) s += v;
        return s;
    }

    private static double sumSquares(double[] a) {
        double s = 0.0;
        for (double v : a) s += v * v;
        return s;
    }

    private static double max(double[] a) {
        double m = a[0];
        for (int i = 1; i < a.length; i++) if (a[i] > m) m = a[i];
        return m;
    }

    private static double clamp01(double p) {
        if (p < 0.0) return 0.0;
        if (p > 1.0) return 1.0;
        if (!Double.isFinite(p)) return 0.0;
        return p;
    }

    /**
     * Stable-ish log for positive x without pulling in extra deps.
     * (You can replace with FastMath.log if you prefer.)
     */
    private static double log(double x) {
        return FastMath.log(x);
    }
}