// File: edu/cmu/tetrad/sem/TrainedDagSimulator.java
package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * <h2>Trained DAG simulator (ANM): learn local mechanisms from a real dataset and resimulate</h2>
 *
 * <p>This class is a “train-then-resimulate” simulator: given an observed {@link DataSet} and a
 * user-supplied acyclic {@link Graph} (treated as a DAG), it learns a local conditional mechanism
 * for each node and then generates new samples by running the DAG forward in topological order.</p>
 *
 * <p><b>Primary motivation.</b> This simulator is designed to demonstrate that (i) marginal and
 * pairwise plots can often be made to look “realistic” under substantially different DAGs when the
 * mechanisms are flexible and noise is bootstrapped, yet (ii) likelihood/score-based evaluation
 * should systematically penalize fitting the wrong graph to data generated from the learned
 * mechanisms. In other words: visual realism is cheap; causal correctness should still matter for
 * scoring.</p>
 *
 * <h3>Model family (ANM)</h3>
 * <p>For each node {@code Y} with parents {@code Pa(Y)}, the fitted mechanism is an additive-noise model</p>
 * <pre>
 *   Y = f(Pa(Y)) + e
 * </pre>
 * <p>where {@code f} is a small neural regressor/classifier and {@code e} is drawn by bootstrap from
 * training residuals (continuous) or via sampling from fitted class probabilities (discrete).</p>
 *
 * <h3>Mixed data support</h3>
 * <ul>
 *   <li><b>Continuous child:</b> 1-hidden-layer MLP regressor trained by SGD; during simulation,
 *       generate {@code yHat = f(x)} and add a bootstrapped residual {@code e} from the training rows
 *       used for that node.</li>
 *   <li><b>Discrete child:</b> 1-hidden-layer MLP softmax classifier; during simulation, sample a
 *       category from the predicted class probabilities.</li>
 * </ul>
 *
 * <h3>Parent encoding</h3>
 * <ul>
 *   <li><b>Continuous parents</b> enter as z-scored scalars (mean/sd computed from available rows).</li>
 *   <li><b>Discrete parents</b> enter as one-hot blocks (levels inferred from {@link DiscreteVariable}
 *       metadata, with a configurable cap).</li>
 * </ul>
 *
 * <h3>Missingness</h3>
 * <p>Training is done node-by-node using only rows where the child and all of its parents are observed
 * (rows with any missing among {@code {Y} ∪ Pa(Y)} are skipped for that node). Simulation produces
 * complete samples given the learned mechanisms.</p>
 *
 * <h3>Notes and limitations</h3>
 * <ul>
 *   <li>This is intentionally a lightweight baseline (single hidden layer, tanh activation, SGD, L2 decay),
 *       aimed at robustness and ease of packaging rather than state-of-the-art prediction accuracy.</li>
 *   <li>The quality of the resimulation depends on the supplied DAG and on support coverage of parent values;
 *       extrapolation may occur when simulated parent configurations fall outside the training support.</li>
 * </ul>
 */
public final class TrainedDagSimulatorANM {

    // -------------------- configuration --------------------

    /**
     * List of NodeReport objects containing detailed simulation results for each node.
     */
    private final List<NodeReport> nodeReports = new ArrayList<>();

    // -------------------- fit reporting --------------------
    /**
     * Training dataset.
     */
    private final DataSet data;
    /**
     * Directed Acyclic Graph (DAG) representing the causal structure.
     */
    private final Graph dag;
    /**
     * Simulation parameters.
     */
    private final Params params;
    /**
     * List of variables
     */
    private final List<Node> variables;
    /**
     * Array indicating whether each variable is discrete or not.
     */
    private final boolean[] isDiscrete;

    // -------------------- trained model per node --------------------
    /**
     * Array of trained mechanisms for each variable.
     */
    private final Mechanism[] mechanisms;

