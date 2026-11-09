package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared machinery for the random-MLP structural-equation simulators
 * ({@link GeneralNoiseSimulation}, {@link AdditiveNoiseSimulation},
 * {@link GeneralAdditiveModel}).
 * <p>
 * Consolidates the previously duplicated (and drifting) copies of the fast-tanh
 * detection, bias addition, in-place activation, column standardization, weight
 * initialization, and the ping-pong forward pass into one package-private
 * helper so the three simulators stay in sync.
 * <p>
 * Not intended as public API; deliberately package-private.
 */
final class RandomMlpSupport {

    private RandomMlpSupport() {
        // static utility class
    }

    // --------------------------------------------------------------------
    // Activation utilities
    // --------------------------------------------------------------------

    /**
     * Exact-match signature test for tanh, enabling the fast path in
     * {@link #applyActivationInPlace}. A loose "bounded and odd" heuristic
     * would also match sin, softsign, tanh(2x), etc., and the fast path would
     * then silently replace the caller's function with tanh — so we require
     * agreement with tanh to within 1e-12 at two generic points.
     *
     * @param f the candidate activation function
     * @return true iff f agrees with tanh at the probe points
     */
    static boolean isTanhLike(Function<Double, Double> f) {
        return TMath.abs(f.apply(1.0) - TMath.tanh(1.0)) < 1e-12
                && TMath.abs(f.apply(-0.7) - TMath.tanh(-0.7)) < 1e-12;
    }

    /**
     * Applies the activation function elementwise in place. If {@code fastTanh}
     * is true, uses {@link TMath#tanh(double)} directly, avoiding boxing
     * through the {@link Function} interface.
     *
     * @param a        matrix whose entries are transformed in place
     * @param f        activation function (ignored when fastTanh is true)
     * @param fastTanh whether to use the direct tanh fast path
     */
    static void applyActivationInPlace(DMatrixRMaj a,
                                       Function<Double, Double> f,
                                       boolean fastTanh) {
        final int n = a.getNumElements();
        if (fastTanh) {
            for (int i = 0; i < n; i++) a.data[i] = TMath.tanh(a.data[i]);
        } else {
            for (int i = 0; i < n; i++) a.data[i] = f.apply(a.data[i]);
        }
    }

    // --------------------------------------------------------------------
    // Row/column utilities
    // --------------------------------------------------------------------

