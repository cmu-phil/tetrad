package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.*;
import java.util.function.Function;

/**
 * <p><b>GeneralAdditiveModel</b></p>
 *
 * <p>
 * Simulates a genuine generalized additive structural equation model of the form
 * </p>
 *
 * <pre>
 *     X_j = sum_{k in Pa(j)} f_{jk}(X_k) + e_j
 * </pre>
 *
 * <p>
 * where each {@code f_{jk}} is a separate randomly initialized univariate neural
 * network (a 1-input MLP), and {@code e_j} is an independent noise term drawn
 * from the supplied {@link Sampler}.
 * </p>
 *
 * <p>
 * Root nodes are generated as pure noise. For non-root nodes, each parent
 * contributes through its own learned random subnet, preserving additivity
 * across parent effects.
 * </p>
 *
 * <p>
 * This class is intended to align with the ANM/GNM simulators while preserving
 * the defining additive structure of a GAM.
 * </p>
 */
public final class GeneralAdditiveModel {

    private final Graph graph;
    private final int numSamples;
    private final Sampler sampler;

    private int[] hiddenDimensions = new int[]{8, 8};
    private double inputScale = 1.0;
    private boolean inputStandardize = true;
    private Function<Double, Double> activationFunction = TMath::tanh;
    private boolean useFastTanh = true;

    /**
     * Constructs a new generalized additive model simulator.
     *
     * @param graph      DAG over which data are simulated.
     * @param numSamples Number of rows to simulate.
     * @param sampler    Noise sampler used for the additive errors.
     */
    public GeneralAdditiveModel(Graph graph, int numSamples, Sampler sampler) {
        if (graph == null) throw new NullPointerException("graph");
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }
        if (numSamples < 1) {
            throw new IllegalArgumentException("numSamples must be positive.");
        }
        if (sampler == null) throw new NullPointerException("sampler");

