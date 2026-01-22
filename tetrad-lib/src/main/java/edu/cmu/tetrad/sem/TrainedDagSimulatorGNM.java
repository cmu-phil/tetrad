// File: edu/cmu/tetrad/sem/TrainedDagSimulator.java
package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * <h2>Trained DAG simulator (GNM): learn local mechanisms with non-additive noise and resimulate</h2>
 *
 * <p>This class is a “train-then-resimulate” simulator: given an observed {@link DataSet} and a
 * user-supplied acyclic {@link Graph} (treated as a DAG), it learns a local conditional mechanism
 * for each node and then generates new samples by running the DAG forward in topological order.</p>
 *
 * <p><b>Primary motivation.</b> The purpose is to construct realistic-looking synthetic datasets
 * anchored to an observed dataset while making the assumed causal structure explicit. Because the
 * mechanisms are learned from real data and noise is bootstrapped, substantially different DAGs can
 * yield resimulated data with similar marginals and pairwise structure; however, scoring the resulting
 * data with the wrong graph should degrade in a systematic way. This helps separate “looks plausible”
 * from “fits the causal structure.”</p>
 *
 * <h3>Model family (GNM)</h3>
 * <p>For each node {@code Y} with parents {@code Pa(Y)}, the fitted mechanism is a general-noise model</p>
 * <pre>
 *   Y = g(Pa(Y), e)
 * </pre>
 * <p>where {@code e} is a bootstrapped noise term and {@code g} is learned by a small neural network that
 * allows the noise to enter <em>non-additively</em>. One simple realization is a two-stage fit:</p>
 * <ol>
 *   <li>Fit {@code μ(x) ≈ E[Y | x]} using an MLP on parent features {@code x}.</li>
 *   <li>Compute residuals {@code e = y - μ(x)} on the training rows, bootstrap {@code e}, and train a
 *       second network to predict {@code y} from {@code (x, e)}.</li>
 * </ol>
 * <p>At simulation time, sample {@code e} by bootstrap and generate {@code y = g(x, e)}.</p>
 *
 * <h3>Mixed data support</h3>
 * <ul>
 *   <li><b>Continuous child:</b> uses the general-noise mechanism described above; noise values are bootstrapped
 *       (optionally stratified by discrete-parent signatures) and injected as an explicit input to {@code g}.</li>
 *   <li><b>Discrete child:</b> uses a softmax classifier; optionally mixes unconditional base rates with
 *       parent-conditional probabilities to “turn down” parent influence.</li>
 * </ul>
 *
 * <h3>Parent encoding</h3>
 * <ul>
 *   <li><b>Continuous parents</b> enter as z-scored scalars.</li>
 *   <li><b>Discrete parents</b> enter as one-hot blocks (with a configurable maximum number of levels).</li>
 * </ul>
 *
 * <h3>Missingness</h3>
 * <p>Training is done node-by-node using only rows where the child and all of its parents are observed.
 * Rows with any missing among {@code {Y} ∪ Pa(Y)} are skipped for that node. Simulation produces complete
 * samples given the learned mechanisms.</p>
 *
 * <h3>Notes and limitations</h3>
 * <ul>
 *   <li>This is intentionally a lightweight, dependency-minimal mechanism learner (single hidden layer, SGD, L2 decay),
 *       intended for simulation fidelity and portability rather than best-in-class predictive modeling.</li>
 *   <li>Because the simulator runs forward under the supplied DAG, unrealistic samples may occur when simulated parent
 *       configurations leave the support of the trafining data (extrapolation). Optional diagnostics may warn when
 *       parent z-scores become extreme.</li>
 *   <li>The GNM is strictly more expressive than the ANM: it can represent non-additive effects of noise on the child
 *       given parents, while still using bootstrapped noise anchored to the observed dataset.</li>
 * </ul>
 */
public final class TrainedDagSimulatorGNM {

    // -------------------- configuration --------------------
    private final List<NodeReport> nodeReports = new ArrayList<>();

    // -------------------- fit reporting --------------------
    private final DataSet data;
    private final Graph dag;
    private final Params params;
    private final List<Node> variables;
    private final boolean[] isDiscrete;

    // -------------------- trained model per node --------------------
    private final Mechanism[] mechanisms;

    public TrainedDagSimulatorGNM(DataSet data, Graph dag, Params params) {
        if (data == null) throw new NullPointerException("data");
        if (dag == null) throw new NullPointerException("dag");
        if (!dag.paths().isAcyclic()) throw new IllegalArgumentException("DAG contains cycles.");

        this.data = data;
        this.dag = dag;
        this.params = (params == null) ? new Params() : params;

        this.variables = data.getVariables();

        int p = variables.size();
        this.isDiscrete = new boolean[p];
        for (int j = 0; j < p; j++) isDiscrete[j] = (variables.get(j) instanceof DiscreteVariable);

        this.mechanisms = new Mechanism[p];
    }

    private static int sampleCategorical(double[] probs, Random rng) {
        double u = rng.nextDouble();
        double cdf = 0.0;
        int k = probs.length - 1;
        for (int i = 0; i < probs.length; i++) {
            cdf += probs[i];
            if (u <= cdf) {
                k = i;
                break;
            }
        }
        return k;
    }

