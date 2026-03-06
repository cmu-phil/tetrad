// File: edu/cmu/tetrad/sem/TrainedDagSimulator.java
package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;

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
 *       configurations leave the support of the training data (extrapolation). Optional diagnostics may warn when
 *       parent z-scores become extreme.</li>
 *   <li>The GNM is strictly more expressive than the ANM: it can represent non-additive effects of noise on the child
 *       given parents, while still using bootstrapped noise anchored to the observed dataset.</li>
 * </ul>
 */
public final class TrainedDagSimulatorGNM {

    // -------------------- configuration --------------------
    private final List<NodeReport> nodeReports = Collections.synchronizedList(new ArrayList<>());

    // -------------------- fit reporting --------------------
    private final DataSet data;
    private final Graph dag;
    private final Params params;
    private final List<Node> variables;
    private final boolean[] isDiscrete;

    // -------------------- trained model per node --------------------
    private final Mechanism[] mechanisms;

    /**
     * Constructs a new instance of the TrainedDagSimulatorGNM class. This simulator utilizes a directed acyclic graph (DAG)
     * representation and corresponding data to model variable relationships and simulate values based on learned mechanisms.
     *
     * @param data   A {@code DataSet} object containing the variables and observations to be utilized in the simulation.
     *               Must not be {@code null}.
     * @param dag    A {@code Graph} object representing the structure of the DAG to be used. The DAG must be acyclic and
     *               must also not be {@code null}.
     * @param params An optional {@code Params} object containing additional configuration parameters for the simulator.
     *               If this parameter is {@code null}, default parameters will be used.
     * @throws NullPointerException     If {@code data} or {@code dag} is {@code null}.
     * @throws IllegalArgumentException If {@code dag} contains cycles (i.e., is not a valid DAG).
     */
    public TrainedDagSimulatorGNM(DataSet data, Graph dag, Params params) {
        if (data == null) throw new NullPointerException("data");
        if (dag == null) throw new NullPointerException("dag");
        if (!dag.paths().isLegalDag()) throw new IllegalArgumentException("The supplied graph is not a DAG.");

        dag = GraphUtils.replaceNodes(dag, data.getVariables());

        if (!new HashSet<>(data.getVariables()).containsAll(new HashSet<>(dag.getNodes()))) {
            throw new IllegalArgumentException("The supplied dataset does not contain all variables from the DAG.");
        }

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
     * Safely retrieves an integer value from the specified {@code DataSet} at the given row and column.
     * If the cell contains a non-integer but finite value, it is rounded to the nearest integer.
     * For non-finite values (e.g., NaN or infinities), a default value of {@code -1} is returned.
     *
     * @param data The {@code DataSet} object from which the value is retrieved. Must not be {@code null}.
     * @param row  The row index of the value to retrieve. Must be a valid index within the dataset.
     * @param col  The column index of the value to retrieve. Must be a valid index within the dataset.
     * @return The integer value at the specified cell, or {@code -1} if the cell contains a non-finite value.
     */
    public static int safeGetInt(DataSet data, int row, int col) {
        try {
            return data.getInt(row, col);
        } catch (Throwable t) {
            double x = data.getDouble(row, col);
            if (!Double.isFinite(x)) return -1;
            return (int) TMath.rint(x);
        }
    }

    // -------------------- training row selection per node --------------------

    /**
     * Human-readable report (one block per node).
     *
     * @return A string containing
     */
    public String getFitReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("TrainedDagSimulator fit report\n");
        sb.append("seed=").append(params.seed).append("\n");
        sb.append("hiddenLayers=").append(Arrays.toString(params.getHiddenLayers()))
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
     * @return An unmodifiable list of node reports, each containing fit metrics for a node.
     */
    public List<NodeReport> getNodeReports() {
        return Collections.unmodifiableList(nodeReports);
    }

    /**
     * Writes a human-readable textual representation of the fit report to the specified file.
     * The report contains information about the trained DAG simulator, including parameters
     * and per-node reports on training fit metrics.
     *
     * @param file The target file where the fit report will be written.
     *             Must be writable and not {@code null}.
     * @throws IOException If an error occurs while writing to the file.
     */
    public void writeFitReportTxt(File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(getFitReportText());
        }
    }

    // -------------------- main simulator object --------------------

    private static long mixSeed(long baseSeed, int index, long salt) {
        long z = baseSeed + salt + 0x9E3779B97F4A7C15L * (index + 1L);
        z ^= (z >>> 30);
        z *= 0xBF58476D1CE4E5B9L;
        z ^= (z >>> 27);
        z *= 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        return z;
    }

    private int[] parentIndicesFor(Node child, Map<String, Integer> indexByName) {
        List<Node> ps = dag.getParents(child);
        int[] parentIdx = new int[ps.size()];

        for (int k = 0; k < ps.size(); k++) {
            Integer pi = indexByName.get(ps.get(k).getName());
            if (pi == null) {
                throw new IllegalArgumentException("Parent not found in dataset: " + ps.get(k).getName());
            }
            parentIdx[k] = pi;
        }

        return parentIdx;
    }