    /**
     * Constructs a TrainedDagSimulatorANM
     *
     * @param data   Training dataset
     * @param dag    Directed Acyclic Graph (DAG) representing the causal structure
     * @param params Simulation parameters
     */
    public TrainedDagSimulatorANM(DataSet data, Graph dag, Params params) {
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
     * Safely retrieves an integer value from a DataSet, handling missing or non-integer values gracefully.
     *
     * @param data DataSet containing the data
     * @param row  Row index
     * @param col  Column index
     * @return Integer value or -1 if missing or non-integer
     */
    public static int safeGetInt(DataSet data, int row, int col) {
        try {
            return data.getInt(row, col);
        } catch (Throwable t) {
            double x = data.getDouble(row, col);
            if (!Double.isFinite(x)) return -1;     // sentinel for missing/bad
            return (int) FastMath.rint(x);              // best-effort convert
        }
    }

    // -------------------- training row selection per node --------------------

    /**
     * Human-readable report (one block per node).
     *
     * @return String containing the fit report text.
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

    // -------------------- tiny MLPs (1 hidden layer) --------------------

    /**
     * Structured per-node reports.
     *
     * @return List of NodeReport objects containing detailed simulation results for each node
     */
    public List<NodeReport> getNodeReports() {
        return Collections.unmodifiableList(nodeReports);
    }

    /**
     * Writes a human-readable fit report to a specified text file.
     * The fit report provides per-node details on the training process
     * and its results, including error metrics and parent information.
     *
     * @param file The output file where the fit report will be written.
     * @throws IOException If an I/O error occurs during writing.
     */
    public void writeFitReportTxt(File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(getFitReportText());
        }
    }

    // -------------------- main simulator object --------------------

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

    /**
     * Simulates the DAG with the given number of samples and random seed.
     *
     * @param nSamples Number of samples to generate
     * @return SimResult object containing simulation results
     * @throws IllegalArgumentException if nSamples is less than 1
     */
    public SimResult simulate(int nSamples) {
        return simulate(nSamples, params.seed ^ 0x9E3779B97F4A7C15L);
    }

    /**
     * Simulates the DAG with the given number of samples and random seed.
     *
     * @param nSamples Number of samples to generate
     * @param seed     Random seed for the simulation
     * @return SimResult object containing simulation results
     * @throws IllegalArgumentException if nSamples is less than 1
     */
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

    /**
     * Defines various configuration parameters for simulations involving
     * directed acyclic graphs (DAGs). These parameters control aspects such
     * as network structure, optimization settings, and runtime behavior of
     * the simulation.
     *
     * The class includes both general configuration options and specific
     * features designed to handle structural and statistical properties of
     * the modeled system.
     *
     * Fields of interest include learning rate, regularization terms, batch
     * size, and parameters enabling advanced simulation features like
     * bootstrapping, interpolation, and stratification.
     */
    public static final class Params {
        /**
         * Default constructor for the Params class.
         * Initializes an instance of the Params object with default values.
         */
        public Params() { }

        /**
         * Represents the number of hidden units or nodes in a layer or model.
         * This variable is commonly used in machine learning or simulation
         * configurations to specify the dimensionality of hidden layers
         * within a model architecture.
         */
        public int hidden = 16;
        /**
         * Specifies the number of training epochs to be used in simulations or
         * iterative optimization tasks. The value determines how many complete
         * passes are made over the entire dataset during the training process.
         *
         * A higher number of epochs allows the model to iterate more on the data,
         * potentially improving learning outcomes but also increasing computation
         * time. Conversely, a lower number of epochs can reduce runtime but may
         * result in underfitting.
         *
         * Ideal values depend on the complexity of the problem, the size of the
         * dataset, and the chosen optimization algorithm.
         */
        public int epochs = 200;
        /**
         * Represents the learning rate used in optimization algorithms during model training.
         * The learning rate determines the step size at each iteration while moving toward
         * a minimum of the loss function.
         *
         * A smaller learning rate ensures stable convergence at the cost of slower training,
         * while a larger learning rate can speed up the process but may risk overshooting
         * the optimal value or lead to instability in training.
         *
         * Typical values range between 0.0001 and 0.1, depending on the problem complexity
         * and the chosen optimization algorithm.
         */
        public double lr = 0.01;
        /**
         * Represents the regularization strength applied during model training.
         * Higher values increase regularization, which can prevent overfitting but may
         * also reduce model expressiveness.
         */
        public double l2 = 1e-4;
        /**
         * The number of samples to process in a single batch during training or computation.
         * This value is often used to determine the subset of data processed at one time,
         * which can impact both computational efficiency and memory usage.
         */
        public int batchSize = 64;
        /**
         * Seed for the random number generator, used to ensure reproducibility of results.
         */
        public long seed = 12345L;
        /**
         * Specifies the maximum number of discrete levels that a variable or
         * feature can have within the context where this parameter is used.
         * It acts as an upper limit to reduce computational complexity
         * when processing or analyzing discrete data.
         */
        public int maxDiscreteLevels = 50;