    private static int argmax(double[] a) {
        int best = 0;
        double v = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > v) {
                v = a[i];
                best = i;
            }
        }
        return best;
    }

    private static double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }

    // -------------------- encoding parents into feature vectors --------------------

    /**
     * Human-readable report (one block per node).
     */
    public String getFitReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("TrainedDagSimulator fit report\n");
        sb.append("seed=").append(params.seed).append("\n");
        sb.append("hidden=").append(params.hidden)
                .append(" epochs=").append(params.epochs)
                .append(" lr=").append(params.lr)
                .append(" l2=").append(params.l2)
                .append(" batchSize=").append(params.batchSize)
                .append("\n\n");

        for (NodeReport r : nodeReports) {
            sb.append(r.node).append(r.discreteChild ? " (discrete)" : " (continuous)").append("\n");
            sb.append("  parents=").append(r.parents).append("\n");
            sb.append("  trainingRowsUsed=").append(r.trainingRowsUsed).append("\n");
            if (!r.discreteChild) {
                sb.append("  mseTrain=").append(r.mseTrain).append("\n");
                sb.append("  residualMean=").append(r.residualMean).append("\n");
                sb.append("  residualSd=").append(r.residualSd).append("\n");
            } else {
                sb.append("  numLevels=").append(r.numLevels).append("\n");
                sb.append("  xentTrain=").append(r.xentTrain).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // -------------------- training row selection per node --------------------

    /**
     * Structured per-node reports.
     */
    public List<NodeReport> getNodeReports() {
        return Collections.unmodifiableList(nodeReports);
    }

    // -------------------- tiny MLPs (1 hidden layer) --------------------

    public void writeFitReportTxt(File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(getFitReportText());
        }
    }

    /**
     * Fit one mechanism per node given its parents in the supplied DAG.
     */
    public void fit() {
        nodeReports.clear();
        Random rng = new Random(params.seed);

        // topo order (use dag nodes, but we map to dataset indices)
        List<Node> topo = dag.paths().getValidOrder(dag.getNodes(), true);

        // map name -> dataset index
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) indexByName.put(variables.get(j).getName(), j);

        for (Node child : topo) {
            Integer childIdxObj = indexByName.get(child.getName());
            if (childIdxObj == null) continue;
            int childIdx = childIdxObj;

            List<Node> ps = dag.getParents(child);
            int[] parentIdx = new int[ps.size()];
            for (int k = 0; k < ps.size(); k++) {
                Integer pi = indexByName.get(ps.get(k).getName());
                if (pi == null) {
                    throw new IllegalArgumentException("Parent not found in dataset: " + ps.get(k).getName());
                }
                parentIdx[k] = pi;
            }

            boolean isRoot = parentIdx.length == 0;

            // Important: build encoder once, BEFORE any non-root mechanism needs it.
            // (It is harmless for roots too, but roots don't need it.)
            InputEncoder encoder = new InputEncoder(data, parentIdx, params.maxDiscreteLevels);

            if (!isDiscrete[childIdx]) {
                if (isRoot && params.bootstrapRoots) {
                    RootContinuousMechanism m = new RootContinuousMechanism(childIdx);
                    m.fit(data, rng, params);
                    mechanisms[childIdx] = m;
                } else {
                    ContinuousMechanism m = new ContinuousMechanism(childIdx, parentIdx, encoder, params.hidden, rng);
                    m.fit(data, rng, params);
                    mechanisms[childIdx] = m;
                }
            } else {
                int L = ((DiscreteVariable) variables.get(childIdx)).getNumCategories();
                if (L <= 1) {
                    throw new IllegalArgumentException("Discrete variable has <=1 category: " + variables.get(childIdx).getName());
                }
                if (L > params.maxDiscreteLevels) {
                    throw new IllegalArgumentException("Discrete child has too many levels: " + variables.get(childIdx).getName() + " L=" + L);
                }

                if (isRoot && params.bootstrapRoots) {
                    RootDiscreteMechanism m = new RootDiscreteMechanism(childIdx, L);
                    m.fit(data, rng, params);
                    mechanisms[childIdx] = m;
                } else {
                    DiscreteMechanism m = new DiscreteMechanism(childIdx, parentIdx, encoder, params.hidden, L, rng);
                    m.fit(data, rng, params);
                    mechanisms[childIdx] = m;
                }
            }
        }
    }

    // -------------------- main simulator object --------------------

    public SimResult simulate(int nSamples) {
        return simulate(nSamples, params.seed ^ 0x9E3779B97F4A7C15L);
    }

    public SimResult simulate(int nSamples, long seed) {
        if (nSamples < 1) throw new IllegalArgumentException("nSamples < 1");
        Random rng = new Random(seed);

        int p = variables.size();
        double[][] cont = new double[nSamples][p];
        int[][] disc = new int[nSamples][p];
        for (int r = 0; r < nSamples; r++) Arrays.fill(disc[r], 0);

        List<Node> topo = dag.paths().getValidOrder(dag.getNodes(), true);
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) indexByName.put(variables.get(j).getName(), j);

        for (int r = 0; r < nSamples; r++) {
            for (Node child : topo) {
                Integer idx = indexByName.get(child.getName());
                if (idx == null) continue;
                Mechanism mech = mechanisms[idx];
                if (mech == null) continue;
                mech.generateOneRow(data, cont[r], disc[r], rng);
            }
        }

        return new SimResult(cont, disc, variables, dag);
    }

    private void addNodeReportContinuous(int childIdx, int[] parentIdx, int trainingRowsUsed,
                                         double mse, double residMean, double residSd) {
        String childName = variables.get(childIdx).getName();
        List<String> parents = new ArrayList<>();
        for (int pi : parentIdx) parents.add(variables.get(pi).getName());

        nodeReports.add(new NodeReport(
                childName,
                false,
                parents,
                trainingRowsUsed,
                mse,
                residMean,
                residSd,
                Double.NaN,
                0
        ));
    }

    private void addNodeReportDiscrete(int childIdx, int[] parentIdx, int trainingRowsUsed,
                                       double xent, int numLevels) {
        String childName = variables.get(childIdx).getName();
        List<String> parents = new ArrayList<>();
        for (int pi : parentIdx) parents.add(variables.get(pi).getName());

        nodeReports.add(new NodeReport(
                childName,
                true,
                parents,
                trainingRowsUsed,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                xent,
                numLevels
        ));
    }

    public static int safeGetInt(DataSet data, int row, int col) {
        try {
            return data.getInt(row, col);
        } catch (Throwable t) {
            double x = data.getDouble(row, col);
            if (!Double.isFinite(x)) return -1;
            return (int) Math.rint(x);
        }
    }

    public static final class Params {
        public int hidden = 16;
        public int epochs = 200;
        public double lr = 0.01;
        public double l2 = 1e-4;
        public int batchSize = 64;
        public long seed = 12345L;
        public int maxDiscreteLevels = 50;

        // NEW: preserve marginals at roots
        public boolean bootstrapRoots = true;

        // NEW: interpolate unconditional vs structural mechanism
        // 1.0 = fully structural (current behavior), 0.0 = ignore parents (unconditional baseline)
        public double lambdaParents = 1.0;

        // NEW: support warnings based on z-scores of continuous parents at simulation time
        public double zWarn1 = 4.0;
        public double zWarn2 = 6.0;

        // NEW: stratify residual bootstrap by discrete-parent signature (helps mixed)
        public boolean stratifyResidualsByDiscreteParents = true;

        // cap to avoid pathological number of strata maps in large categorical parent sets
        public int maxResidualStrata = 5000;
    }

    public static final class NodeReport {
        public final String node;
        public final boolean discreteChild;
        public final List<String> parents;
        public final int trainingRowsUsed;

        // Continuous-only
        public final double mseTrain;          // NaN if discrete child
        public final double residualMean;      // NaN if discrete child
        public final double residualSd;        // NaN if discrete child

        // Discrete-only
        public final double xentTrain;         // NaN if continuous child
        public final int numLevels;            // 0 if continuous child

        NodeReport(String node,
                   boolean discreteChild,
                   List<String> parents,
                   int trainingRowsUsed,
                   double mseTrain,
                   double residualMean,
                   double residualSd,
                   double xentTrain,
                   int numLevels) {
            this.node = node;
            this.discreteChild = discreteChild;
            this.parents = Collections.unmodifiableList(new ArrayList<>(parents));
            this.trainingRowsUsed = trainingRowsUsed;
            this.mseTrain = mseTrain;
            this.residualMean = residualMean;
            this.residualSd = residualSd;
            this.xentTrain = xentTrain;
            this.numLevels = numLevels;
        }
    }

    private static abstract class Mechanism {
        final int childIndex;
        final int[] parentIdx;         // indices in dataset variable list
        final InputEncoder encoder;    // encodes parent row -> feature vector

        Mechanism(int childIndex, int[] parentIdx, InputEncoder encoder) {
            this.childIndex = childIndex;
            this.parentIdx = parentIdx;
            this.encoder = encoder;
        }

        abstract void fit(DataSet data, Random rng, Params p);

        abstract void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng);
    }

    /**
     * Encodes mixed parents as:
     * [ zscored_cont_parent_1, ..., zscored_cont_parent_nc, onehot(disc_parent_1), ..., onehot(disc_parent_nd) ]
     * <p>
     * Discrete levels:
     * - if variable is DiscreteVariable, uses getNumCategories().
     * - else falls back to max observed + 1 (best-effort).
     */
    private static final class InputEncoder {
        final DataSet data;
        final int[] parentIdx;
        final boolean[] parentIsDisc;
        final int[] discLevels;       // per parent (0 if continuous)
        final int[] discOffset;       // start offset within feature vector for each discrete parent
        final int featureDim;

        // z-score stats for continuous parents
        final double[] mean;
        final double[] sd;
        final int[] contParents;      // indices of continuous parents (subset of parentIdx)
        final int[] discParents;      // indices of discrete parents (subset of parentIdx)

        InputEncoder(DataSet data, int[] parentIdx, int maxDiscreteLevels) {
            this.data = data;
            this.parentIdx = parentIdx.clone();

            int P = parentIdx.length;
            parentIsDisc = new boolean[P];
            discLevels = new int[P];
            discOffset = new int[P];

            List<Integer> cont = new ArrayList<>();
            List<Integer> disc = new ArrayList<>();

            for (int j = 0; j < P; j++) {
                Node v = data.getVariable(parentIdx[j]);
                boolean isDisc = (v instanceof DiscreteVariable);
                parentIsDisc[j] = isDisc;

                if (isDisc) {
                    int L = ((DiscreteVariable) v).getNumCategories();
                    if (L <= 0) L = 0;
                    if (L > maxDiscreteLevels)
                        throw new IllegalArgumentException("Discrete parent has too many levels: " + v.getName() + " L=" + L);
                    discLevels[j] = L;
                    disc.add(parentIdx[j]);
                } else {
                    discLevels[j] = 0;
                    cont.add(parentIdx[j]);
                }
            }

            contParents = cont.stream().mapToInt(Integer::intValue).toArray();
            discParents = disc.stream().mapToInt(Integer::intValue).toArray();

            mean = new double[contParents.length];
            sd = new double[contParents.length];

            // compute z-score stats for continuous parents (skip NaNs)
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double sum = 0.0, sum2 = 0.0;
                int n = 0;
                for (int r = 0; r < data.getNumRows(); r++) {
                    double x = data.getDouble(r, col);
                    if (!Double.isFinite(x)) continue;
                    sum += x;
                    sum2 += x * x;
                    n++;
                }
                double m = (n > 0) ? sum / n : 0.0;
                double var = (n > 1) ? (sum2 - n * m * m) / (n - 1.0) : 1.0;
                double s = Math.sqrt(Math.max(1e-12, var));
                mean[k] = m;
                sd[k] = s;
            }

            // compute offsets for discrete parents after the continuous block
            int dim = contParents.length;
            for (int j = 0; j < P; j++) {
                if (parentIsDisc[j]) {
                    discOffset[j] = dim;
                    dim += discLevels[j];
                } else {
                    discOffset[j] = -1;
                }
            }
            featureDim = dim;
        }

        void encodeRow(DataSet data, int row, double[] out) {
            Arrays.fill(out, 0.0);

            // continuous block (in fixed order contParents)
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double x = data.getDouble(row, col);
                // assume caller filtered missing rows; still be defensive
                if (!Double.isFinite(x)) x = mean[k];
                out[k] = (x - mean[k]) / sd[k];
            }

            // discrete blocks
            for (int j = 0; j < parentIdx.length; j++) {
                if (!parentIsDisc[j]) continue;
                int col = parentIdx[j];
                int L = discLevels[j];
                int off = discOffset[j];
                int v = safeGetInt(data, row, col);
                if (v < 0 || v >= L) continue;
                out[off + v] = 1.0;
            }
        }

        double[] encodeFromGenerated(double[] contRow, int[] discRow) {
            double[] out = new double[featureDim];
            // continuous
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double x = contRow[col];
                out[k] = (x - mean[k]) / sd[k];
            }
            // discrete
            for (int j = 0; j < parentIdx.length; j++) {
                if (!parentIsDisc[j]) continue;
                int col = parentIdx[j];
                int L = discLevels[j];
                int off = discOffset[j];
                int v = discRow[col];
                if (v < 0 || v >= L) continue;
                out[off + v] = 1.0;
            }
            return out;
        }

        double maxAbsZFromGenerated(double[] contRow) {
            double m = 0.0;
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double z = (contRow[col] - mean[k]) / sd[k];
                double az = Math.abs(z);
                if (az > m) m = az;
            }
            return m;
        }

        boolean hasAnyContinuousParents() {
            return contParents.length > 0;
        }

        boolean hasAnyDiscreteParents() {
            return discParents.length > 0;
        }

        int discreteSignatureFromGenerated(int[] discRow) {
            // hash all discrete parent values into one signature
            int h = 1;
            for (int j = 0; j < parentIdx.length; j++) {
                if (!parentIsDisc[j]) continue;
                int col = parentIdx[j];
                int v = discRow[col];
                h = 31 * h + v;
            }
            return h;
        }
    }

    private static final class TrainingRows {
        final int[] rows;
        final int n;

        TrainingRows(int[] rows) {
            this.rows = rows;
            this.n = rows.length;
        }

        static TrainingRows forNode(DataSet data, int child, int[] parents) {
            int N = data.getNumRows();
            int[] tmp = new int[N];
            int m = 0;

            outer:
            for (int r = 0; r < N; r++) {
                // child must be present
                if (isMissing(data, r, child)) continue;

                for (int p : parents) {
                    if (isMissing(data, r, p)) continue outer;
                }
                tmp[m++] = r;
            }
            return new TrainingRows(Arrays.copyOf(tmp, m));
        }

        private static boolean isMissing(DataSet data, int row, int col) {
            Node v = data.getVariable(col);
            if (v instanceof DiscreteVariable) {
                try {
                    int x = data.getInt(row, col);
                    return x == Integer.MIN_VALUE;
                } catch (Throwable t) {
                    double x = data.getDouble(row, col);
                    return !Double.isFinite(x);
                }
            } else {
                double x = data.getDouble(row, col);
                return !Double.isFinite(x);
            }
        }

        void shuffle(Random rng) {
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = rows[i];
                rows[i] = rows[j];
                rows[j] = t;
            }
        }
    }

    private static final class MlpRegressor {
        final int din, hidden;
        final double[][] W1;  // hidden x din
        final double[] b1;    // hidden
        final double[] W2;    // hidden -> scalar
        double b2;

        MlpRegressor(int din, int hidden, Random rng) {
            this.din = din;
            this.hidden = hidden;
            this.W1 = new double[hidden][din];
            this.b1 = new double[hidden];
            this.W2 = new double[hidden];
            initHe(rng);
        }

        void initHe(Random rng) {
            double s1 = Math.sqrt(2.0 / Math.max(1, din));
            for (int i = 0; i < hidden; i++) {
                for (int j = 0; j < din; j++) W1[i][j] = rng.nextGaussian() * s1;
                b1[i] = 0.0;
            }
            double s2 = Math.sqrt(2.0 / Math.max(1, hidden));
            for (int i = 0; i < hidden; i++) W2[i] = rng.nextGaussian() * s2;
            b2 = 0.0;
        }

        double predict(double[] x) {
            double[] h = new double[hidden];
            for (int i = 0; i < hidden; i++) {
                double z = b1[i];
                double[] wi = W1[i];
                for (int j = 0; j < din; j++) z += wi[j] * x[j];
                h[i] = Math.tanh(z);
            }
            double y = b2;
            for (int i = 0; i < hidden; i++) y += W2[i] * h[i];
            return y;
        }

        void sgdStep(DataSet data, int[] rows, int start, int end,
                     InputEncoder enc, int childCol, double lr, double l2) {

            double[] x = new double[din];
            double[] h = new double[hidden];
            double[] dh = new double[hidden];

            for (int t = start; t < end; t++) {
                int r = rows[t];
                enc.encodeRow(data, r, x);
                double y = data.getDouble(r, childCol);

                // forward
                for (int i = 0; i < hidden; i++) {
                    double z = b1[i];
                    double[] wi = W1[i];
                    for (int j = 0; j < din; j++) z += wi[j] * x[j];
                    h[i] = Math.tanh(z);
                }
                double yhat = b2;
                for (int i = 0; i < hidden; i++) yhat += W2[i] * h[i];

                double err = (yhat - y); // d/dyhat 0.5*(err^2) = err

                // grads output layer
                for (int i = 0; i < hidden; i++) {
                    double gW2 = err * h[i] + l2 * W2[i];
                    W2[i] -= lr * gW2;
                }
                b2 -= lr * err;

                // backprop to hidden
                for (int i = 0; i < hidden; i++) {
                    double d = err * W2[i];
                    // tanh' = 1 - h^2
                    dh[i] = d * (1.0 - h[i] * h[i]);
                }

                // grads W1, b1
                for (int i = 0; i < hidden; i++) {
                    double[] wi = W1[i];
                    double dhi = dh[i];
                    for (int j = 0; j < din; j++) {
                        double g = dhi * x[j] + l2 * wi[j];
                        wi[j] -= lr * g;
                    }
                    b1[i] -= lr * dhi;
                }
            }
        }

        void sgdStepOne(double[] x, double y, double lr, double l2) {
            double[] h = new double[hidden];
            double[] dh = new double[hidden];

            // forward
            for (int i = 0; i < hidden; i++) {
                double z = b1[i];
                double[] wi = W1[i];
                for (int j = 0; j < din; j++) z += wi[j] * x[j];
                h[i] = Math.tanh(z);
            }
            double yhat = b2;
            for (int i = 0; i < hidden; i++) yhat += W2[i] * h[i];

            double err = (yhat - y);

            // output layer
            for (int i = 0; i < hidden; i++) {
                double gW2 = err * h[i] + l2 * W2[i];
                W2[i] -= lr * gW2;
            }
            b2 -= lr * err;

            // backprop hidden
            for (int i = 0; i < hidden; i++) {
                double d = err * W2[i];
                dh[i] = d * (1.0 - h[i] * h[i]);
            }

            // W1, b1
            for (int i = 0; i < hidden; i++) {
                double[] wi = W1[i];
                double dhi = dh[i];
                for (int j = 0; j < din; j++) {
                    double g = dhi * x[j] + l2 * wi[j];
                    wi[j] -= lr * g;
                }
                b1[i] -= lr * dhi;
            }
        }
    }

    private static final class MlpSoftmaxClassifier {
        final int din, hidden, k;
        final double[][] W1; // hidden x din
        final double[] b1;   // hidden
        final double[][] W2; // k x hidden
        final double[] b2;   // k

        MlpSoftmaxClassifier(int din, int hidden, int k, Random rng) {
            this.din = din;
            this.hidden = hidden;
            this.k = k;
            this.W1 = new double[hidden][din];
            this.b1 = new double[hidden];
            this.W2 = new double[k][hidden];
            this.b2 = new double[k];
            initHe(rng);
        }

        private static double[] softmax(double[] z) {
            double[] out = new double[z.length];
            softmaxInto(z, out);
            return out;
        }

        private static double[] softmaxInto(double[] z, double[] out) {
            double max = z[0];
            for (int i = 1; i < z.length; i++) max = Math.max(max, z[i]);
            double sum = 0.0;
            for (int i = 0; i < z.length; i++) {
                out[i] = Math.exp(z[i] - max);
                sum += out[i];
            }
            double inv = 1.0 / Math.max(1e-300, sum);
            for (int i = 0; i < z.length; i++) out[i] *= inv;
            return out;
        }

        void initHe(Random rng) {
            double s1 = Math.sqrt(2.0 / Math.max(1, din));
            for (int i = 0; i < hidden; i++) {
                for (int j = 0; j < din; j++) W1[i][j] = rng.nextGaussian() * s1;
                b1[i] = 0.0;
            }
            double s2 = Math.sqrt(2.0 / Math.max(1, hidden));
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < hidden; j++) W2[i][j] = rng.nextGaussian() * s2;
                b2[i] = 0.0;
            }
        }

        double[] predictProbs(double[] x) {
            double[] h = new double[hidden];
            for (int i = 0; i < hidden; i++) {
                double z = b1[i];
                double[] wi = W1[i];
                for (int j = 0; j < din; j++) z += wi[j] * x[j];
                h[i] = Math.tanh(z);
            }
            double[] logits = new double[k];
            for (int c = 0; c < k; c++) {
                double z = b2[c];
                double[] wc = W2[c];
                for (int j = 0; j < hidden; j++) z += wc[j] * h[j];
                logits[c] = z;
            }
            return softmax(logits);
        }

        void sgdStep(DataSet data, int[] rows, int start, int end,
                     InputEncoder enc, int childCol, double lr, double l2) {

            double[] x = new double[din];
            double[] h = new double[hidden];
            double[] dh = new double[hidden];
            double[] logits = new double[k];
            double[] probs = new double[k];

            for (int t = start; t < end; t++) {
                int r = rows[t];
                enc.encodeRow(data, r, x);

                int y = safeGetInt(data, r, childCol);
                if (y < 0 || y >= k) continue;

                // forward hidden
                for (int i = 0; i < hidden; i++) {
                    double z = b1[i];
                    double[] wi = W1[i];
                    for (int j = 0; j < din; j++) z += wi[j] * x[j];
                    h[i] = Math.tanh(z);
                }

                // logits
                for (int c = 0; c < k; c++) {
                    double z = b2[c];
                    double[] wc = W2[c];
                    for (int j = 0; j < hidden; j++) z += wc[j] * h[j];
                    logits[c] = z;
                }

                // softmax
                System.arraycopy(softmaxInto(logits, probs), 0, probs, 0, k);

                // gradient on logits: (p - y_onehot)
                probs[y] -= 1.0;

                // update W2, b2
                for (int c = 0; c < k; c++) {
                    double gc = probs[c];
                    double[] wc = W2[c];
                    for (int j = 0; j < hidden; j++) {
                        double g = gc * h[j] + l2 * wc[j];
                        wc[j] -= lr * g;
                    }
                    b2[c] -= lr * gc;
                }

                // backprop to hidden: dh = (W2^T * probs) ⊙ tanh'
                Arrays.fill(dh, 0.0);
                for (int j = 0; j < hidden; j++) {
                    double s = 0.0;
                    for (int c = 0; c < k; c++) s += W2[c][j] * probs[c];
                    dh[j] = s * (1.0 - h[j] * h[j]);
                }

                // update W1, b1
                for (int i = 0; i < hidden; i++) {
                    double[] wi = W1[i];
                    double dhi = dh[i];
                    for (int j = 0; j < din; j++) {
                        double g = dhi * x[j] + l2 * wi[j];
                        wi[j] -= lr * g;
                    }
                    b1[i] -= lr * dhi;
                }
            }
        }
    }

    // Drop-in replacement for TrainedDagSimulator.SimResult
    public static final class SimResult {
        public final double[][] cont;      // continuous values for all vars (discrete columns also exist but meaningless here)
        public final int[][] disc;         // discrete codes for all vars (continuous columns are left as 0)
        public final List<Node> variables;
        public final Graph trueDag;

        // --- NEW: optional simulation diagnostics (safe defaults) ---
        public final long nSamples;
        public final long[] zExceedWarn1;   // per variable index
        public final long[] zExceedWarn2;   // per variable index
        public final double zWarn1;
        public final double zWarn2;

        // Backwards-compatible ctor (same shape as your current code expects)
        SimResult(double[][] cont, int[][] disc, List<Node> variables, Graph trueDag) {
            this(cont, disc, variables, trueDag,
                    null, null,
                    cont != null ? cont.length : 0L,
                    Double.NaN, Double.NaN);
        }

        // Extended ctor (use if you track warnings in simulate())
        SimResult(double[][] cont,
                  int[][] disc,
                  List<Node> variables,
                  Graph trueDag,
                  long[] zExceedWarn1,
                  long[] zExceedWarn2,
                  long nSamples,
                  double zWarn1,
                  double zWarn2) {
            this.cont = cont;
            this.disc = disc;
            this.variables = new ArrayList<>(variables);
            this.trueDag = trueDag;

            this.nSamples = nSamples;

            int p = this.variables.size();
            this.zExceedWarn1 = (zExceedWarn1 == null) ? new long[p] : zExceedWarn1;
            this.zExceedWarn2 = (zExceedWarn2 == null) ? new long[p] : zExceedWarn2;

            this.zWarn1 = zWarn1;
            this.zWarn2 = zWarn2;
        }

        public void writeSimulatedDataTsv(File outFile) throws IOException {
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
                // header
                for (int j = 0; j < variables.size(); j++) {
                    if (j > 0) out.print('\t');
                    out.print(variables.get(j).getName());
                }
                out.println();

                int n = cont.length;
                int p = variables.size();
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < p; j++) {
                        if (j > 0) out.print('\t');
                        Node v = variables.get(j);
                        if (v instanceof DiscreteVariable) out.print(disc[i][j]);
                        else out.print(cont[i][j]);
                    }
                    out.println();
                }
            }
        }

        public void writeTrueGraphTxt(File outFile) throws IOException {
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
                for (Edge e : trueDag.getEdges()) {
                    if (e.isDirected()) {
                        out.println(e.getNode1().getName() + " -> " + e.getNode2().getName());
                    }
                }
            }
        }

        // NEW: optional simulation report. Safe even if warnings are NaN / not tracked.
        public String getSimReportText(double lambdaParents, boolean bootstrapRoots) {
            StringBuilder sb = new StringBuilder();
            sb.append("TrainedDagSimulator simulation report\n");
            sb.append("nSamples=").append(nSamples).append("\n");
            sb.append("lambdaParents=").append(lambdaParents).append("\n");
            sb.append("bootstrapRoots=").append(bootstrapRoots).append("\n");

            if (Double.isFinite(zWarn1) && Double.isFinite(zWarn2)) {
                sb.append("zWarn1=").append(zWarn1).append(" zWarn2=").append(zWarn2).append("\n\n");
                sb.append("Support warnings (fraction of rows where max|z(parent)| exceeds thresholds)\n");

                long denom = Math.max(1L, nSamples);
                for (int j = 0; j < variables.size(); j++) {
                    double f1 = zExceedWarn1[j] / (double) denom;
                    double f2 = zExceedWarn2[j] / (double) denom;
                    sb.append(variables.get(j).getName())
                            .append(": >").append(zWarn1).append("=").append(f1)
                            .append("  >").append(zWarn2).append("=").append(f2)
                            .append("\n");
                }
            } else {
                sb.append("supportWarnings=not_tracked\n");
            }

            return sb.toString();
        }

        // Convenience if you want a file
        public void writeSimReportTxt(File outFile, double lambdaParents, boolean bootstrapRoots) throws IOException {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                w.write(getSimReportText(lambdaParents, bootstrapRoots));
            }
        }
    }

    private final class ContinuousMechanism extends Mechanism {
        // Stage 1: mean model mu(x)
        final MlpRegressor netMean;

        // Stage 2: general-noise generator g(x, e)
        final MlpRegressor netGNM;

        double baseMean;

        // residual pool(s) for e
        double[] residuals;                 // global pool
        Map<Integer, double[]> residualsBySig; // optional stratified pool

        // for training g(x,e): residual by original row index (so shuffles are safe)
        double[] residByRow;

        // standardization of residual input to g
        double residMean;
        double residSd;

        ContinuousMechanism(int childIndex, int[] parentIdx, InputEncoder encoder, int hidden, Random rng) {
            super(childIndex, parentIdx, encoder);
            this.netMean = new MlpRegressor(encoder.featureDim, hidden, rng);
            this.netGNM = new MlpRegressor(encoder.featureDim + 1, hidden, rng); // +1 for e
        }

        @Override
        void fit(DataSet data, Random rng, Params p) {
            TrainingRows tr = TrainingRows.forNode(data, childIndex, parentIdx);

            baseMean = computeChildMean(data, childIndex, tr);

            if (tr.n < 5) {
                residuals = new double[]{0.0};
                residByRow = new double[data.getNumRows()];
                Arrays.fill(residByRow, Double.NaN);
                residMean = 0.0;
                residSd = 1.0;

                addNodeReportContinuous(childIndex, parentIdx, tr.n,
                        Double.NaN, Double.NaN, Double.NaN);
                return;
            }

            // -------------------------
            // Stage 1: fit mu(x)
            // -------------------------
            for (int ep = 0; ep < p.epochs; ep++) {
                tr.shuffle(rng);
                for (int start = 0; start < tr.n; start += p.batchSize) {
                    int end = Math.min(tr.n, start + p.batchSize);
                    netMean.sgdStep(data, tr.rows, start, end, encoder, childIndex, p.lr, p.l2);
                }
            }

            // -------------------------
            // Compute residuals e = y - mu(x)
            // -------------------------
            residuals = new double[tr.n];
            residByRow = new double[data.getNumRows()];
            Arrays.fill(residByRow, Double.NaN);

            double[] x = new double[encoder.featureDim];

            double sse = 0.0;
            double sumE = 0.0, sumE2 = 0.0;

            boolean doStrata = p.stratifyResidualsByDiscreteParents && encoder.hasAnyDiscreteParents();
            Map<Integer, ArrayList<Double>> tmpStrata = doStrata ? new HashMap<>() : null;

            for (int i = 0; i < tr.n; i++) {
                int row = tr.rows[i];
                encoder.encodeRow(data, row, x);
                double yhat = netMean.predict(x);
                double y = data.getDouble(row, childIndex);
                double e = y - yhat;

                residuals[i] = e;
                residByRow[row] = e;

                sse += e * e;
                sumE += e;
                sumE2 += e * e;

                if (doStrata) {
                    int sig = discreteSignatureFromDataRow(data, row, encoder);
                    tmpStrata.computeIfAbsent(sig, k -> new ArrayList<>()).add(e);
                }
            }

            // residual stats for feeding e into g
            residMean = sumE / tr.n;
            double varE = (tr.n > 1) ? (sumE2 - tr.n * residMean * residMean) / (tr.n - 1.0) : 1.0;
            residSd = Math.sqrt(Math.max(1e-12, varE));

            // optional stratified pools
            if (doStrata && tmpStrata.size() <= p.maxResidualStrata) {
                residualsBySig = new HashMap<>();
                for (Map.Entry<Integer, ArrayList<Double>> ent : tmpStrata.entrySet()) {
                    ArrayList<Double> list = ent.getValue();
                    double[] arr = new double[list.size()];
                    for (int k = 0; k < arr.length; k++) arr[k] = list.get(k);
                    residualsBySig.put(ent.getKey(), arr);
                }
            } else {
                residualsBySig = null;
            }

            // report (keep the same report fields you already expose)
            double mse = sse / tr.n;
            double mean = residMean;
            double sd = residSd;
            addNodeReportContinuous(childIndex, parentIdx, tr.n, mse, mean, sd);

            // -------------------------
            // Stage 2: fit g(x, e) to predict y
            //   input = [encodedParents, eStd]
            // -------------------------
            for (int ep = 0; ep < p.epochs; ep++) {
                tr.shuffle(rng);
                for (int start = 0; start < tr.n; start += p.batchSize) {
                    int end = Math.min(tr.n, start + p.batchSize);

                    for (int t = start; t < end; t++) {
                        int row = tr.rows[t];

                        encoder.encodeRow(data, row, x);
                        double y = data.getDouble(row, childIndex);

                        double e = residByRow[row];
                        if (!Double.isFinite(e)) e = 0.0;
                        double eStd = (e - residMean) / residSd;

                        // build extended feature vector [x, eStd]
                        double[] xe = new double[encoder.featureDim + 1];
                        System.arraycopy(x, 0, xe, 0, encoder.featureDim);
                        xe[encoder.featureDim] = eStd;

                        netGNM.sgdStepOne(xe, y, p.lr, p.l2);
                    }
                }
            }
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            // parent features from already-generated parents
            double[] x = encoder.encodeFromGenerated(contRow, discRow);

            // optional “turn down” parent influence
            double lam = clamp01(params.lambdaParents);
            if (lam != 1.0) {
                for (int i = 0; i < x.length; i++) x[i] *= lam;
            }

            // sample e from pools
            double e;
            if (residualsBySig != null) {
                int sig = encoder.discreteSignatureFromGenerated(discRow);
                double[] pool = residualsBySig.get(sig);
                if (pool != null && pool.length > 0) e = pool[rng.nextInt(pool.length)];
                else e = residuals[rng.nextInt(residuals.length)];
            } else {
                e = residuals[rng.nextInt(residuals.length)];
            }

            double eStd = (e - residMean) / residSd;

            double[] xe = new double[x.length + 1];
            System.arraycopy(x, 0, xe, 0, x.length);
            xe[x.length] = eStd;

            double yGen = netGNM.predict(xe);

            // If you want a tiny anchor to the unconditional mean (rarely needed), you can blend:
            // yGen = (1.0 - lam) * baseMean + lam * yGen;
            contRow[childIndex] = yGen;
        }

        private double computeChildMean(DataSet data, int childIndex, TrainingRows tr) {
            double sum = 0.0;
            int used = 0;
            if (tr.n > 0) {
                for (int i = 0; i < tr.n; i++) {
                    double y = data.getDouble(tr.rows[i], childIndex);
                    if (!Double.isFinite(y)) continue;
                    sum += y;
                    used++;
                }
            } else {
                for (int r = 0; r < data.getNumRows(); r++) {
                    double y = data.getDouble(r, childIndex);
                    if (!Double.isFinite(y)) continue;
                    sum += y;
                    used++;
                }
            }
            return (used > 0) ? (sum / used) : 0.0;
        }

        private int discreteSignatureFromDataRow(DataSet data, int row, InputEncoder enc) {
            int h = 1;
            for (int j = 0; j < enc.parentIdx.length; j++) {
                if (!enc.parentIsDisc[j]) continue;
                int col = enc.parentIdx[j];
                int v = TrainedDagSimulatorGNM.safeGetInt(data, row, col);
                h = 31 * h + v;
            }
            return h;
        }
    }

    private final class DiscreteMechanism extends Mechanism {
        final MlpSoftmaxClassifier net;
        final int numLevels;

        double[] baseProbs; // empirical p(y) over training rows

        DiscreteMechanism(int childIndex, int[] parentIdx, InputEncoder encoder, int hidden, int numLevels, Random rng) {
            super(childIndex, parentIdx, encoder);
            this.numLevels = numLevels;
            this.net = new MlpSoftmaxClassifier(encoder.featureDim, hidden, numLevels, rng);
        }

        @Override
        void fit(DataSet data, Random rng, Params p) {
            TrainingRows tr = TrainingRows.forNode(data, childIndex, parentIdx);

            // baseline probs from training rows
            baseProbs = empiricalProbs(data, tr, childIndex, numLevels);

            if (tr.n < 5) {
                addNodeReportDiscrete(childIndex, parentIdx, tr.n, Double.NaN, numLevels);
                return;
            }

            for (int ep = 0; ep < p.epochs; ep++) {
                tr.shuffle(rng);
                for (int start = 0; start < tr.n; start += p.batchSize) {
                    int end = Math.min(tr.n, start + p.batchSize);
                    net.sgdStep(data, tr.rows, start, end, encoder, childIndex, p.lr, p.l2);
                }
            }

            // Cross-entropy on training rows (net only)
            double xent = 0.0;
            int used = 0;
            double[] x = new double[encoder.featureDim];

            for (int i = 0; i < tr.n; i++) {
                int row = tr.rows[i];
                encoder.encodeRow(data, row, x);
                int y = safeGetInt(data, row, childIndex);
                if (y < 0 || y >= numLevels) continue;

                double[] probs = net.predictProbs(x);
                xent += -Math.log(Math.max(1e-300, probs[y]));
                used++;
            }

            double xentAvg = (used > 0) ? (xent / used) : Double.NaN;
            addNodeReportDiscrete(childIndex, parentIdx, tr.n, xentAvg, numLevels);
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            double[] x = encoder.encodeFromGenerated(contRow, discRow);
            double[] pNet = net.predictProbs(x);

            double lam = clamp01(params.lambdaParents);

            // p = (1-lam)*pBase + lam*pNet
            double[] pMix = new double[numLevels];
            double sum = 0.0;
            for (int k = 0; k < numLevels; k++) {
                double v = (1.0 - lam) * baseProbs[k] + lam * pNet[k];
                pMix[k] = v;
                sum += v;
            }
            if (sum > 0) {
                double inv = 1.0 / sum;
                for (int k = 0; k < numLevels; k++) pMix[k] *= inv;
            } else {
                // fallback uniform
                for (int k = 0; k < numLevels; k++) pMix[k] = 1.0 / numLevels;
            }

            discRow[childIndex] = sampleCategorical(pMix, rng);
        }

        private double[] empiricalProbs(DataSet data, TrainingRows tr, int col, int K) {
            double[] counts = new double[K];
            int used = 0;
            if (tr.n > 0) {
                for (int i = 0; i < tr.n; i++) {
                    int y = safeGetInt(data, tr.rows[i], col);
                    if (y < 0 || y >= K) continue;
                    counts[y] += 1.0;
                    used++;
                }
            } else {
                for (int r = 0; r < data.getNumRows(); r++) {
                    int y = safeGetInt(data, r, col);
                    if (y < 0 || y >= K) continue;
                    counts[y] += 1.0;
                    used++;
                }
            }
            double[] p = new double[K];
            if (used > 0) {
                for (int k = 0; k < K; k++) p[k] = counts[k] / used;
            } else {
                for (int k = 0; k < K; k++) p[k] = 1.0 / K;
            }
            return p;
        }
    }

    // Root: continuous variable sampled by bootstrap (preserves histogram)
    private final class RootContinuousMechanism extends Mechanism {
        private double[] pool;      // observed values (finite)
        private double baseMean;    // fallback

        RootContinuousMechanism(int childIndex) {
            super(childIndex, new int[0], new InputEncoder(data, new int[0], params.maxDiscreteLevels));
        }

        @Override
        void fit(DataSet data, Random rng, Params p) {
            // collect finite values
            int N = data.getNumRows();
            double[] tmp = new double[N];
            int m = 0;
            double sum = 0.0;
            int used = 0;

            for (int r = 0; r < N; r++) {
                double y = data.getDouble(r, childIndex);
                if (!Double.isFinite(y)) continue;
                tmp[m++] = y;
                sum += y;
                used++;
            }
            this.pool = (m > 0) ? Arrays.copyOf(tmp, m) : new double[]{0.0};
            this.baseMean = (used > 0) ? (sum / used) : 0.0;

            // report as continuous with empty parents
            addNodeReportContinuous(childIndex, new int[0], used,
                    Double.NaN, Double.NaN, Double.NaN);
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            if (!params.bootstrapRoots) {
                contRow[childIndex] = baseMean;
                return;
            }
            contRow[childIndex] = pool[rng.nextInt(pool.length)];
        }
    }

    // Root: discrete variable sampled from empirical frequencies (preserves bar plot)
    private final class RootDiscreteMechanism extends Mechanism {
        private final int numLevels;
        private double[] probs; // empirical

        RootDiscreteMechanism(int childIndex, int numLevels) {
            super(childIndex, new int[0], new InputEncoder(data, new int[0], params.maxDiscreteLevels));
            this.numLevels = numLevels;
        }

        @Override
        void fit(DataSet data, Random rng, Params p) {
            int N = data.getNumRows();
            double[] counts = new double[numLevels];
            int used = 0;

            for (int r = 0; r < N; r++) {
                int y = safeGetInt(data, r, childIndex);
                if (y < 0 || y >= numLevels) continue;
                counts[y] += 1.0;
                used++;
            }

            probs = new double[numLevels];
            if (used > 0) {
                for (int k = 0; k < numLevels; k++) probs[k] = counts[k] / used;
            } else {
                // fallback uniform
                for (int k = 0; k < numLevels; k++) probs[k] = 1.0 / numLevels;
            }

            addNodeReportDiscrete(childIndex, new int[0], used, Double.NaN, numLevels);
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            if (!params.bootstrapRoots) {
                discRow[childIndex] = argmax(probs);
                return;
            }
            discRow[childIndex] = sampleCategorical(probs, rng);
        }
    }
}