    /**
     * Fit one mechanism per node given its parents in the supplied DAG.
     */
    public void fit() {
        nodeReports.clear();

        // topo order (use dag nodes, but we map to dataset indices)
        List<Node> topo = dag.paths().getValidOrder(dag.getNodes(), true);

        // map name -> dataset index
        Map<String, Integer> indexByName = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            indexByName.put(variables.get(j).getName(), j);
        }

        // Convert topo nodes to dataset indices once
        List<Integer> topoIdx = new ArrayList<>(topo.size());
        for (Node child : topo) {
            Integer idx = indexByName.get(child.getName());
            if (idx != null) {
                topoIdx.add(idx);
            }
        }

        topoIdx.parallelStream().forEach(childIdx -> {
            Node child = variables.get(childIdx);
            int[] parentIdx = parentIndicesFor(child, indexByName);
            boolean isRoot = parentIdx.length == 0;

            // Independent RNGs per node to avoid shared contention.
            Random initRng = new Random(mixSeed(params.seed, childIdx, 0x1234ABCDL));
            Random fitRng = new Random(mixSeed(params.seed, childIdx, 0x5678EF01L));

            InputEncoder encoder = new InputEncoder(data, parentIdx, params.maxDiscreteLevels);

            Mechanism m;

            if (!isDiscrete[childIdx]) {
                if (isRoot && params.bootstrapRoots) {
                    m = new RootContinuousMechanism(childIdx);
                } else {
                    m = new ContinuousMechanism(childIdx, parentIdx, encoder, initRng);
                }
            } else {
                int L = ((DiscreteVariable) variables.get(childIdx)).getNumCategories();

                if (L <= 1) {
                    throw new IllegalArgumentException(
                            "Discrete variable has <=1 category: " + variables.get(childIdx).getName());
                }
                if (L > params.maxDiscreteLevels) {
                    throw new IllegalArgumentException(
                            "Discrete child has too many levels: " + variables.get(childIdx).getName() + " L=" + L);
                }

                if (isRoot && params.bootstrapRoots) {
                    m = new RootDiscreteMechanism(childIdx, L);
                } else {
                    m = new DiscreteMechanism(childIdx, parentIdx, encoder, L, initRng);
                }
            }

            m.fit(data, fitRng, params);
            mechanisms[childIdx] = m;
        });
    }

    /**
     * Simulates data by generating continuous and discrete values for all variables in the
     * trained directed acyclic graph (DAG) model. It performs the simulation using a specified
     * number of samples and a seed for random number generation.
     *
     * @param nSamples The number of samples to generate during the simulation. Must be a
     *                 positive integer.
     * @return A {@code SimResult} object containing the simulated continuous and discrete
     * values, along with additional metadata related to the DAG and variables used
     * in the simulation.
     */
    public SimResult simulate(int nSamples) {
        return simulate(nSamples, params.seed ^ 0x9E3779B97F4A7C15L);
    }

    /**
     * Simulates data by generating continuous and discrete values for all variables
     * in the trained directed acyclic graph (DAG) model. The simulation uses a specified
     * number of samples and a seed for random number generation, ensuring reproducibility.
     *
     * @param nSamples The number of samples to generate during the simulation.
     *                 Must be a positive integer greater than or equal to 1.
     * @param seed     A long value representing the seed for the random number generator.
     *                 Provides deterministic simulation results when the same seed is used.
     * @return A {@code SimResult} object containing the simulated continuous and discrete
     * values, along with metadata about the DAG and the set of variables used
     * in the simulation.
     * @throws IllegalArgumentException If {@code nSamples} is less than 1.
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

    public static final class Params {
        public int hidden = 64;
        public int[] hiddenLayers = null;

        public int epochs = 200;
        public double lr = 0.01;
        public double l2 = 1e-4;
        public int batchSize = 64;
        public long seed = 12345L;
        public int maxDiscreteLevels = 50;
        public boolean bootstrapRoots = true;
        public double lambdaParents = 1.0;
        public double zWarn1 = 4.0;
        public double zWarn2 = 6.0;
        public boolean stratifyResidualsByDiscreteParents = true;
        public int maxResidualStrata = 5000;

        public Params() {
        }

        public int[] getHiddenLayers() {
            if (hiddenLayers != null && hiddenLayers.length > 0) {
                return hiddenLayers.clone();
            }
            return new int[]{hidden};
        }
    }

    /**
     * Represents a report for a single node in a trained directed acyclic graph (DAG) structure.
     * The report contains metrics specific to the node, which can vary depending on whether the
     * node represents a discrete or continuous variable. This is an immutable class.
     */
    public static final class NodeReport {
        /**
         * Node identifier.
         */
        public final String node;
        /**
         * Whether the node represents a discrete variable.
         */
        public final boolean discreteChild;
        /**
         * List of parent nodes for the current node.
         */
        public final List<String> parents;
        /**
         * Number of training rows used for the node.
         */
        public final int trainingRowsUsed;

        // Continuous-only
        /**
         * Mean Squared Error (MSE) for the training dataset associated with the node.
         * This metric is relevant only for nodes representing continuous variables.
         * If the node is a discrete variable, this value will be NaN.
         */
        public final double mseTrain;          // NaN if discrete child
        /**
         * Mean and standard deviation of residuals for the training dataset associated with the node.
         * This metric is relevant only for nodes representing continuous variables.
         * If the node is a discrete variable, these values will be NaN.
         */
        public final double residualMean;      // NaN if discrete child
        /**
         * Standard deviation of residuals for the training dataset associated with the node.
         * This metric is relevant only for nodes representing continuous variables.
         * If the node is a discrete variable, this value will be NaN.
         */
        public final double residualSd;        // NaN if discrete child

        // Discrete-only
        /**
         * Cross-entropy loss for the training dataset associated with the node.
         * This metric is relevant only for nodes representing discrete variables.
         * If the node is a continuous variable, this value will be NaN.
         */
        public final double xentTrain;         // NaN if continuous child
        /**
         * Number of discrete levels for the node.
         * This metric is relevant only for nodes representing discrete variables.
         * If the node is a continuous variable, this value will be 0.
         */
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
                double s = TMath.sqrt(TMath.max(1e-12, var));
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
            encodeFromGenerated(contRow, discRow, out);
            return out;
        }