        // NEW: preserve marginals at roots
        /**
         * A boolean variable that indicates whether the bootstrap process should include root nodes.
         * If set to true, the bootstrap operation will incorporate root nodes into its process;
         * if false, it will exclude them.
         */
        public boolean bootstrapRoots = true;

        // NEW: interpolate unconditional vs structural mechanism
        // 1.0 = fully structural (current behavior), 0.0 = ignore parents (unconditional baseline)
        /**
         * A parameter that controls the interpolation between unconditional and structural mechanisms
         * during simulation. A value of 1.0 means fully structural, while 0.0 means ignoring parents
         * for unconditional simulation.
         */
        public double lambdaParents = 1.0;

        // NEW: support warnings based on z-scores of continuous parents at simulation time
        /**
         * Thresholds for warning levels based on z-scores of continuous parents during simulation.
         * These values are used to trigger warnings when the z-score of a parent variable exceeds
         * the specified threshold.
         */
        public double zWarn1 = 4.0;
        /**
         * Thresholds for warning levels based on z-scores of continuous parents during simulation.
         * These values are used to trigger warnings when the z-score of a parent variable exceeds
         * the specified threshold.
         */
        public double zWarn2 = 6.0;

        // NEW: stratify residual bootstrap by discrete-parent signature (helps mixed)
        /**
         * A boolean variable that determines whether to stratify residual bootstrap by discrete-parent signature.
         * This can help improve simulation results in mixed data scenarios.
         */
        public boolean stratifyResidualsByDiscreteParents = true;

        // cap to avoid pathological number of strata maps in large categorical parent sets
        /**
         * Maximum number of residual strata to consider during simulation. This parameter helps
         * manage computational complexity by limiting the number of strata maps, especially
         * in scenarios with large categorical parent sets.
         */
        public int maxResidualStrata = 5000;
    }

    /**
     * Class representing a report for a node in the simulation results.
     */
    public static final class NodeReport {
        /**
         * Name of the node.
         */
        public final String node;
        /**
         * Indicates whether the node is a discrete child.
         */
        public final boolean discreteChild;
        /**
         * A list of parent nodes that influence the current node in the simulation report.
         * Each entry in the list represents the name of a parent node.
         * This list is immutable and reflects the structural dependencies in the simulation model.
         */
        public final List<String> parents;
        /**
         * Number of rows used for training the node in the simulation.
         */
        public final int trainingRowsUsed;

        // Continuous-only
        /**
         * Mean Squared Error (MSE) for the training data associated with the node.
         * This value is only applicable for nodes representing continuous variables.
         * If the node is a discrete child, this value is set to NaN.
         */
        public final double mseTrain;          // NaN if discrete child
        /**
         * Mean Squared Error (MSE) for the residual data associated with the node.
         * This value is only applicable for nodes representing continuous variables.
         * If the node is a discrete child, this value is set to NaN.
         */
        public final double residualMean;      // NaN if discrete child
        /**
         * Standard Deviation of the residual data associated with the node.
         * This value is only applicable for nodes representing continuous variables.
         * If the node is a discrete child, this value is set to NaN.
         */
        public final double residualSd;        // NaN if discrete child

        // Discrete-only
        /**
         * Cross-Entropy (XENT) for the training data associated with the node.
         * This value is only applicable for nodes representing discrete variables.
         * If the node is a continuous child, this value is set to NaN.
         */
        public final double xentTrain;         // NaN if continuous child
        /**
         * Number of discrete levels for the node.
         * This value is only applicable for nodes representing discrete variables.
         * If the node is a continuous child, this value is set to 0.
         */
        public final int numLevels;            // 0 if continuous child