        this.graph = graph;
        this.numSamples = numSamples;
        this.sampler = sampler;
    }

    // --------------------------------------------------------------------
    // Configuration
    // --------------------------------------------------------------------

    private static boolean looksLikeTanh(Function<Double, Double> f) {
        double[] xs = {-2.0, -1.0, -0.5, 0.5, 1.0, 2.0};
        for (double x : xs) {
            double fx = f.apply(x);
            if (!Double.isFinite(fx)) return false;
            if (TMath.abs(fx) > 1.000001) return false;
        }

        double a = f.apply(0.5), b = f.apply(-0.5);
        double c = f.apply(1.0), d = f.apply(-1.0);
        return TMath.abs(a + b) < 1e-6 && TMath.abs(c + d) < 1e-6;
    }

    private static void addBiasRowsInPlace(DMatrixRMaj A, double[] b) {
        final int n = A.numRows;
        final int m = A.numCols;
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++, k++) {
                A.data[k] += b[j];
            }
        }
    }

    private static void applyActivationInPlace(DMatrixRMaj A, Function<Double, Double> f, boolean fastTanh) {
        final int n = A.getNumElements();
        if (fastTanh) {
            for (int i = 0; i < n; i++) {
                A.data[i] = TMath.tanh(A.data[i]);
            }
        } else {
            for (int i = 0; i < n; i++) {
                A.data[i] = f.apply(A.data[i]);
            }
        }
    }

    /**
     * Z-scores each column of A in place.
     * Columns with near-zero variance are left unchanged.
     */
    private static void zScoreColumnsInPlace(DMatrixRMaj A) {
        final int n = A.numRows;
        final int d = A.numCols;

        for (int j = 0; j < d; j++) {
            double mean = 0.0;
            int k = j;
            for (int i = 0; i < n; i++, k += d) {
                mean += A.data[k];
            }
            mean /= n;

            double var = 0.0;
            k = j;
            for (int i = 0; i < n; i++, k += d) {
                double diff = A.data[k] - mean;
                var += diff * diff;
            }
            var /= n;

            if (var < 1e-12) continue;

            double invSd = 1.0 / TMath.sqrt(var);
            k = j;
            for (int i = 0; i < n; i++, k += d) {
                A.data[k] = (A.data[k] - mean) * invSd;
            }
        }
    }

    /**
     * Sets the hidden dimensions for the model. Hidden dimensions represent the sizes
     * of the hidden layers used within the model. All dimensions must be greater than or equal to 1.
     *
     * @param hiddenDimensions the sizes of the hidden layers of the model, each must be >= 1
     * @return the updated GeneralAdditiveModel instance with the specified hidden dimensions
     * @throws NullPointerException     if the hiddenDimensions array is null
     * @throws IllegalArgumentException if any dimension in hiddenDimensions is less than 1
     */
    public GeneralAdditiveModel setHiddenDimensions(int... hiddenDimensions) {
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        for (int h : hiddenDimensions) {
            if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1.");
        }
        this.hiddenDimensions = hiddenDimensions.clone();
        return this;
    }

    // --------------------------------------------------------------------
    // Main generation
    // --------------------------------------------------------------------

    /**
     * Sets the input scale for the model. The input scale must be a finite, positive value.
     *
     * @param inputScale the scaling factor to be applied to the input data, must be finite and greater than 0
     * @return the updated GeneralAdditiveModel instance with the specified input scale
     * @throws IllegalArgumentException if the input scale is not finite or less than or equal to 0
     */
    public GeneralAdditiveModel setInputScale(double inputScale) {
        if (!Double.isFinite(inputScale) || inputScale <= 0.0) {
            throw new IllegalArgumentException("inputScale must be finite and > 0.");
        }
        this.inputScale = inputScale;
        return this;
    }

    // --------------------------------------------------------------------
    // Utilities
    // --------------------------------------------------------------------

    /**
     * Sets whether the input data should be standardized for the model.
     * Standardizing inputs typically involves scaling them to have a mean of
     * zero and a standard deviation of one, which can improve model performance
     * and stability in certain cases.
     *
     * @param inputStandardize a boolean indicating whether input standardization
     *                         should be applied (true for standardization, false otherwise)
     * @return the updated GeneralAdditiveModel instance with the specified
     * input standardization behavior
     */
    public GeneralAdditiveModel setInputStandardize(boolean inputStandardize) {
        this.inputStandardize = inputStandardize;
        return this;
    }

    /**
     * Sets the activation function for the generalized additive model. The activation function
     * transforms data through a specified mapping, typically used within neural networks or
     * simulation frameworks. If the specified activation function resembles a hyperbolic tangent
     * function, an optimization flag is set to use a faster implementation.
     *
     * @param activationFunction the function to be used as the activation function, must be non-null
     * @return the updated GeneralAdditiveModel instance with the specified activation function
     * @throws NullPointerException if the activationFunction is null
     */
    public GeneralAdditiveModel setActivationFunction(Function<Double, Double> activationFunction) {
        Objects.requireNonNull(activationFunction, "activationFunction");
        this.activationFunction = activationFunction;
        this.useFastTanh = looksLikeTanh(activationFunction);
        return this;
    }

    /**
     * Generates a simulated dataset according to the structure of the graph and the specified parameters
     * of the General Additive Model. The dataset is created by sampling noise, combining contributions
     * from parent nodes in the graph using subnet evaluations, and optionally applying input standardization
     * and activation functions.
     *
     * @return a DataSet object containing the simulated data and associated graph node ordering
     */
    public DataSet generate() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int p = topo.size();
        final int n = numSamples;

        final double[][] raw = new double[n][p];

        final Map<Node, Integer> indexOf = new HashMap<>(2 * p);
        for (int j = 0; j < p; j++) {
            indexOf.put(topo.get(j), j);
        }

        final int[][] parentsIdx = new int[p][];
        for (int j = 0; j < p; j++) {
            List<Node> parents = graph.getParents(topo.get(j));
            int[] idx = new int[parents.size()];
            for (int k = 0; k < parents.size(); k++) {
                idx[k] = indexOf.get(parents.get(k));
            }
            parentsIdx[j] = idx;
        }

        // Reusable buffers for one-parent subnet evaluations.
        DMatrixRMaj xCol = new DMatrixRMaj(n, 1);
        DMatrixRMaj scratch = new DMatrixRMaj(n, 1);
        DMatrixRMaj out = new DMatrixRMaj(n, 1);

        final double[] noise = new double[n];

        for (int j = 0; j < p; j++) {
            final int[] pj = parentsIdx[j];

            // draw additive error term e_j
            for (int i = 0; i < n; i++) {
                noise[i] = sampler.sample();
            }

            if (pj.length == 0) {
                // Root: X_j = e_j
                for (int i = 0; i < n; i++) {
                    raw[i][j] = noise[i];
                }
                continue;
            }

            // Start with additive noise.
            for (int i = 0; i < n; i++) {
                raw[i][j] = noise[i];
            }

            // Sum separate subnet contributions f_jk(X_k) over parents.
            for (int parentIndex : pj) {
                // Build 1-column input from this parent.
                xCol.reshape(n, 1, false);
                for (int i = 0; i < n; i++) {
                    xCol.data[i] = raw[i][parentIndex];
                }

                if (inputStandardize) {
                    zScoreColumnsInPlace(xCol);
                }

                // Optional pre-activation on raw input, matching the ANM style a bit.
                applyActivationInPlace(xCol, activationFunction, useFastTanh);

                RandomUnivariateMLP subnet = new RandomUnivariateMLP(hiddenDimensions, inputScale, activationFunction, useFastTanh);

                out = subnet.forward(xCol, scratch, out);

                for (int i = 0; i < n; i++) {
                    raw[i][j] += out.data[i];
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    // --------------------------------------------------------------------
    // Random univariate MLP
    // --------------------------------------------------------------------

    /**
     * A 1-input random MLP used to generate one parent contribution f_jk(X_k).
     */
    private static final class RandomUnivariateMLP {
        private final int[] hidden;
        private final DMatrixRMaj[] W; // (out x in)
        private final double[][] b;
        private final Function<Double, Double> activationFunction;
        private final boolean useFastTanh;

        RandomUnivariateMLP(int[] hidden, double inputScale, Function<Double, Double> activationFunction, boolean useFastTanh) {
            this.hidden = hidden == null ? new int[0] : hidden.clone();
            this.activationFunction = activationFunction;
            this.useFastTanh = useFastTanh;

            int L = this.hidden.length + 1;
            this.W = new DMatrixRMaj[L];
            this.b = new double[L][];

            int prev = 1; // univariate input
            for (int l = 0; l < this.hidden.length; l++) {
                W[l] = new DMatrixRMaj(this.hidden[l], prev);
                b[l] = new double[this.hidden[l]];
                xavierLikeInit(W[l], inputScale, true);
                prev = this.hidden[l];
            }

            W[L - 1] = new DMatrixRMaj(1, prev);
            b[L - 1] = new double[1];
            xavierLikeInit(W[L - 1], 0.5 * inputScale, true);
        }

        private static void xavierLikeInit(DMatrixRMaj W, double scale, boolean tanhLike) {
            double base = tanhLike ? 1.0 : 2.0;
            double s = scale * TMath.sqrt(base / TMath.max(1, W.numCols));
            for (int i = 0, n = W.getNumElements(); i < n; i++) {
                W.data[i] = RandomUtil.getInstance().nextGaussian() * s;
            }
        }

        DMatrixRMaj forward(DMatrixRMaj X, DMatrixRMaj scratch1, DMatrixRMaj out) {
            DMatrixRMaj cur = X;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = new DMatrixRMaj(1, 1);

            for (int l = 0; l < hidden.length; l++) {
                int h = hidden[l];
                DMatrixRMaj dest = (cur == bufA) ? bufB : bufA;
                dest.reshape(X.numRows, h, false);

                CommonOps_DDRM.multTransB(cur, W[l], dest);
                addBiasRowsInPlace(dest, b[l]);
                applyActivationInPlace(dest, activationFunction, useFastTanh);
                cur = dest;
            }

            out.reshape(X.numRows, 1, false);
            CommonOps_DDRM.multTransB(cur, W[W.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }
    }
}