    /**
     * Adds the bias vector b (length = numCols) to every row of A, in place.
     *
     * @param a matrix (row-major) to modify
     * @param b bias vector, one entry per column of a
     */
    static void addBiasRowsInPlace(DMatrixRMaj a, double[] b) {
        final int n = a.numRows;
        final int m = a.numCols;
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++, k++) {
                a.data[k] += b[j];
            }
        }
    }

    /**
     * Z-scores each column of A in place (mean 0, sd 1, dividing by n).
     * Columns with near-zero variance are left unchanged.
     * <p>
     * Note: this standardizes by the <em>realized sample</em> moments, which
     * couples rows within a dataset. Callers that use it (e.g.
     * {@link GeneralAdditiveModel}) should document that coupling.
     *
     * @param a matrix whose columns are standardized in place
     */
    static void zScoreColumnsInPlace(DMatrixRMaj a) {
        final int n = a.numRows;
        final int d = a.numCols;

        for (int j = 0; j < d; j++) {
            double mean = 0.0;
            int k = j;
            for (int i = 0; i < n; i++, k += d) {
                mean += a.data[k];
            }
            mean /= n;

            double var = 0.0;
            k = j;
            for (int i = 0; i < n; i++, k += d) {
                double diff = a.data[k] - mean;
                var += diff * diff;
            }
            var /= n;

            if (var < 1e-12) continue; // avoid division by ~0

            double invSd = 1.0 / TMath.sqrt(var);
            k = j;
            for (int i = 0; i < n; i++, k += d) {
                a.data[k] = (a.data[k] - mean) * invSd;
            }
        }
    }

    // --------------------------------------------------------------------
    // Initialization
    // --------------------------------------------------------------------

    /**
     * Fan-in Gaussian initialization: std = scale * sqrt(base / fanIn), where
     * base = 1 for tanh-like activations (LeCun-style) and base = 2 for
     * ReLU-like activations (He-style).
     *
     * @param w        weight matrix (out x in), filled in place
     * @param scale    overall scale multiplier
     * @param tanhLike whether to use the tanh-friendly base of 1 (vs 2)
     */
    static void initWeights(DMatrixRMaj w, double scale, boolean tanhLike) {
        double base = tanhLike ? 1.0 : 2.0;
        double s = scale * TMath.sqrt(base / TMath.max(1, w.numCols));
        for (int i = 0, n = w.getNumElements(); i < n; i++) {
            w.data[i] = RandomUtil.getInstance().nextGaussian() * s;
        }
    }

    /**
     * Fills a bias vector with iid Gaussian(0, std^2) entries. A std of 0
     * leaves the biases at exactly zero. Nonzero biases break the oddness of
     * zero-bias tanh MLPs (which otherwise map 0 to 0 and produce
     * sign-symmetric function families under symmetric noise).
     *
     * @param b   bias vector to fill
     * @param std standard deviation; 0 yields all-zero biases
     */
    static void randomizeBiases(double[] b, double std) {
        if (std == 0.0) return;
        for (int i = 0; i < b.length; i++) {
            b[i] = RandomUtil.getInstance().nextGaussian() * std;
        }
    }

    // --------------------------------------------------------------------
    // Diagnostics
    // --------------------------------------------------------------------

    /**
     * Prints the fraction of activations at or beyond the given absolute
     * threshold, as a saturation diagnostic for tanh-like layers.
     *
     * @param nodeName     name of the node being simulated
     * @param layerIndex   1-based hidden-layer index
     * @param activations  the post-activation matrix for the layer
     * @param absThreshold absolute-value threshold defining "saturated"
     */
    static void printSaturationStats(String nodeName,
                                     int layerIndex,
                                     DMatrixRMaj activations,
                                     double absThreshold) {
        int total = activations.getNumElements();
        int sat = 0;

        for (int i = 0; i < total; i++) {
            if (TMath.abs(activations.data[i]) >= absThreshold) sat++;
        }

        double pct = 100.0 * sat / TMath.max(1, total);

        System.out.printf(
                Locale.US,
                "Random-MLP saturation: node=%s layer=%d threshold=|a|>=%.3f saturated=%d/%d (%.2f%%)%n",
                nodeName, layerIndex, absThreshold, sat, total, pct
        );
    }

    // --------------------------------------------------------------------
    // The random MLP itself
    // --------------------------------------------------------------------

    /**
     * A randomly initialized feed-forward MLP with an arbitrary number of
     * hidden layers, evaluated batchwise on an (N x Din) input matrix using a
     * two-buffer ping-pong forward pass so EJML never sees aliasing and no
     * per-call allocation occurs.
     * <p>
     * Weights use {@link #initWeights} with the tanh-friendly base; hidden
     * layers use the full input scale and the output layer half of it (the
     * convention shared by all three simulators). Biases are Gaussian with the
     * supplied std (0 preserves the historical all-zero-bias behavior).
     */
    static final class RandomMlp {
        private final int dout;
        private final int[] hidden;
        private final DMatrixRMaj[] w;   // layer weights: (out x in)
        private final double[][] b;      // biases per layer
        private final Function<Double, Double> activation;
        private final boolean fastTanh;

        /**
         * Constructs a random MLP.
         *
         * @param din        input dimension (>= 1)
         * @param hidden     hidden layer sizes; may be empty (linear map)
         * @param dout       output dimension (>= 1)
         * @param inputScale scale multiplier for weight initialization
         * @param biasStd    std of Gaussian biases; 0 for all-zero biases
         * @param activation activation function for hidden layers
         * @param fastTanh   whether activation is exactly tanh (fast path)
         */
        RandomMlp(int din,
                  int[] hidden,
                  int dout,
                  double inputScale,
                  double biasStd,
                  Function<Double, Double> activation,
                  boolean fastTanh) {
            if (din < 1) throw new IllegalArgumentException("din must be >= 1");
            if (dout < 1) throw new IllegalArgumentException("dout must be >= 1");
            Objects.requireNonNull(activation, "activation");

            this.dout = dout;
            this.hidden = hidden == null ? new int[0] : hidden.clone();
            this.activation = activation;
            this.fastTanh = fastTanh;

            int numLayers = this.hidden.length + 1;
            this.w = new DMatrixRMaj[numLayers];
            this.b = new double[numLayers][];

            int prev = din;
            for (int l = 0; l < this.hidden.length; l++) {
                w[l] = new DMatrixRMaj(this.hidden[l], prev);
                b[l] = new double[this.hidden[l]];
                initWeights(w[l], inputScale, true);
                randomizeBiases(b[l], biasStd);
                prev = this.hidden[l];
            }

            w[numLayers - 1] = new DMatrixRMaj(dout, prev);
            b[numLayers - 1] = new double[dout];
            initWeights(w[numLayers - 1], 0.5 * inputScale, true);
            randomizeBiases(b[numLayers - 1], 0.5 * biasStd);
        }

        /**
         * Forward pass without saturation reporting.
         *
         * @param x        (N x Din) input; not modified
         * @param scratch1 reusable scratch buffer (reshaped as needed)
         * @param scratch2 reusable scratch buffer (reshaped as needed)
         * @param out      output buffer; reshaped to (N x Dout)
         * @return out, for convenience
         */
        DMatrixRMaj forward(DMatrixRMaj x,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj scratch2,
                            DMatrixRMaj out) {
            return forward(x, scratch1, scratch2, out, false, 0.0, null);
        }

        /**
         * Forward pass with optional per-layer saturation reporting.
         *
         * @param x                (N x Din) input; not modified
         * @param scratch1         reusable scratch buffer
         * @param scratch2         reusable scratch buffer
         * @param out              output buffer; reshaped to (N x Dout)
         * @param reportSaturation whether to print saturation stats per layer
         * @param satThreshold     absolute activation threshold for saturation
         * @param nodeName         node label used in the saturation report
         * @return out, for convenience
         */
        DMatrixRMaj forward(DMatrixRMaj x,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj scratch2,
                            DMatrixRMaj out,
                            boolean reportSaturation,
                            double satThreshold,
                            String nodeName) {

            DMatrixRMaj cur = x;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = scratch2;

            // Hidden layers
            for (int l = 0; l < hidden.length; l++) {
                int h = hidden[l];

                DMatrixRMaj dest = (cur == bufA) ? bufB : bufA;
                dest.reshape(x.numRows, h, false);

                CommonOps_DDRM.multTransB(cur, w[l], dest);
                addBiasRowsInPlace(dest, b[l]);
                applyActivationInPlace(dest, activation, fastTanh);

                if (reportSaturation) {
                    printSaturationStats(nodeName, l + 1, dest, satThreshold);
                }

                cur = dest;
            }

            // Output layer into 'out' (must not alias cur; with empty hidden
            // dims, cur is still x here, so the guard also protects x).
            if (out == cur) throw new IllegalArgumentException("Output aliases current buffer.");
            out.reshape(x.numRows, dout, false);
            CommonOps_DDRM.multTransB(cur, w[w.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }
    }
}
