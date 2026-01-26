package edu.cmu.tetrad.search.test.ffci_utils;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.decomposition.qr.QRDecompositionHouseholder_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.Random;

/**
 * Utilities for Random Fourier Features (RFF) and Orthogonal Random Features (ORF)
 * for the RBF kernel.
 *
 * Conventions:
 *  - raw is n×d (typically already standardized / z-scored)
 *  - sigma is the RBF bandwidth (must be > 0; if not, we fall back to 1.0)
 *  - numF is the number of random features (must be >= 1)
 *
 * RFF-RBF:
 *  phi(x) = sqrt(2/m) * cos(W x + b), with W_ij ~ N(0, 1/sigma) and b_i ~ U[0, 2pi)
 *
 * ORF-RBF (Yu et al.-style):
 *  W blocks are formed from orthogonal matrices Q and radial scaling s ~ ||N(0,I)||.
 *  This gives "more orthogonal" directions than plain iid Gaussian W and often reduces variance.
 */
public final class RffUtils {

    private RffUtils() {}

    /** RFF for RBF: sqrt(2/m) * cos(W x + b), W ~ N(0, 1/sigma), b ~ U[0, 2pi). */
    public static SimpleMatrix rffRbf(SimpleMatrix raw, int numF, double sigma, Random rng) {
        if (raw == null) throw new NullPointerException("raw");
        if (rng == null) throw new NullPointerException("rng");
        int n = raw.numRows();
        int d = raw.numCols();
        if (numF < 1) throw new IllegalArgumentException("numF must be >= 1");
        if (d == 0) return new SimpleMatrix(n, 0);

        sigma = sanitizeSigma(sigma);
        double invSigma = 1.0 / sigma;

        // phases
        double[] b = new double[numF];
        double twoPi = 2.0 * Math.PI;
        for (int i = 0; i < numF; i++) b[i] = rng.nextDouble() * twoPi;

        // W row-major (numF x d)
        double[] W = new double[numF * d];
        for (int i = 0; i < numF; i++) {
            int base = i * d;
            for (int j = 0; j < d; j++) {
                W[base + j] = rng.nextGaussian() * invSigma;
            }
        }

        // features
        SimpleMatrix feat = new SimpleMatrix(n, numF);
        double scale = Math.sqrt(2.0 / numF);

        for (int r = 0; r < n; r++) {
            for (int f = 0; f < numF; f++) {
                int base = f * d;
                double dot = 0.0;
                for (int j = 0; j < d; j++) dot += W[base + j] * raw.get(r, j);
                feat.set(r, f, scale * Math.cos(dot + b[f]));
            }
        }
        return feat;
    }

    /**
     * ORF for RBF (orthogonal random features).
     *
     * Implementation details:
     *  - Build W in blocks of size d x d (or smaller on last block if numF not multiple of d).
     *  - For each block:
     *      G ~ N(0,1)^{dxd}
     *      Q = orthonormal(G)  (via QR; adjust sign so diag(R) positive)
     *      s_i = ||g_i|| where g_i ~ N(0, I_d)  (chi radius)
     *      w_i = (s_i / sigma) * q_i   (q_i = row i of Q)
     *  - Then same cosine features: sqrt(2/m) * cos(Wx + b).
     *
     * Notes:
     *  - This is the common ORF-RBF recipe used in practice; it’s not the only ORF variant.
     *  - If d is 1, ORF collapses to RFF (still works).
     */
    public static SimpleMatrix orfRbf(SimpleMatrix raw, int numF, double sigma, Random rng) {
        if (raw == null) throw new NullPointerException("raw");
        if (rng == null) throw new NullPointerException("rng");
        int n = raw.numRows();
        int d = raw.numCols();
        if (numF < 1) throw new IllegalArgumentException("numF must be >= 1");
        if (d == 0) return new SimpleMatrix(n, 0);

        sigma = sanitizeSigma(sigma);
        double invSigma = 1.0 / sigma;

        // phases
        double[] b = new double[numF];
        double twoPi = 2.0 * Math.PI;
        for (int i = 0; i < numF; i++) b[i] = rng.nextDouble() * twoPi;

        // Build W row-major (numF x d) in orthogonal blocks
        double[] W = new double[numF * d];
        int filled = 0;
        while (filled < numF) {
            int blockRows = Math.min(d, numF - filled); // how many rows we still need
            // Build a full dxd orthogonal matrix Q, then take its first blockRows rows.
            SimpleMatrix Q = randomOrthonormalMatrix(d, rng); // d x d

            // radial scales s_i ~ ||N(0, I_d)||
            double[] s = new double[blockRows];
            for (int i = 0; i < blockRows; i++) {
                // chi radius
                double ss = 0.0;
                for (int j = 0; j < d; j++) {
                    double g = rng.nextGaussian();
                    ss += g * g;
                }
                s[i] = Math.sqrt(ss);
            }

            // rows of Q -> rows of W
            for (int i = 0; i < blockRows; i++) {
                int row = filled + i;
                int base = row * d;
                double scale = s[i] * invSigma;
                for (int j = 0; j < d; j++) {
                    // row i of Q
                    W[base + j] = scale * Q.get(i, j);
                }
            }

            filled += blockRows;
        }

        // features
        SimpleMatrix feat = new SimpleMatrix(n, numF);
        double scale = Math.sqrt(2.0 / numF);

        for (int r = 0; r < n; r++) {
            for (int f = 0; f < numF; f++) {
                int base = f * d;
                double dot = 0.0;
                for (int j = 0; j < d; j++) dot += W[base + j] * raw.get(r, j);
                feat.set(r, f, scale * Math.cos(dot + b[f]));
            }
        }
        return feat;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static double sanitizeSigma(double sigma) {
        if (!Double.isFinite(sigma) || sigma <= 0.0) return 1.0;
        // avoid absurdly tiny bandwidths that explode W
        return Math.max(sigma, 1e-12);
    }

    /**
     * Random orthonormal matrix Q (d×d) from QR decomposition of a Gaussian matrix.
     * We flip signs so that diag(R) is positive (makes Q deterministic-ish given G).
     */
    private static SimpleMatrix randomOrthonormalMatrix(int d, Random rng) {
        // G ~ N(0,1)^{dxd}
        DMatrixRMaj G = new DMatrixRMaj(d, d);
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                G.set(i, j, rng.nextGaussian());
            }
        }

        QRDecompositionHouseholder_DDRM qr = new QRDecompositionHouseholder_DDRM();
        if (!qr.decompose(G)) {
            // extremely unlikely; fall back to identity
            return SimpleMatrix.identity(d);
        }

        DMatrixRMaj Q = new DMatrixRMaj(d, d);
        qr.getQ(Q, false);

        // Make diag(R) positive by flipping corresponding columns of Q.
        DMatrixRMaj R = new DMatrixRMaj(d, d);
        qr.getR(R, false);

        for (int i = 0; i < d; i++) {
            double rii = R.get(i, i);
            if (rii < 0) {
                // flip column i of Q
                for (int r = 0; r < d; r++) {
                    Q.set(r, i, -Q.get(r, i));
                }
            }
        }

        // Ensure Q is well-formed (optional; cheap sanity)
        // (You can comment this out once you’re comfortable.)
        // orthonormality drift here is usually tiny, but we could re-orth if needed.

        return SimpleMatrix.wrap(Q);
    }
}