        /**
         * Constructs a NodeReport instance with the specified parameters.
         *
         * @param node            The name of the node being reported.
         * @param discreteChild   Specifies whether the node has discrete child relationships.
         * @param parents         A list of parent nodes associated with the node.
         * @param trainingRowsUsed The number of training rows utilized for this node's evaluation.
         * @param mseTrain        The mean squared error (MSE) during training for this node.
         * @param residualMean    The mean of residuals from this node's training process.
         * @param residualSd      The standard deviation of residuals from the training process.
         * @param xentTrain       The cross-entropy loss during training for this node.
         * @param numLevels       The number of levels associated with this node's hierarchy or structure.
         */
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
                double s = FastMath.sqrt(FastMath.max(1e-12, var));
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
                double az = FastMath.abs(z);
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
            double s1 = FastMath.sqrt(2.0 / FastMath.max(1, din));
            for (int i = 0; i < hidden; i++) {
                for (int j = 0; j < din; j++) W1[i][j] = rng.nextGaussian() * s1;
                b1[i] = 0.0;
            }
            double s2 = FastMath.sqrt(2.0 / FastMath.max(1, hidden));
            for (int i = 0; i < hidden; i++) W2[i] = rng.nextGaussian() * s2;
            b2 = 0.0;
        }

        double predict(double[] x) {
            double[] h = new double[hidden];
            for (int i = 0; i < hidden; i++) {
                double z = b1[i];
                double[] wi = W1[i];
                for (int j = 0; j < din; j++) z += wi[j] * x[j];
                h[i] = FastMath.tanh(z);
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
                    h[i] = FastMath.tanh(z);
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
            for (int i = 1; i < z.length; i++) max = FastMath.max(max, z[i]);
            double sum = 0.0;
            for (int i = 0; i < z.length; i++) {
                out[i] = FastMath.exp(z[i] - max);
                sum += out[i];
            }
            double inv = 1.0 / FastMath.max(1e-300, sum);
            for (int i = 0; i < z.length; i++) out[i] *= inv;
            return out;
        }

        void initHe(Random rng) {
            double s1 = FastMath.sqrt(2.0 / FastMath.max(1, din));
            for (int i = 0; i < hidden; i++) {
                for (int j = 0; j < din; j++) W1[i][j] = rng.nextGaussian() * s1;
                b1[i] = 0.0;
            }
            double s2 = FastMath.sqrt(2.0 / FastMath.max(1, hidden));
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
                h[i] = FastMath.tanh(z);
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
                    h[i] = FastMath.tanh(z);
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

    /**
     * Represents the result of a simulation, encapsulating the simulation data, variables,
     * the true Directed Acyclic Graph (DAG), and optional diagnostic information.
     *
     * The class contains both continuous and discrete data matrices, as well as information
     * about any warnings or thresholds exceeded during the simulation process.
     * It also provides a method to generate a formatted report summarizing the results.
     */
    public static final class SimResult {
        /**
         * A container for simulation results, including continuous and discrete values, variables, and true DAG.
         */
        public final double[][] cont;      // continuous values for all vars (discrete columns also exist but meaningless here)
        /**
         * A 2D array representing the discrete codes for all variables in the simulation.
         * Each element in the array corresponds to the discrete encoding for a specific variable.
         * Continuous variables are represented with a value of 0 in their respective columns.
         * This array is used to store the categorical or discrete values relevant for the simulation.
         */
        public final int[][] disc;         // discrete codes for all vars (continuous columns are left as 0)
        /**
         * A list of variables involved in the simulation, including both continuous and discrete variables.
         */
        public final List<Node> variables;
        /**
         * Represents the true Directed Acyclic Graph (DAG) associated with the simulation results.
         * This graph encodes the underlying causal structure presumed to generate the data.
         * It is typically used for validation purposes or to compare to the inferred structure.
         */
        public final Graph trueDag;

        // --- NEW: optional simulation diagnostics (safe defaults) ---
        /**
         * Represents the total number of samples generated during a simulation.
         * This value is fundamental to interpreting the results of the simulation,
         * as it determines the dataset size used for computational procedures.
         * <p>
         * It is typically used to assess the scale of the simulation and may
         * influence statistical measures computed from the results.
         */
        public final long nSamples;
        /**
         * Array storing the count of exceedance warnings for each variable index during simulations.
         * These warnings are triggered whenever certain thresholds or conditions are surpassed
         * for the respective variables, which may indicate potential issues or anomalies in the simulation process.
         */
        public final long[] zExceedWarn1;   // per variable index
        /**
         * Array storing the count of exceedance warnings for each variable index during simulations.
         * These warnings are triggered whenever certain thresholds or conditions are surpassed
         * for the respective variables, which may indicate potential issues or anomalies in the simulation process.
         */
        public final long[] zExceedWarn2;   // per variable index
        /**
         * Array storing the count of exceedance warnings for each variable index during simulations.
         * These warnings are triggered whenever certain thresholds or conditions are surpassed
         * for the respective variables, which may indicate potential issues or anomalies in the simulation process.
         * This array is used to track the number of times a variable's value exceeds a certain threshold during simulations.
         */
        public final double zWarn1;
        /**
         * Array storing the count of exceedance warnings for each variable index during simulations.
         * These warnings are triggered whenever certain thresholds or conditions are surpassed
         * for the respective variables, which may indicate potential issues or anomalies in the simulation process.
         * This array is used to track the number of times a variable's value exceeds a certain threshold during simulations.
         */
        public final double zWarn2;

        // Backwards-compatible ctor (same shape as your current code expects)

        /**
         * Constructs a new SimResult object.
         *
         * @param cont      Continuous data matrix
         * @param disc      Discrete data matrix
         * @param variables List of nodes representing variables
         * @param trueDag   True DAG (Directed Acyclic Graph) for the simulation
         */
        SimResult(double[][] cont, int[][] disc, List<Node> variables, Graph trueDag) {
            this(cont, disc, variables, trueDag,
                    null, null,
                    cont != null ? cont.length : 0L,
                    Double.NaN, Double.NaN);
        }

        // Extended ctor (use if you track warnings in simulate())

        /**
         * Constructs a new SimResult object with detailed simulation results.
         *
         * @param cont         Continuous data matrix
         * @param disc         Discrete data matrix
         * @param variables    List of nodes representing variables
         * @param trueDag      True DAG (Directed Acyclic Graph) for the simulation
         * @param zExceedWarn1 Array storing exceedance warnings for continuous variables
         * @param zExceedWarn2 Array storing exceedance warnings for discrete variables
         * @param nSamples     Number of samples used in the simulation
         * @param zWarn1       Threshold for continuous variable exceedance warnings
         * @param zWarn2       Threshold for discrete variable exceedance warnings
         */
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

        // NEW: optional simulation report. Safe even if warnings are NaN / not tracked.

        /**
         * Generates a simulation report summarizing the results of a TrainedDagSimulator.
         * The report includes details about the simulation parameters, warnings, and other
         * relevant information.
         *
         * @param lambdaParents  The regularization parameter controlling the strength of parent
         *                       relationships in the simulation.
         * @param bootstrapRoots A boolean indicating whether roots were bootstrapped in the simulation.
         * @return A string containing the formatted simulation report.
         */
        public String getSimReportText(double lambdaParents, boolean bootstrapRoots) {
            StringBuilder sb = new StringBuilder();
            sb.append("TrainedDagSimulator simulation report\n");
            sb.append("nSamples=").append(nSamples).append("\n");
            sb.append("lambdaParents=").append(lambdaParents).append("\n");
            sb.append("bootstrapRoots=").append(bootstrapRoots).append("\n");

            if (Double.isFinite(zWarn1) && Double.isFinite(zWarn2)) {
                sb.append("zWarn1=").append(zWarn1).append(" zWarn2=").append(zWarn2).append("\n\n");
                sb.append("Support warnings (fraction of rows where max|z(parent)| exceeds thresholds)\n");

                long denom = FastMath.max(1L, nSamples);
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
    }

    private final class ContinuousMechanism extends Mechanism {
        final MlpRegressor net;

        double baseMean;
        double[] residuals; // global residual pool
        Map<Integer, double[]> residualsBySig; // optional strata (sig -> residual pool)

        ContinuousMechanism(int childIndex, int[] parentIdx, InputEncoder encoder, int hidden, Random rng) {
            super(childIndex, parentIdx, encoder);
            this.net = new MlpRegressor(encoder.featureDim, hidden, rng);
        }

        @Override
        void fit(DataSet data, Random rng, Params p) {
            TrainingRows tr = TrainingRows.forNode(data, childIndex, parentIdx);

            // compute baseMean over training rows (or all finite if no rows)
            baseMean = computeChildMean(data, childIndex, tr);

            if (tr.n < 5) {
                residuals = new double[]{0.0};
                addNodeReportContinuous(childIndex, parentIdx, tr.n,
                        Double.NaN, Double.NaN, Double.NaN);
                return;
            }

            // SGD
            for (int ep = 0; ep < p.epochs; ep++) {
                tr.shuffle(rng);
                for (int start = 0; start < tr.n; start += p.batchSize) {
                    int end = FastMath.min(tr.n, start + p.batchSize);
                    net.sgdStep(data, tr.rows, start, end, encoder, childIndex, p.lr, p.l2);
                }
            }

            // Residuals + MSE
            residuals = new double[tr.n];
            double[] x = new double[encoder.featureDim];

            double sse = 0.0;
            double sum = 0.0, sum2 = 0.0;

            // optional stratified residuals by discrete-parent signature
            boolean doStrata = p.stratifyResidualsByDiscreteParents && encoder.hasAnyDiscreteParents();
            Map<Integer, ArrayList<Double>> tmpStrata = doStrata ? new HashMap<>() : null;

            for (int i = 0; i < tr.n; i++) {
                int row = tr.rows[i];
                encoder.encodeRow(data, row, x);
                double yhat = net.predict(x);
                double y = data.getDouble(row, childIndex);
                double e = y - yhat;

                residuals[i] = e;

                sse += e * e;
                sum += e;
                sum2 += e * e;

                if (doStrata) {
                    int sig = discreteSignatureFromDataRow(data, row, encoder);
                    ArrayList<Double> list = tmpStrata.computeIfAbsent(sig, k -> new ArrayList<>());
                    list.add(e);
                }
            }

            if (doStrata && tmpStrata.size() <= p.maxResidualStrata) {
                residualsBySig = new HashMap<>();
                for (Map.Entry<Integer, ArrayList<Double>> e : tmpStrata.entrySet()) {
                    ArrayList<Double> list = e.getValue();
                    double[] arr = new double[list.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
                    residualsBySig.put(e.getKey(), arr);
                }
            } else {
                residualsBySig = null; // fallback global
            }

            double mse = sse / tr.n;
            double mean = sum / tr.n;
            double var = (tr.n > 1) ? (sum2 - tr.n * mean * mean) / (tr.n - 1.0) : 0.0;
            double sd = FastMath.sqrt(FastMath.max(0.0, var));

            addNodeReportContinuous(childIndex, parentIdx, tr.n, mse, mean, sd);
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            double[] x = encoder.encodeFromGenerated(contRow, discRow);
            double yhat = net.predict(x);

            // blend unconditional mean vs parent-based prediction
            double lam = clamp01(params.lambdaParents);
            double mu = (1.0 - lam) * baseMean + lam * yhat;

            // choose residual pool (stratified if available)
            double eps;
            if (residualsBySig != null) {
                int sig = encoder.discreteSignatureFromGenerated(discRow);
                double[] pool = residualsBySig.get(sig);
                if (pool != null && pool.length > 0) eps = pool[rng.nextInt(pool.length)];
                else eps = residuals.length == 0 ? 0.0 : residuals[rng.nextInt(residuals.length)];
            } else {
                eps = residuals.length == 0 ? 0.0 : residuals[rng.nextInt(residuals.length)];
            }

            contRow[childIndex] = mu + eps;
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
                int v = safeGetInt(data, row, col);
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
                    int end = FastMath.min(tr.n, start + p.batchSize);
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
                xent += -FastMath.log(FastMath.max(1e-300, probs[y]));
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