//        double[] encodeFromGenerated(double[] contRow, int[] discRow) {
//            double[] out = new double[featureDim];
//            // continuous
//            for (int k = 0; k < contParents.length; k++) {
//                int col = contParents[k];
//                double x = contRow[col];
//                out[k] = (x - mean[k]) / sd[k];
//            }
//            // discrete
//            for (int j = 0; j < parentIdx.length; j++) {
//                if (!parentIsDisc[j]) continue;
//                int col = parentIdx[j];
//                int L = discLevels[j];
//                int off = discOffset[j];
//                int v = discRow[col];
//                if (v < 0 || v >= L) continue;
//                out[off + v] = 1.0;
//            }
//            return out;
//        }

        void encodeFromGenerated(double[] contRow, int[] discRow, double[] out) {
            Arrays.fill(out, 0.0);

            // continuous block
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double x = contRow[col];
                out[k] = (x - mean[k]) / sd[k];
            }

            // discrete blocks
            for (int j = 0; j < parentIdx.length; j++) {
                if (!parentIsDisc[j]) continue;
                int col = parentIdx[j];
                int L = discLevels[j];
                int off = discOffset[j];
                int v = discRow[col];
                if (v < 0 || v >= L) continue;
                out[off + v] = 1.0;
            }
        }

        double maxAbsZFromGenerated(double[] contRow) {
            double m = 0.0;
            for (int k = 0; k < contParents.length; k++) {
                int col = contParents[k];
                double z = (contRow[col] - mean[k]) / sd[k];
                double az = TMath.abs(z);
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

    private static final class EncodedRegressionData {
        final double[][] X;
        final double[] y;
        final int[] rows;   // original dataset row indices
        final int n;

        EncodedRegressionData(double[][] X, double[] y, int[] rows) {
            this.X = X;
            this.y = y;
            this.rows = rows;
            this.n = rows.length;
        }
    }

    private static final class EncodedClassificationData {
        final double[][] X;
        final int[] y;
        final int[] rows;   // original dataset row indices
        final int n;

        EncodedClassificationData(double[][] X, int[] y, int[] rows) {
            this.X = X;
            this.y = y;
            this.rows = rows;
            this.n = rows.length;
        }
    }

    private static EncodedRegressionData buildEncodedRegressionData(
            DataSet data, InputEncoder encoder, int childIndex, TrainingRows tr) {

        int n = tr.n;
        double[][] X = new double[n][encoder.featureDim];
        double[] y = new double[n];
        int[] rows = tr.rows.clone();

        for (int i = 0; i < n; i++) {
            int row = rows[i];
            encoder.encodeRow(data, row, X[i]);
            y[i] = data.getDouble(row, childIndex);
        }

        return new EncodedRegressionData(X, y, rows);
    }

    private static EncodedClassificationData buildEncodedClassificationData(
            DataSet data, InputEncoder encoder, int childIndex, TrainingRows tr) {

        int n = tr.n;
        double[][] X = new double[n][encoder.featureDim];
        int[] y = new int[n];
        int[] rows = tr.rows.clone();

        for (int i = 0; i < n; i++) {
            int row = rows[i];
            encoder.encodeRow(data, row, X[i]);
            y[i] = safeGetInt(data, row, childIndex);
        }

        return new EncodedClassificationData(X, y, rows);
    }

    private static void shuffleIndices(int[] order, Random rng) {
        for (int i = order.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
    }

    private static final class MlpRegressor {
        final int din;
        final int[] hiddenLayers;

        // Hidden layers
        final double[][][] W;   // W[layer][out][in]
        final double[][] b;     // b[layer][out]

        // Output layer
        final double[] Wout;    // scalar output from last hidden
        double bout;

        MlpRegressor(int din, int[] hiddenLayers, Random rng) {
            this.din = din;
            this.hiddenLayers = hiddenLayers.clone();

            this.W = new double[hiddenLayers.length][][];
            this.b = new double[hiddenLayers.length][];

            int prev = din;
            for (int l = 0; l < hiddenLayers.length; l++) {
                int h = hiddenLayers[l];
                W[l] = new double[h][prev];
                b[l] = new double[h];
                prev = h;
            }

            this.Wout = new double[TMath.max(1, prev)];
            initHe(rng);
        }

        void initHe(Random rng) {
            int prev = din;
            for (int l = 0; l < hiddenLayers.length; l++) {
                int h = hiddenLayers[l];
                double s = TMath.sqrt(2.0 / TMath.max(1, prev));
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < prev; j++) {
                        W[l][i][j] = rng.nextGaussian() * s;
                    }
                    b[l][i] = 0.0;
                }
                prev = h;
            }

            double sout = TMath.sqrt(2.0 / TMath.max(1, prev));
            for (int i = 0; i < Wout.length; i++) {
                Wout[i] = rng.nextGaussian() * sout;
            }
            bout = 0.0;
        }

        double predict(double[] x) {
            double[] a = x;

            for (int l = 0; l < hiddenLayers.length; l++) {
                double[] next = new double[hiddenLayers[l]];
                for (int i = 0; i < hiddenLayers[l]; i++) {
                    double z = b[l][i];
                    double[] wi = W[l][i];
                    for (int j = 0; j < a.length; j++) {
                        z += wi[j] * a[j];
                    }
                    next[i] = TMath.tanh(z);
                }
                a = next;
            }

            double y = bout;
            for (int i = 0; i < a.length; i++) {
                y += Wout[i] * a[i];
            }
            return y;
        }

        void sgdStep(DataSet data, int[] rows, int start, int end,
                     InputEncoder enc, int childCol, double lr, double l2) {
            double[] x = new double[din];
            for (int t = start; t < end; t++) {
                int r = rows[t];
                enc.encodeRow(data, r, x);
                double y = data.getDouble(r, childCol);
                sgdStepOne(x, y, lr, l2);
            }
        }

        void sgdStepOne(double[] x, double y, double lr, double l2) {
            int L = hiddenLayers.length;

            // Forward pass
            double[][] activations = new double[L + 1][];
            activations[0] = x;

            for (int l = 0; l < L; l++) {
                double[] prev = activations[l];
                double[] curr = new double[hiddenLayers[l]];
                for (int i = 0; i < hiddenLayers[l]; i++) {
                    double z = b[l][i];
                    double[] wi = W[l][i];
                    for (int j = 0; j < prev.length; j++) {
                        z += wi[j] * prev[j];
                    }
                    curr[i] = TMath.tanh(z);
                }
                activations[l + 1] = curr;
            }

            double[] last = activations[L];
            double yhat = bout;
            for (int i = 0; i < last.length; i++) {
                yhat += Wout[i] * last[i];
            }

            double err = yhat - y;

            // Gradient wrt output layer
            double[] deltaNext = new double[last.length];
            for (int i = 0; i < last.length; i++) {
                deltaNext[i] = err * Wout[i];
                double g = err * last[i] + l2 * Wout[i];
                Wout[i] -= lr * g;
            }
            bout -= lr * err;

            // Backprop hidden layers
            for (int l = L - 1; l >= 0; l--) {
                double[] a = activations[l + 1];
                double[] prev = activations[l];
                double[] delta = new double[a.length];

                for (int i = 0; i < a.length; i++) {
                    delta[i] = deltaNext[i] * (1.0 - a[i] * a[i]);
                }

                double[] newDeltaNext = new double[prev.length];

                for (int i = 0; i < a.length; i++) {
                    double[] wi = W[l][i];
                    double di = delta[i];

                    for (int j = 0; j < prev.length; j++) {
                        newDeltaNext[j] += di * wi[j];
                        double g = di * prev[j] + l2 * wi[j];
                        wi[j] -= lr * g;
                    }
                    b[l][i] -= lr * di;
                }

                deltaNext = newDeltaNext;
            }
        }

        void sgdStep(double[][] X, double[] y, int[] order, int start, int end,
                     double lr, double l2) {
            for (int t = start; t < end; t++) {
                int i = order[t];
                sgdStepOne(X[i], y[i], lr, l2);
            }
        }
    }

    private static final class MlpSoftmaxClassifier {
        final int din;
        final int[] hiddenLayers;
        final int k;

        // Hidden layers
        final double[][][] W;   // W[layer][out][in]
        final double[][] b;     // b[layer][out]

        // Output layer
        final double[][] Wout;  // k x lastHidden
        final double[] bout;    // k

        MlpSoftmaxClassifier(int din, int[] hiddenLayers, int k, Random rng) {
            this.din = din;
            this.hiddenLayers = hiddenLayers.clone();
            this.k = k;

            this.W = new double[hiddenLayers.length][][];
            this.b = new double[hiddenLayers.length][];

            int prev = din;
            for (int l = 0; l < hiddenLayers.length; l++) {
                int h = hiddenLayers[l];
                W[l] = new double[h][prev];
                b[l] = new double[h];
                prev = h;
            }

            this.Wout = new double[k][TMath.max(1, prev)];
            this.bout = new double[k];

            initHe(rng);
        }

        void initHe(Random rng) {
            int prev = din;
            for (int l = 0; l < hiddenLayers.length; l++) {
                int h = hiddenLayers[l];
                double s = TMath.sqrt(2.0 / TMath.max(1, prev));
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < prev; j++) {
                        W[l][i][j] = rng.nextGaussian() * s;
                    }
                    b[l][i] = 0.0;
                }
                prev = h;
            }

            double sout = TMath.sqrt(2.0 / TMath.max(1, prev));
            for (int c = 0; c < k; c++) {
                for (int j = 0; j < Wout[c].length; j++) {
                    Wout[c][j] = rng.nextGaussian() * sout;
                }
                bout[c] = 0.0;
            }
        }

        private static void softmaxInto(double[] z, double[] out) {
            double max = z[0];
            for (int i = 1; i < z.length; i++) {
                if (z[i] > max) max = z[i];
            }

            double sum = 0.0;
            for (int i = 0; i < z.length; i++) {
                out[i] = TMath.exp(z[i] - max);
                sum += out[i];
            }

            double inv = 1.0 / TMath.max(1e-300, sum);
            for (int i = 0; i < z.length; i++) {
                out[i] *= inv;
            }
        }

        double[] predictProbs(double[] x) {
            double[] probs = new double[k];
            predictProbsInto(x, probs);
            return probs;
        }

        void predictProbsInto(double[] x, double[] outProbs) {
            double[] a = x;

            for (int l = 0; l < hiddenLayers.length; l++) {
                double[] next = new double[hiddenLayers[l]];
                for (int i = 0; i < hiddenLayers[l]; i++) {
                    double z = b[l][i];
                    double[] wi = W[l][i];
                    for (int j = 0; j < a.length; j++) {
                        z += wi[j] * a[j];
                    }
                    next[i] = TMath.tanh(z);
                }
                a = next;
            }

            double[] logits = new double[k];
            for (int c = 0; c < k; c++) {
                double z = bout[c];
                for (int j = 0; j < a.length; j++) {
                    z += Wout[c][j] * a[j];
                }
                logits[c] = z;
            }

            softmaxInto(logits, outProbs);
        }

        void sgdStep(DataSet data, int[] rows, int start, int end,
                     InputEncoder enc, int childCol, double lr, double l2) {
            double[] x = new double[din];

            for (int t = start; t < end; t++) {
                int r = rows[t];
                enc.encodeRow(data, r, x);

                int y = safeGetInt(data, r, childCol);
                if (y < 0 || y >= k) continue;

                sgdStepOne(x, y, lr, l2);
            }
        }

        void sgdStepOne(double[] x, int y, double lr, double l2) {
            int L = hiddenLayers.length;

            // Forward pass
            double[][] activations = new double[L + 1][];
            activations[0] = x;

            for (int l = 0; l < L; l++) {
                double[] prev = activations[l];
                double[] curr = new double[hiddenLayers[l]];
                for (int i = 0; i < hiddenLayers[l]; i++) {
                    double z = b[l][i];
                    double[] wi = W[l][i];
                    for (int j = 0; j < prev.length; j++) {
                        z += wi[j] * prev[j];
                    }
                    curr[i] = TMath.tanh(z);
                }
                activations[l + 1] = curr;
            }

            double[] last = activations[L];

            double[] logits = new double[k];
            for (int c = 0; c < k; c++) {
                double z = bout[c];
                for (int j = 0; j < last.length; j++) {
                    z += Wout[c][j] * last[j];
                }
                logits[c] = z;
            }

            double[] probs = new double[k];
            softmaxInto(logits, probs);

            // gradient wrt logits
            probs[y] -= 1.0;

            // backprop into last hidden using OLD Wout
            double[] deltaNext = new double[last.length];
            for (int j = 0; j < last.length; j++) {
                double s = 0.0;
                for (int c = 0; c < k; c++) {
                    s += Wout[c][j] * probs[c];
                }
                deltaNext[j] = s;
            }

            // update output layer
            for (int c = 0; c < k; c++) {
                double gc = probs[c];
                for (int j = 0; j < last.length; j++) {
                    double g = gc * last[j] + l2 * Wout[c][j];
                    Wout[c][j] -= lr * g;
                }
                bout[c] -= lr * gc;
            }

            // backprop through hidden layers
            for (int l = L - 1; l >= 0; l--) {
                double[] a = activations[l + 1];
                double[] prev = activations[l];

                double[] delta = new double[a.length];
                for (int i = 0; i < a.length; i++) {
                    delta[i] = deltaNext[i] * (1.0 - a[i] * a[i]);
                }

                double[] newDeltaNext = new double[prev.length];

                for (int i = 0; i < a.length; i++) {
                    double[] wi = W[l][i];
                    double di = delta[i];

                    for (int j = 0; j < prev.length; j++) {
                        newDeltaNext[j] += di * wi[j];
                        double g = di * prev[j] + l2 * wi[j];
                        wi[j] -= lr * g;
                    }
                    b[l][i] -= lr * di;
                }

                deltaNext = newDeltaNext;
            }
        }

        void sgdStep(double[][] X, int[] y, int[] order, int start, int end,
                     double lr, double l2) {
            for (int t = start; t < end; t++) {
                int i = order[t];
                int yi = y[i];
                if (yi < 0 || yi >= k) continue;
                sgdStepOne(X[i], yi, lr, l2);
            }
        }
    }

    /**
     * Represents the result of a simulation, containing continuous and discrete data
     * for variables, metadata about the simulation, and support for generating reports.
     * This class is designed to encapsulate all relevant outputs of a simulation process
     * including variable data, causal graphs, and warning metrics.
     */
    public static final class SimResult {
        /**
         * A two-dimensional array representing continuous values for all variables in a simulation result.
         * This array includes numerical data corresponding to continuous variables, while discrete columns
         * are also present but are considered irrelevant in this context. Each row typically corresponds
         * to a sample, and each column corresponds to a variable.
         */
        public final double[][] cont;      // continuous values for all vars (discrete columns also exist but meaningless here)
        /**
         * Discrete codes for all variables in a simulation result.
         * This array includes numerical data corresponding to discrete variables, while continuous columns
         * are also present but are considered irrelevant in this context. Each row typically corresponds
         * to a sample, and each column corresponds to a variable.
         */
        public final int[][] disc;         // discrete codes for all vars (continuous columns are left as 0)
        /**
         * List of variables in the simulation result.
         * This field contains a list of Node objects representing the variables in the simulation.
         * Each Node corresponds to a variable in the simulation, and the list order reflects the
         * order of variables in the simulation result.
         */
        public final List<Node> variables;
        /**
         * Graph representing the true causal structure of the simulation.
         * This field contains a Graph object representing the true causal structure of the simulation.
         * It is used to compare against the simulated structure and assess the accuracy of the simulation.
         */
        public final Graph trueDag;

        /**
         * Number of samples simulated in this result.
         */
        public final long nSamples;
        /**
         * Tracks the count of occurrences where a specific variable index exceeds a certain
         * z-score warning threshold during simulation. These counts are indexed by variable.
         * <p>
         * This field is primarily used to support warnings and validations in simulation reports
         * where z-score thresholds are monitored. The array size matches the number of variables
         * being simulated, allowing each index to correspond to a specific variable.
         * <p>
         * Modifications to this field are expected to occur internally within simulation processes
         * that monitor and track z-score exceedance events.
         */
        public final long[] zExceedWarn1;   // per variable index
        /**
         * Tracks the count of occurrences where a specific variable index exceeds a certain
         * z-score warning threshold during simulation. These counts are indexed by variable.
         * <p>
         * This field is primarily used to support warnings and validations in simulation reports
         * where z-score thresholds are monitored. The array size matches the number of variables
         * being simulated, allowing each index to correspond to a specific variable.
         * <p>
         * Modifications to this field are expected to occur internally within simulation processes
         * that monitor and track z-score exceedance events.
         */
        public final long[] zExceedWarn2;   // per variable index
        /**
         * Tracks the count of occurrences where a specific variable index exceeds a certain
         * z-score warning threshold during simulation. These counts are indexed by variable.
         * <p>
         * This field is primarily used to support warnings and validations in simulation reports
         * where z-score thresholds are monitored. The array size matches the number of variables
         * being simulated, allowing each index to correspond to a specific variable.
         * <p>
         * Modifications to this field are expected to occur internally within simulation processes
         * that monitor and track z-score exceedance events.
         */
        public final double zWarn1;
        /**
         * Tracks the count
         */
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

        /**
         * Generates a simulation report as a string, summarizing details of the
         * simulation and support warnings based on input parameters and internal state.
         *
         * @param lambdaParents  The regularization parameter applied to parent weights.
         * @param bootstrapRoots A flag indicating whether roots were bootstrapped during
         *                       simulation.
         * @return A string containing the simulation report, including the number of
         * samples, parameter values, and support warnings if thresholds were tracked.
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

                long denom = TMath.max(1L, nSamples);
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

        /**
         * Writes the simulation report generated by the {@code getSimReportText} method
         * to a specified output file in plain text format.
         *
         * @param outFile        The file to which the simulation report will be written. Must not be null.
         * @param lambdaParents  The regularization parameter applied to parent weights.
         * @param bootstrapRoots A flag indicating whether roots were bootstrapped during simulation.
         * @throws IOException If an I/O error occurs while writing to the file.
         */
        public void writeSimReportTxt(File outFile, double lambdaParents, boolean bootstrapRoots) throws IOException {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                w.write(getSimReportText(lambdaParents, bootstrapRoots));
            }
        }

        /**
         * Converts the current simulation result into a DataSet object, combining both
         * continuous and discrete data variables.
         *
         * This method processes the stored continuous and discrete data arrays, validates their
         * consistency with the variable list, and organizes the data in a format compatible
         * with the MixedDataBox structure. An IllegalStateException is thrown if there are
         * mismatches in the variables or data arrays.
         *
         * @return A {@code DataSet} object containing the combined continuous and discrete
         *         data variables, organized in row-major format.
         * @throws IllegalStateException If both continuous and discrete arrays are empty,
         *                               or if there are inconsistencies between the variable list
         *                               and the data arrays.
         */
        public DataSet toDataSet() {
            List<Node> vars = this.variables;

            int p = vars.size();

            // Determine n from whichever array is present
            int n;
            if (cont != null && cont.length > 0) {
                n = cont.length;
            } else if (disc != null && disc.length > 0) {
                n = disc.length;
            } else {
                throw new IllegalStateException("Both cont and disc arrays are empty.");
            }

            // MixedDataBox expects row-major: [row][col]
            MixedDataBox box = new MixedDataBox(vars, n);

            for (int j = 0; j < p; j++) {
                Node v = vars.get(j);

                if (v instanceof DiscreteVariable) {
                    if (disc == null) {
                        throw new IllegalStateException("Discrete variable " + v.getName() + " but disc array is null.");
                    }

                    for (int i = 0; i < n; i++) {
                        box.set(i, j, disc[i][j]);
                    }

                } else {
                    if (cont == null) {
                        throw new IllegalStateException("Continuous variable " + v.getName() + " but cont array is null.");
                    }

                    for (int i = 0; i < n; i++) {
                        box.set(i, j, cont[i][j]);
                    }
                }
            }

            return new BoxDataSet(box, vars);
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

        // reusable simulation buffers
        private final double[] workX;
        private final double[] workXE;

        ContinuousMechanism(int childIndex, int[] parentIdx, InputEncoder encoder, Random rng) {
            super(childIndex, parentIdx, encoder);

            int[] layers = params.getHiddenLayers();
            this.netMean = new MlpRegressor(encoder.featureDim, layers, rng);
            this.netGNM = new MlpRegressor(encoder.featureDim + 1, layers, rng);

            this.workX = new double[encoder.featureDim];
            this.workXE = new double[encoder.featureDim + 1];
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

            // ---------------------------------
            // Pre-encode X and y once
            // ---------------------------------
            EncodedRegressionData encData = buildEncodedRegressionData(data, encoder, childIndex, tr);
            double[][] X = encData.X;
            double[] y = encData.y;
            int[] rows = encData.rows;
            int n = encData.n;

            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;

            // ---------------------------------
            // Stage 1: fit mu(x)
            // ---------------------------------
            for (int ep = 0; ep < p.epochs; ep++) {
                shuffleIndices(order, rng);
                for (int start = 0; start < n; start += p.batchSize) {
                    int end = TMath.min(n, start + p.batchSize);
                    netMean.sgdStep(X, y, order, start, end, p.lr, p.l2);
                }
            }

            // ---------------------------------
            // Residuals e = y - mu(x)
            // ---------------------------------
            residuals = new double[n];
            residByRow = new double[data.getNumRows()];
            Arrays.fill(residByRow, Double.NaN);

            double sse = 0.0;
            double sumE = 0.0, sumE2 = 0.0;

            boolean doStrata = p.stratifyResidualsByDiscreteParents && encoder.hasAnyDiscreteParents();
            Map<Integer, ArrayList<Double>> tmpStrata = doStrata ? new HashMap<>() : null;

            for (int i = 0; i < n; i++) {
                double yhat = netMean.predict(X[i]);
                double e = y[i] - yhat;

                residuals[i] = e;
                residByRow[rows[i]] = e;

                sse += e * e;
                sumE += e;
                sumE2 += e * e;

                if (doStrata) {
                    int sig = discreteSignatureFromDataRow(data, rows[i], encoder);
                    tmpStrata.computeIfAbsent(sig, k -> new ArrayList<>()).add(e);
                }
            }

            residMean = sumE / n;
            double varE = (n > 1) ? (sumE2 - n * residMean * residMean) / (n - 1.0) : 1.0;
            residSd = TMath.sqrt(TMath.max(1e-12, varE));

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

            double mse = sse / n;
            addNodeReportContinuous(childIndex, parentIdx, n, mse, residMean, residSd);

            // ---------------------------------
            // Stage 2: build XE once, fit g(x,e)
            // ---------------------------------
            double[][] XE = new double[n][encoder.featureDim + 1];
            for (int i = 0; i < n; i++) {
                System.arraycopy(X[i], 0, XE[i], 0, encoder.featureDim);
                XE[i][encoder.featureDim] = (residuals[i] - residMean) / residSd;
            }

            for (int ep = 0; ep < p.epochs; ep++) {
                shuffleIndices(order, rng);
                for (int start = 0; start < n; start += p.batchSize) {
                    int end = TMath.min(n, start + p.batchSize);
                    netGNM.sgdStep(XE, y, order, start, end, p.lr, p.l2);
                }
            }
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            // parent features from already-generated parents
            encoder.encodeFromGenerated(contRow, discRow, workX);

            // optional “turn down” parent influence
            double lam = clamp01(params.lambdaParents);
            if (lam != 1.0) {
                for (int i = 0; i < workX.length; i++) {
                    workX[i] *= lam;
                }
            }

            // sample e from pools
            double e;
            if (residualsBySig != null) {
                int sig = encoder.discreteSignatureFromGenerated(discRow);
                double[] pool = residualsBySig.get(sig);
                if (pool != null && pool.length > 0) {
                    e = pool[rng.nextInt(pool.length)];
                } else {
                    e = residuals[rng.nextInt(residuals.length)];
                }
            } else {
                e = residuals[rng.nextInt(residuals.length)];
            }

            double eStd = (e - residMean) / residSd;

            System.arraycopy(workX, 0, workXE, 0, workX.length);
            workXE[workX.length] = eStd;

            double yGen = netGNM.predict(workXE);
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

        // reusable simulation buffers
        private final double[] workX;
        private final double[] workPNet;
        private final double[] workPMix;

        DiscreteMechanism(int childIndex, int[] parentIdx, InputEncoder encoder, int numLevels, Random rng) {
            super(childIndex, parentIdx, encoder);
            this.numLevels = numLevels;

            int[] layers = params.getHiddenLayers();
            this.net = new MlpSoftmaxClassifier(encoder.featureDim, layers, numLevels, rng);

            this.workX = new double[encoder.featureDim];
            this.workPNet = new double[numLevels];
            this.workPMix = new double[numLevels];
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

            // ---------------------------------
            // Pre-encode X and y once
            // ---------------------------------
            EncodedClassificationData encData = buildEncodedClassificationData(data, encoder, childIndex, tr);
            double[][] X = encData.X;
            int[] y = encData.y;
            int n = encData.n;

            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;

            for (int ep = 0; ep < p.epochs; ep++) {
                shuffleIndices(order, rng);
                for (int start = 0; start < n; start += p.batchSize) {
                    int end = TMath.min(n, start + p.batchSize);
                    net.sgdStep(X, y, order, start, end, p.lr, p.l2);
                }
            }

            // Cross-entropy on training rows
            double xent = 0.0;
            int used = 0;

            for (int i = 0; i < n; i++) {
                int yi = y[i];
                if (yi < 0 || yi >= numLevels) continue;

                double[] probs = net.predictProbs(X[i]);
                xent += -TMath.log(TMath.max(1e-300, probs[yi]));
                used++;
            }

            double xentAvg = (used > 0) ? (xent / used) : Double.NaN;
            addNodeReportDiscrete(childIndex, parentIdx, n, xentAvg, numLevels);
        }

        @Override
        void generateOneRow(DataSet data, double[] contRow, int[] discRow, Random rng) {
            encoder.encodeFromGenerated(contRow, discRow, workX);
            net.predictProbsInto(workX, workPNet);

            double lam = clamp01(params.lambdaParents);

            // p = (1-lam)*pBase + lam*pNet
            double sum = 0.0;
            for (int k = 0; k < numLevels; k++) {
                double v = (1.0 - lam) * baseProbs[k] + lam * workPNet[k];
                workPMix[k] = v;
                sum += v;
            }

            if (sum > 0) {
                double inv = 1.0 / sum;
                for (int k = 0; k < numLevels; k++) {
                    workPMix[k] *= inv;
                }
            } else {
                for (int k = 0; k < numLevels; k++) {
                    workPMix[k] = 1.0 / numLevels;
                }
            }

            discRow[childIndex] = sampleCategorical(workPMix, rng);
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