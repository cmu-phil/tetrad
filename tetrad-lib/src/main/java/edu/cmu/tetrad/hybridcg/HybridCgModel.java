/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Pm;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Mixed Continuous/Discrete model with discretization for discrete children that have continuous parents.
 *
 * <p>Design goals:</p>
 * <ul>
 *   <li>Mirror BayesPm/MlBayesIm and SemPm/SemIm ergonomics where sensible.</li>
 *   <li>Allow mixed parent sets. For a continuous child: stratify by discrete parents and fit linear-Gaussian
 *       per stratum. For a discrete child: build a CPT whose rows are the cross-product of discrete-parent states
 *       and <b>discretized bins</b> of continuous-parent values (bin edges kept in the PM).</li>
 *   <li>Keep a stable row-indexing contract so tables can be read/written and scored efficiently.</li>
 * </ul>
 */
public final class HybridCgModel {

    /**
     * The HybridCgPm class represents a structural model for hybrid Bayesian networks, which may include both discrete
     * and continuous variables. It provides various methods to manage the graph structure, nodes, discrete and
     * continuous parent relationships, and discretize continuous values based on predefined cutpoints. The class is
     * also responsible for indexing and generating the local tables needed for conditional probability computations
     * across mixed data types.
     */
    public static final class HybridCgPm implements Pm, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Represents a structural model for hybrid Bayesian networks, which may include both discrete and continuous
         * variables.
         */
        private final Graph dag;
        /**
         * An array representing the nodes in the hybrid causal graph probabilistic model. Each element corresponds to a
         * specific node in the directed acyclic graph (DAG), reflecting the structure and dependencies of the model.
         * <p>
         * This field is initialized during the construction of the model and contains the ordered representation of
         * nodes used throughout various computations and methods within the model.
         * <p>
         * The nodes can be either discrete or continuous, as defined by the model's configuration.
         */
        private final Node[] nodes;
        /**
         * Variable typing and categories (for discrete variables only)
         */
        private final boolean[] isDiscrete;
        /**
         * Represents the categories associated with each node in the hybrid causal probabilistic model. This array
         * defines the categorical values for discrete nodes and remains null for continuous nodes.
         * <p>
         * For a discrete node, the list size corresponds to the node's cardinality (number of categories), and each
         * entry in the list denotes a specific category label. If a node is continuous,
         */
        private final List<String>[] categories;   // null for continuous; else size = cardinality
        /**
         * Parent lists split by type, pointing to indices into nodes[]
         */
        private final int[][] discParents;
        /**
         * Parent lists split by type, pointing to indices into nodes[]
         */
        private final int[][] contParents;
        /**
         * For DISCRETE children ONLY: if a continuous parent exists, we discretize it. Store per (child,
         * contParentIndexInOrder) the monotonically increasing cutpoints. bins = cutpoints.length + 1
         */
        private final double[][][] contParentCutpointsForDiscreteChild;

        /**
         * Constructs a HybridCgPm instance based on the provided directed acyclic graph (DAG), node ordering, discrete
         * flags for nodes, and a mapping of node categories.
         * <p>
         * The HybridCgPm represents a probabilistic model that supports a mix of continuous and discrete variables with
         * parent dependencies. This constructor initializes the model structure and categorization for nodes based on
         * the supplied inputs, ensuring the specified order and discrete/continuous classifications are accounted for.
         *
         * @param dag           the directed acyclic graph representing dependencies; must not be null
         * @param nodeOrder     the ordered list of nodes, defining the sequence used; must not be null
         * @param discreteFlags a map indicating whether each node is discrete (true) or continuous (false)
         * @param categoryMap   a map providing a list of category strings for discrete nodes
         */
        public HybridCgPm(Graph dag, List<Node> nodeOrder, Map<Node, Boolean> discreteFlags,
                          Map<Node, List<String>> categoryMap) {
            this.dag = Objects.requireNonNull(dag, "dag");
            this.nodes = nodeOrder.toArray(new Node[0]);

            int n = nodes.length;
            this.isDiscrete = new boolean[n];
            //noinspection unchecked
            this.categories = (List<String>[]) new List<?>[n];
            this.discParents = new int[n][];
            this.contParents = new int[n][];
            this.contParentCutpointsForDiscreteChild = new double[n][][]; // may be null by node

            // Types
            for (int i = 0; i < n; i++) {
                Node v = nodes[i];
                boolean disc = Boolean.TRUE.equals(discreteFlags.get(v));
                isDiscrete[i] = disc;
                categories[i] = disc ? new ArrayList<>(Objects.requireNonNull(categoryMap.get(v))) : null;
            }

            // Parent splits (respect dag ordering, but we keep the exact order returned by dag.getParents(v))
            for (int i = 0; i < n; i++) {
                Node v = nodes[i];
                List<Node> parents = dag.getParents(v);
                List<Integer> dp = new ArrayList<>();
                List<Integer> cp = new ArrayList<>();
                for (Node p : parents) {
                    int j = indexOf(p);
                    if (isDiscrete[j]) dp.add(j);
                    else cp.add(j);
                }
                discParents[i] = dp.stream().mapToInt(Integer::intValue).toArray();
                contParents[i] = cp.stream().mapToInt(Integer::intValue).toArray();
            }
        }

        // --- small local helper (keep near other helpers in PM) ---
        private static int binFromCutpoints(double[] cuts, double v) {
            int b = 0;
            while (b < cuts.length && v > cuts[b]) b++;
            return b; // 0..cuts.length
        }

        /**
         * Retrieves the directed acyclic graph (DAG) associated with this model.
         *
         * @return the DAG representing the structure of this probabilistic model
         */
        public Graph getGraph() {
            return dag;
        }

        /**
         * Retrieves the ordered list of nodes in this probabilistic model.
         *
         * @return the ordered list of nodes
         */
        public Node[] getNodes() {
            return nodes.clone();
        }

        /**
         * Retrieves the index of a given node within the ordered list of nodes.
         *
         * @param v the node to find the index for
         * @return the index of the node, or -1 if not found
         */
        public int indexOf(Node v) {
            for (int i = 0; i < nodes.length; i++) if (nodes[i].equals(v)) return i;
            return -1;
        }

        /**
         * Checks if a node at a given index is discrete.
         *
         * @param nodeIndex the index of the node to check
         * @return true if the node is discrete, false otherwise
         */
        public boolean isDiscrete(int nodeIndex) {
            return isDiscrete[nodeIndex];
        }

        /**
         * Retrieves the list of categories for a discrete node at a given index.
         *
         * @param nodeIndex the index of the discrete node
         * @return the list of categories for the node
         */
        public List<String> getCategories(int nodeIndex) {
            return categories[nodeIndex];
        }

        /**
         * Retrieves the cardinality (number of categories) for a discrete node at a given index.
         *
         * @param nodeIndex the index of the discrete node
         * @return the number of categories for the node
         */
        public int getCardinality(int nodeIndex) {
            if (!isDiscrete[nodeIndex]) throw new IllegalStateException("Not a discrete node: " + nodes[nodeIndex]);
            return categories[nodeIndex].size();
        }

        /**
         * Retrieves the discrete parents of a node at a given index.
         *
         * @param nodeIndex the index of the node
         * @return an array of indices representing discrete parents
         */
        public int[] getDiscreteParents(int nodeIndex) {
            return discParents[nodeIndex];
        }

        /**
         * Retrieves the continuous parents of a node at a given index.
         *
         * @param nodeIndex the index of the node
         * @return an array of indices representing continuous parents
         */
        public int[] getContinuousParents(int nodeIndex) {
            return contParents[nodeIndex];
        }

        /**
         * Sets the continuous parent cutpoints for a specified discrete child node. This method ensures that for each
         * continuous parent of the discrete child, the respective cutpoints are strictly increasing.
         *
         * @param child                 The discrete child node for which the cutpoints will be set.
         * @param cutpointsByContParent A map containing the cutpoints for each continuous parent node. The keys in the
         *                              map are the continuous parent nodes, and the values are arrays of cutpoints,
         *                              which are expected to be strictly increasing.
         * @throws IllegalArgumentException If the specified child is not discrete, if the cutpoints are missing for any
         *                                  continuous parent, or if the provided cutpoints are not strictly
         *                                  increasing.
         */
        public void setContParentCutpointsForDiscreteChild(Node child, Map<Node, double[]> cutpointsByContParent) {
            int y = indexOf(child);
            if (!isDiscrete[y]) throw new IllegalArgumentException("child is not discrete: " + child);
            int[] cps = contParents[y];
            double[][] cuts = new double[cps.length][];
            for (int t = 0; t < cps.length; t++) {
                Node p = nodes[cps[t]];
                double[] edges = Optional.ofNullable(cutpointsByContParent.get(p))
                        .orElseThrow(() -> new IllegalArgumentException("missing cutpoints for parent " + p));
                double[] cloned = edges.clone();
                // sanity: strictly increasing
                for (int e = 1; e < cloned.length; e++)
                    if (!(cloned[e] > cloned[e - 1]))
                        throw new IllegalArgumentException("cutpoints not strictly increasing for parent " + p);
                cuts[t] = cloned;
            }
            contParentCutpointsForDiscreteChild[y] = cuts;
        }

        /**
         * Retrieves the cutpoints for continuous parents of a discrete child node.
         *
         * @param nodeIndex the index of the discrete child node
         * @return an Optional containing the cutpoints array, or empty if not set
         */
        public Optional<double[][]> getContParentCutpointsForDiscreteChild(int nodeIndex) {
            return Optional.ofNullable(contParentCutpointsForDiscreteChild[nodeIndex]);
        }

        /**
         * Retrieves the number of rows in the local table for a given node index.
         *
         * @param nodeIndex the index of the node
         * @return the number of rows in the local table
         */
        public int getNumRows(int nodeIndex) {
            int rows = 1;
            // Discrete parents always contribute.
            for (int p : discParents[nodeIndex]) rows *= getCardinality(p);
            if (isDiscrete[nodeIndex]) {
                // Plus bins for each continuous parent (if any); requires cutpoints to be set.
                double[][] cuts = contParentCutpointsForDiscreteChild[nodeIndex];
                if (contParents[nodeIndex].length > 0) {
                    if (cuts == null)
                        throw new IllegalStateException("cutpoints not set for discrete child with cont parents: " + nodes[nodeIndex]);
                    for (double[] c : cuts) rows *= (c.length + 1);
                }
            }
            return rows;
        }

        /**
         * Computes and returns the row dimensions for a specific node in the network based on its discrete and
         * continuous parents.
         *
         * @param nodeIndex the index of the node for which the row dimensions are to be computed
         * @return an array of integers representing the row dimensions for the given node
         */
        public int[] getRowDims(int nodeIndex) {
            int d = discParents[nodeIndex].length;
            int c = contParents[nodeIndex].length;
            int[] dims = new int[d + (isDiscrete[nodeIndex] ? c : 0)];
            int k = 0;
            for (int p : discParents[nodeIndex]) dims[k++] = getCardinality(p);
            if (isDiscrete[nodeIndex]) {
                double[][] cuts = contParentCutpointsForDiscreteChild[nodeIndex];
                for (int t = 0; t < c; t++) dims[k++] = (cuts == null ? 0 : cuts[t].length + 1);
            }
            return dims;
        }

        /**
         * Computes the row index for a given node in the model based on the state indices of its discrete and
         * continuous parents. The method takes into account the dimensions determined by the parents to compute the
         * correct row index.
         *
         * @param nodeIndex   the index of the node whose row index is to be computed
         * @param discVals    an array of indices representing the states of the discrete parents of the node; the
         *                    length must match the number of discrete parents
         * @param contBinVals an array of indices representing the binned states of the continuous parents of the node;
         *                    required only for discrete nodes with continuous parents, and its length must match the
         *                    number of continuous parents for the node
         * @return the computed row index, in the range [0, getNumRows(nodeIndex) - 1]
         * @throws IllegalArgumentException if the lengths of discVals or contBinVals do not match the required
         *                                  dimensions
         */
        public int getRowIndex(int nodeIndex, int[] discVals, int[] contBinVals) {
            int[] dims = getRowDims(nodeIndex);
            int k = 0, row = 0;
            for (int i = 0; i < discVals.length; i++) {
                row = row * dims[k] + discVals[i];
                k++;
            }
            if (isDiscrete[nodeIndex]) {
                if (contParents[nodeIndex].length != (contBinVals == null ? 0 : contBinVals.length))
                    throw new IllegalArgumentException("contBinVals length mismatch");
                for (int i = 0; i < (contBinVals == null ? 0 : contBinVals.length); i++) {
                    row = row * dims[k] + contBinVals[i];
                    k++;
                }
            }
            return row;
        }

        /**
         * Discretizes the given value of a continuous parent node for a specific discrete child node into a bin index
         * based on predefined cutpoints.
         *
         * @param child      the discrete child node whose continuous parent's value will be discretized
         * @param contParent the continuous parent node associated with the child node
         * @param value      the value of the continuous parent node to discretize
         * @return the bin index (in the range 0 to the number of cutpoints) corresponding to the given value
         * @throws IllegalArgumentException if the specified parent node is not a continuous parent of the child node
         * @throws IllegalStateException    if the cutpoints for the child node have not been set
         */
        public int discretizeFor(Node child, Node contParent, double value) {
            int iChild = indexOf(child);
            int[] cps = contParents[iChild];
            int j = -1;
            for (int t = 0; t < cps.length; t++)
                if (nodes[cps[t]].equals(contParent)) {
                    j = t;
                    break;
                }
            if (j < 0) throw new IllegalArgumentException("not a continuous parent of child");
            double[] cuts = Optional.ofNullable(contParentCutpointsForDiscreteChild[iChild])
                    .orElseThrow(() -> new IllegalStateException("cutpoints not set for child: " + child))[j];
            int bin = 0;
            while (bin < cuts.length && value > cuts[bin]) bin++;
            return bin; // 0..cuts.length
        }

        /**
         * Populate cutpoints for each continuous parent of a DISCRETE child using equal-frequency binning.
         *
         * @param child         discrete child
         * @param data          data set that contains all variables by name
         * @param binsPerParent number of bins to use for each continuous parent (>=2 recommended)
         */
        public void autoCutpointsForDiscreteChild(Node child, DataSet data, int binsPerParent) {
            if (binsPerParent < 2) throw new IllegalArgumentException("binsPerParent >= 2");
            int y = indexOf(child);
            if (!isDiscrete[y]) throw new IllegalArgumentException("child is not discrete: " + child);

            int[] cps = getContinuousParents(y);
            if (cps.length == 0) return;

            double[][] cuts = new double[cps.length][];
            for (int t = 0; t < cps.length; t++) {
                Node p = nodes[cps[t]];
                int col = data.getColumn(p);
                if (col < 0) throw new IllegalArgumentException("Data is missing column for parent: " + p.getName());

                // collect non-missing
                List<Double> vals = new ArrayList<>(data.getNumRows());
                for (int r = 0; r < data.getNumRows(); r++) {
                    double v = data.getDouble(r, col);
                    if (!Double.isNaN(v)) vals.add(v);
                }
                if (vals.size() < binsPerParent) {
                    // fall back to unique-sorted midpoints
                    double[] unique = vals.stream().distinct().sorted().mapToDouble(d -> d).toArray();
                    if (unique.length <= 1) {
                        cuts[t] = new double[0]; // all in one bin
                        continue;
                    }
                    int m = Math.min(unique.length - 1, binsPerParent - 1);
                    double[] cp = new double[m];
                    int step = (unique.length - 1) / m;
                    for (int k = 0; k < m; k++) {
                        int i = (k + 1) * step;
                        cp[k] = 0.5 * (unique[i - 1] + unique[i]);
                    }
                    Arrays.sort(cp);
                    cuts[t] = cp;
                    continue;
                }

                // equal-frequency cutpoints at quantiles 1/b, 2/b, ... (b-1)/b
                Collections.sort(vals);
                double[] cp = new double[binsPerParent - 1];
                for (int k = 1; k < binsPerParent; k++) {
                    double q = k / (double) binsPerParent;
                    int idx = Math.min(vals.size() - 1, Math.max(0, (int) Math.round(q * (vals.size() - 1))));
                    cp[k - 1] = vals.get(idx);
                }
                // ensure strictly increasing (nudge ties)
                for (int k = 1; k < cp.length; k++) {
                    if (!(cp[k] > cp[k - 1])) cp[k] = Math.nextUp(cp[k - 1]);
                }
                cuts[t] = cp;
            }
            // install
            Map<Node, double[]> map = new HashMap<>();
            for (int t = 0; t < cps.length; t++) map.put(nodes[cps[t]], cuts[t]);
            setContParentCutpointsForDiscreteChild(child, map);
        }

        /**
         * Compute the local-table row index for a single data case.
         *
         * <p>Rules:
         * <ul>
         *   <li>Discrete parents contribute their category indices directly.</li>
         *   <li>If the child is DISCRETE and has continuous parents, each continuous parent
         *       is discretized using the stored cutpoints to a bin index (0..bins-1),
         *       and those bin indices extend the row index.</li>
         *   <li>If the child is CONTINUOUS, only discrete parents contribute (continuous
         *       parents do not add dimensions for continuous children).</li>
         * </ul>
         *
         * @param nodeIndex        index of the child (in this PM's node order)
         * @param discParentStates length must equal getDiscreteParents(nodeIndex).length; each entry in [0, card-1]
         * @param contParentValues raw values for the child's continuous parents; for a DISCRETE child length must equal
         *                         getContinuousParents(nodeIndex).length; ignored for CONTINUOUS child
         * @return row index in [0, getNumRows(nodeIndex)-1]
         */
        public int rowIndexForCase(int nodeIndex, int[] discParentStates, double[] contParentValues) {
            int[] dps = getDiscreteParents(nodeIndex);
            int[] cps = getContinuousParents(nodeIndex);

            if (discParentStates == null || discParentStates.length != dps.length) {
                throw new IllegalArgumentException("discParentStates length mismatch: expected " + dps.length);
            }

            int row = 0;
            int[] dims = getRowDims(nodeIndex);
            int k = 0; // dims cursor

            // 1) Discrete parent states
            for (int i = 0; i < dps.length; i++) {
                int s = discParentStates[i];
                int card = getCardinality(dps[i]);
                if (s < 0 || s >= card) {
                    throw new IllegalArgumentException("discParentStates[" + i + "] out of range 0.." + (card - 1));
                }
                row = row * dims[k] + s;
                k++;
            }

            // 2) Continuous-parent bins (only if child is DISCRETE)
            if (isDiscrete(nodeIndex)) {
                if (cps.length > 0) {
                    if (contParentValues == null || contParentValues.length != cps.length) {
                        throw new IllegalArgumentException("contParentValues length mismatch: expected " + cps.length);
                    }
                    double[][] cuts = getContParentCutpointsForDiscreteChild(nodeIndex)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Cutpoints not set for discrete child with continuous parents: " + nodes[nodeIndex]));
                    for (int t = 0; t < cps.length; t++) {
                        int bin = binFromCutpoints(cuts[t], contParentValues[t]);
                        int binsHere = dims[k];
                        if (bin < 0 || bin >= binsHere) {
                            throw new IllegalStateException("Computed bin out of range for parent #" + t + ": " + bin + " / " + binsHere);
                        }
                        row = row * dims[k] + bin;
                        k++;
                    }
                }
            }
            return row;
        }

        /**
         * Convenience overload when the child has NO discrete parents. Useful for tests like: pm.rowIndexForCase(yIdx,
         * new double[]{ xVal }).
         *
         * @param nodeIndex        child index
         * @param contParentValues raw values for the child's continuous parents
         * @return the index.
         */
        public int rowIndexForCase(int nodeIndex, double[] contParentValues) {
            if (getDiscreteParents(nodeIndex).length != 0) {
                throw new IllegalArgumentException("Child has discrete parents—use rowIndexForCase(node,int[],double[])");
            }
            return rowIndexForCase(nodeIndex, new int[0], contParentValues);
        }

        /**
         * Convenience overload that reads parent states/values from a DataSet row. Child's discrete-parent states are
         * taken from integer columns; continuous-parent values are taken from double columns and binned (if the child
         * is discrete).
         *
         * @param nodeIndex child index
         * @param data      dataset containing all variables (by name)
         * @param row       row index into the dataset
         * @return the row.
         */
        public int rowIndexForCase(int nodeIndex, edu.cmu.tetrad.data.DataSet data, int row) {
            int[] dps = getDiscreteParents(nodeIndex);
            int[] cps = getContinuousParents(nodeIndex);

            int[] discStates = new int[dps.length];
            for (int i = 0; i < dps.length; i++) {
                Node parent = nodes[dps[i]];
                int col = data.getColumn(parent);
                if (col < 0) {
                    Node byName = data.getVariable(parent.getName());
                    if (byName == null)
                        throw new IllegalArgumentException("Dataset missing parent: " + parent.getName());
                    col = data.getColumn(byName);
                }
                discStates[i] = data.getInt(row, col);
            }

            double[] contVals = null;
            if (isDiscrete(nodeIndex) && cps.length > 0) {
                contVals = new double[cps.length];
                for (int t = 0; t < cps.length; t++) {
                    Node parent = nodes[cps[t]];
                    int col = data.getColumn(parent);
                    if (col < 0) {
                        Node byName = data.getVariable(parent.getName());
                        if (byName == null)
                            throw new IllegalArgumentException("Dataset missing parent: " + parent.getName());
                        col = data.getColumn(byName);
                    }
                    contVals[t] = data.getDouble(row, col);
                }
            }

            return rowIndexForCase(nodeIndex, discStates, contVals);
        }

        /**
         * Retrieves the parents of a node in the hybrid causal graph model.
         *
         * @param y the index of the node
         * @return an array of indices representing the parents of the node
         */
        public int[] getParents(int y) {
            Node child = nodes[y];
            List<Node> parents = dag.getParents(child);
            return parents.stream()
                    .mapToInt(this::indexOf)
                    .toArray();
        }
    }

    // ======== IM (numbers) ========
    public static final class HybridCgIm implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /**
         * A probabilistic model used for hybrid causal graph inference. This field represents an instance of
         * {@link HybridCgPm}, providing the necessary structure and parameters to define the hybrid causal graph
         * model.
         * <p>
         * The {@code pm} is immutable, ensuring the consistency and integrity of the model throughout its usage in the
         * associated {@link HybridCgIm} instance.
         * <p>
         * Typical operations leveraging this field include accessing the model’s parameters, retrieving conditional
         * probabilities, and determining node dependencies within the causal graph.
         * <p>
         * This field encapsulates all the key information required to perform probabilistic reasoning and to simulate
         * observations for both discrete and continuous variables in the hybrid causal structure.
         */
        private final HybridCgPm pm;
        /**
         * Continuous child: for node y, params[y] is rows x (m+2) where m = #cont parents; cols = [intercept,
         * coeffs..., variance]
         */
        private final double[][][] contParams; // null for discrete children
        /**
         * Discrete child: probs[y] is rows x card(y)
         */
        private final double[][][] discProbs;   // null for continuous children

        /**
         * Constructs a HybridCgIm instance from a HybridCgPm.
         *
         * @param pm the probabilistic model
         */
        public HybridCgIm(HybridCgPm pm) {
            this.pm = pm;
            int n = pm.nodes.length;
            this.contParams = new double[n][][];
            this.discProbs = new double[n][][];
            for (int y = 0; y < n; y++) {
                int rows = pm.getNumRows(y);
                if (pm.isDiscrete[y]) {
                    this.discProbs[y] = new double[rows][pm.getCardinality(y)];
                } else {
                    int m = pm.getContinuousParents(y).length;
                    this.contParams[y] = new double[rows][m + 2];
                    for (int r = 0; r < rows; r++) this.contParams[y][r][m + 1] = Double.NaN; // variance unset
                }
            }
        }

        /**
         * Kahn's algorithm using only parent links from Graph.
         */
        private static int[] topologicalOrder(Graph g, Node[] nodes) {
            int n = nodes.length;
            Map<Node, Integer> idx = new HashMap<>();
            for (int i = 0; i < n; i++) idx.put(nodes[i], i);
            int[] indeg = new int[n];
            List<List<Integer>> children = new ArrayList<>(n);
            for (int i = 0; i < n; i++) children.add(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                Node v = nodes[i];
                for (Node p : g.getParents(v)) indeg[i]++;
                for (Node ch : g.getChildren(nodes[i])) children.get(i).add(idx.get(ch));
            }
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int i = 0; i < n; i++) if (indeg[i] == 0) q.add(i);
            int[] order = new int[n];
            int k = 0;
            while (!q.isEmpty()) {
                int u = q.removeFirst();
                order[k++] = u;
                for (int v : children.get(u)) if (--indeg[v] == 0) q.add(v);
            }
            if (k != n) throw new IllegalStateException("Graph has a cycle or missing nodes in topo sort");
            return order;
        }

        private static int sampleCategorical(double[] probs, Random rng) {
            double u = rng.nextDouble();
            double c = 0.0;
            for (int k = 0; k < probs.length; k++) {
                c += probs[k];
                if (u <= c) return k;
            }
            return probs.length - 1; // guard for tiny rounding error
        }

        private static int binFromCutpoints(double[] cuts, double v) {
            int b = 0;
            while (b < cuts.length && v > cuts[b]) b++;
            return b; // 0..cuts.length
        }

        /**
         * Computes the row index for a given data case in the hybrid causal graph model.
         *
         * @param pm        the probabilistic model
         * @param nodeIndex the index of the node
         * @param data      the dataset
         * @param row       the row index in the dataset
         * @param colIndex  the column indices for the parents
         * @return the row index for the data case
         */
        public static int rowIndexForCase(HybridCgPm pm, int nodeIndex, DataSet data, int row, int[] colIndex) {
            int[] dps = pm.getDiscreteParents(nodeIndex);
            int[] cps = pm.getContinuousParents(nodeIndex);
            int[] discVals = new int[dps.length];
            for (int i = 0; i < dps.length; i++) discVals[i] = data.getInt(row, colIndex[dps[i]]);
            int[] contBins = null;
            if (pm.isDiscrete(nodeIndex) && cps.length > 0) {
                contBins = new int[cps.length];
                double[][] cuts = pm.getContParentCutpointsForDiscreteChild(nodeIndex).orElse(null);
                if (cuts == null) throw new IllegalStateException("cutpoints not set for child " + pm.nodes[nodeIndex]);
//                for (int t = 0; t < cps.length; t++)
//                    contBins[t] = HybridEstimator.binFromCutpoints(cuts[t], data.getDouble(row, colIndex[cps[t]]));
                // In HybridCgIm.rowIndexForCase(...)
                for (int t = 0; t < cps.length; t++)
                    contBins[t] = binFromCutpoints(cuts[t],
                            data.getDouble(row, colIndex[cps[t]])); // <-- use the local helper
            }


            return pm.getRowIndex(nodeIndex, discVals, contBins);
        }

        /**
         * Retrieves the probabilistic model associated with this hybrid causal graph model.
         *
         * @return the probabilistic model represented by a {@link HybridCgPm} instance
         */
        public HybridCgPm getPm() {
            return pm;
        }

        /**
         * Retrieves the probability of a specific category for a discrete child node.
         *
         * @param nodeIndex the index of the discrete child node
         * @param rowIndex  the row index in the local table
         * @param yCategory the category index for the discrete child
         * @return the probability of the specified category
         */
        public double getProbability(int nodeIndex, int rowIndex, int yCategory) {
            return discProbs[nodeIndex][rowIndex][yCategory];
        }

        /**
         * Sets the probability of a specific category for a discrete child node.
         *
         * @param nodeIndex the index of the discrete child node
         * @param rowIndex  the row index in the local table
         * @param yCategory the category index for the discrete child
         * @param p         the probability value to set
         */
        public void setProbability(int nodeIndex, int rowIndex, int yCategory, double p) {
            discProbs[nodeIndex][rowIndex][yCategory] = p;
        }

        /**
         * Normalizes the probabilities in a row of the local table for a discrete child node.
         *
         * @param nodeIndex the index of the discrete child node
         * @param rowIndex  the row index in the local table
         */
        public void normalizeRow(int nodeIndex, int rowIndex) {
            double[] row = discProbs[nodeIndex][rowIndex];
            double s = 0.0;
            for (double v : row) s += v;
            if (s <= 0) {
                Arrays.fill(row, 1.0 / row.length);
                return;
            }
            for (int j = 0; j < row.length; j++) row[j] /= s;
        }

        /**
         * Retrieves the intercept value for a continuous child node.
         *
         * @param nodeIndex the index of the continuous child node
         * @param rowIndex  the row index in the local table
         * @return the intercept value
         */
        public double getIntercept(int nodeIndex, int rowIndex) {
            return contParams[nodeIndex][rowIndex][0];
        }

        /**
         * Sets the intercept value for a continuous child node.
         *
         * @param nodeIndex the index of the continuous child node
         * @param rowIndex  the row index in the local table
         * @param v         the intercept value to set
         */
        public void setIntercept(int nodeIndex, int rowIndex, double v) {
            contParams[nodeIndex][rowIndex][0] = v;
        }

        /**
         * Retrieves the coefficient value for a continuous child node.
         *
         * @param nodeIndex            the index of the continuous child node
         * @param rowIndex             the row index in the local table
         * @param contParentOrderIndex the index of the continuous parent
         * @return the coefficient value
         */
        public double getCoefficient(int nodeIndex, int rowIndex, int contParentOrderIndex) {
            return contParams[nodeIndex][rowIndex][1 + contParentOrderIndex];
        }

        /**
         * Sets the coefficient value for a continuous child node.
         *
         * @param nodeIndex            the index of the continuous child node
         * @param rowIndex             the row index in the local table
         * @param contParentOrderIndex the index of the continuous parent
         * @param v                    the coefficient value to set
         */
        public void setCoefficient(int nodeIndex, int rowIndex, int contParentOrderIndex, double v) {
            contParams[nodeIndex][rowIndex][1 + contParentOrderIndex] = v;
        }

        /**
         * Retrieves the variance value for a continuous child node.
         *
         * @param nodeIndex the index of the continuous child node
         * @param rowIndex  the row index in the local table
         * @return the variance value
         */
        public double getVariance(int nodeIndex, int rowIndex) {
            int m = pm.getContinuousParents(nodeIndex).length;
            return contParams[nodeIndex][rowIndex][1 + m];
        }

        /**
         * Sets the variance value for a continuous child node.
         *
         * @param nodeIndex the index of the continuous child node
         * @param rowIndex  the row index in the local table
         * @param v         the variance value to set
         */
        public void setVariance(int nodeIndex, int rowIndex, double v) {
            int m = pm.getContinuousParents(nodeIndex).length;
            contParams[nodeIndex][rowIndex][1 + m] = v;
        }

        /**
         * Convert a sampled matrix into a Tetrad DataSet with the provided node ordering. The node list must be a
         * permutation of the PM's nodes; types (discrete/continuous) are taken from the PM.
         *
         * @param sample the sampled matrix
         */
        public DataSet toDataSet(Sample sample) {
            List<Node> nodes = HybridCgVars.materializeDataVariables(pm);

            Objects.requireNonNull(sample, "sample");

            int n = sample.rows;

            MixedDataBox box = new MixedDataBox(nodes, n);
            DataSet ds = new BoxDataSet(box, nodes);

            // Build PM name -> PM index once
            Map<String, Integer> pmIdxByName = new HashMap<>(pm.getNodes().length * 2);
            for (int i = 0; i < pm.getNodes().length; i++) {
                pmIdxByName.put(pm.getNodes()[i].getName(), i);
            }

            // Map each output column to the PM index
            int[] pmIndex = new int[nodes.size()];
            for (int j = 0; j < nodes.size(); j++) {
                Integer idx = pmIdxByName.get(nodes.get(j).getName());
                if (idx == null) throw new IllegalArgumentException("node not in PM: " + nodes.get(j));
                pmIndex[j] = idx;
            }

            for (int r = 0; r < n; r++) {
                for (int j = 0; j < nodes.size(); j++) {
                    int y = pmIndex[j];
                    if (pm.isDiscrete[y]) {
                        ds.setInt(r, j, sample.discrete[y][r]);
                    } else {
                        ds.setDouble(r, j, sample.continuous[y][r]);
                    }
                }
            }
            return ds;
        }

        /**
         * Simulate {@code n} rows from this IM.
         * <ul>
         *   <li>Order: a topological order over the DAG is computed internally.</li>
         *   <li>Discrete child with continuous parents: uses <b>discretized bins</b> of the parent values to select the CPT row.</li>
         *   <li>Continuous child: for each discrete-parent stratum, samples from the fitted Gaussian regression.</li>
         * </ul>
         */
        public Sample sample(int n, Random rng) {
            if (rng == null) rng = new Random();
            final int p = pm.nodes.length;

            // Precompute topo order
            int[] topo = topologicalOrder(pm.getGraph(), pm.nodes);

            // Allocate columns
            double[][] contCols = new double[p][];
            int[][] discCols = new int[p][];
            for (int j = 0; j < p; j++) {
                if (pm.isDiscrete(j)) discCols[j] = new int[n];
                else contCols[j] = new double[n];
            }

            // For quick parent lookup per node
            int[][] dps = new int[p][];
            int[][] cps = new int[p][];
            for (int j = 0; j < p; j++) {
                dps[j] = pm.getDiscreteParents(j);
                cps[j] = pm.getContinuousParents(j);
            }

            for (int r = 0; r < n; r++) {
                for (int idx = 0; idx < topo.length; idx++) {
                    int y = topo[idx];
                    if (pm.isDiscrete(y)) {
                        // build disc parent values
                        int[] discVals = new int[dps[y].length];
                        for (int i = 0; i < dps[y].length; i++) discVals[i] = discCols[dps[y][i]][r];
                        // build cont parent bins (if any)
                        int[] contBins = null;
                        if (cps[y].length > 0) {
                            contBins = new int[cps[y].length];
                            double[][] cuts = pm.getContParentCutpointsForDiscreteChild(y)
                                    .orElseThrow(() -> new IllegalStateException("cutpoints not set for child " + pm.nodes[y]));
                            for (int t = 0; t < cps[y].length; t++) {
                                double v = contCols[cps[y][t]][r];
                                contBins[t] = binFromCutpoints(cuts[t], v);
                            }
                        }
                        int rowIndex = pm.getRowIndex(y, discVals, contBins);
                        discCols[y][r] = sampleCategorical(discProbs[y][rowIndex], rng);
                    } else {
                        // Continuous child
                        int[] discVals = new int[dps[y].length];
                        for (int i = 0; i < dps[y].length; i++) discVals[i] = discCols[dps[y][i]][r];
                        int rowIndex = pm.getRowIndex(y, discVals, null);
                        double mean = getIntercept(y, rowIndex);
                        for (int t = 0; t < cps[y].length; t++)
                            mean += getCoefficient(y, rowIndex, t) * contCols[cps[y][t]][r];
                        double var = getVariance(y, rowIndex);
                        double sd = var > 0 ? Math.sqrt(var) : 0.0;
                        contCols[y][r] = mean + sd * rng.nextGaussian();
                    }
                }
            }
            return new Sample(contCols, discCols, n);
        }

        /**
         * Returns a string representation of the HybridIm model.
         *
         * @return a string representation of the HybridIm model
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("HybridIm{\n");
            Node[] nodes = pm.getNodes();

            for (int y = 0; y < nodes.length; y++) {
                Node child = nodes[y];
                int[] dps = pm.getDiscreteParents(y);
                int[] cps = pm.getContinuousParents(y);
                int rows = pm.getNumRows(y);

                sb.append("  ")
                        .append(child.getName())
                        .append(pm.isDiscrete(y) ? " (disc)" : " (cont)");

                if (dps.length > 0) {
                    sb.append("  discParents=");
                    for (int i = 0; i < dps.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(nodes[dps[i]].getName());
                    }
                }
                if (cps.length > 0) {
                    sb.append("  contParents=");
                    for (int i = 0; i < cps.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(nodes[cps[i]].getName());
                    }
                }
                sb.append("  rows=").append(rows).append("\n");

                int maxRowsToShow = Math.min(rows, 6);

                if (pm.isDiscrete(y)) {
                    int K = pm.getCardinality(y);
                    int[] dims = pm.getRowDims(y);
                    sb.append("    CPT dims (radices) = ")
                            .append(Arrays.toString(dims))
                            .append(" -> K=")
                            .append(K)
                            .append("\n");

                    for (int r = 0; r < maxRowsToShow; r++) {
                        sb.append("    row ")
                                .append(r)
                                .append(": [");
                        double[] pr = discProbs[y][r];
                        for (int k = 0; k < K; k++) {
                            if (k > 0) sb.append(", ");
                            sb.append(String.format(Locale.US, "%.3f", pr[k]));
                        }
                        sb.append("]\n");
                    }
                    if (rows > maxRowsToShow) {
                        sb.append("    ... (")
                                .append(rows - maxRowsToShow)
                                .append(" more rows)\n");
                    }
                } else {
                    sb.append("    columns: [intercept");
                    for (int j = 0; j < cps.length; j++) {
                        sb.append(", ").append(nodes[cps[j]].getName());
                    }
                    sb.append(", variance]\n");

                    for (int r = 0; r < maxRowsToShow; r++) {
                        sb.append("    row ")
                                .append(r)
                                .append(": [");
                        double[] row = contParams[y][r];
                        for (int c = 0; c < row.length; c++) {
                            if (c > 0) sb.append(", ");
                            sb.append(String.format(Locale.US, "%.3f", row[c]));
                        }
                        sb.append("]\n");
                    }
                    if (rows > maxRowsToShow) {
                        sb.append("    ... (")
                                .append(rows - maxRowsToShow)
                                .append(" more rows)\n");
                    }
                }
            }
            sb.append("}\n");
            return sb.toString();
        }

        /**
         * Represents a data structure for storing a sampled dataset. This is used in conjunction with the HybridCgIm
         * model to hold both continuous and discrete variables for a specified number of rows.
         */
        // ===== Sampler =====
        public record Sample(double[][] continuous, int[][] discrete, int rows) {
        }

        // ======== Estimator (MLE) ========
        public static final class HybridEstimator {
            /**
             * Dirichlet pseudo-count for discrete CPTs.
             */
            private final double alpha;
            /**
             * If true, share a single variance across rows for each continuous child (helps when rows are sparse).
             */
            private final boolean shareVarianceAcrossRows;

            public HybridEstimator() {
                this(1.0, false);
            }

            public HybridEstimator(double alpha, boolean shareVarianceAcrossRows) {
                this.alpha = alpha;
                this.shareVarianceAcrossRows = shareVarianceAcrossRows;
            }

            private static int binFromCutpoints(double[] cuts, double v) {
                int b = 0;
                while (b < cuts.length && v > cuts[b]) b++;
                return b; // 0..cuts.length
            }

            /**
             * Estimates a HybridCgIm model from the given HybridCgPm and DataSet.
             *
             * @param pm   the HybridCgPm
             * @param data the DataSet
             * @return the estimated HybridCgIm model
             */
            public HybridCgIm mle(HybridCgPm pm, DataSet data) {
                HybridCgIm im = new HybridCgIm(pm);
                Node[] nodes = pm.nodes;

                // Build a name→column map once
                Map<String, Integer> colByName = new HashMap<>(data.getNumColumns() * 2);
                for (int c = 0; c < data.getNumColumns(); c++) {
                    colByName.put(data.getVariable(c).getName(), c);
                }

                // Resolve each PM node to a column (by exact name)
                int[] colIndex = new int[nodes.length];
                for (int j = 0; j < nodes.length; j++) {
                    Integer c = colByName.get(nodes[j].getName());
                    if (c == null) {
                        throw new IllegalArgumentException(
                                "Dataset is missing variable required by PM: " + nodes[j].getName());
                    }
                    colIndex[j] = c;
                }

                for (int y = 0; y < nodes.length; y++) {
                    if (pm.isDiscrete[y]) fitDiscreteChild(pm, im, data, y, colIndex);
                    else fitContinuousChild(pm, im, data, y, colIndex);
                }
                return im;
            }

            private void fitDiscreteChild(HybridCgPm pm, HybridCgIm im, DataSet data, int y, int[] colIndex) {
                int[] dps = pm.getDiscreteParents(y);
                int[] cps = pm.getContinuousParents(y);
                int rows = pm.getNumRows(y);
                int K = pm.getCardinality(y);

                double[][] counts = new double[rows][K];

                // For each case, compute rowIndex from (disc parents, discretized cont parents), then increment count for Y
                for (int r = 0; r < data.getNumRows(); r++) {
                    int yVal = data.getInt(r, colIndex[y]);

                    int[] discVals = new int[dps.length];
                    for (int i = 0; i < dps.length; i++) discVals[i] = data.getInt(r, colIndex[dps[i]]);

                    int[] contBins = new int[cps.length];
                    if (cps.length > 0) {
                        double[][] cuts = pm.getContParentCutpointsForDiscreteChild(y)
                                .orElseThrow(() -> new IllegalStateException("cutpoints not set for child " + pm.nodes[y]));
                        for (int t = 0; t < cps.length; t++) {
                            double v = data.getDouble(r, colIndex[cps[t]]);
                            contBins[t] = binFromCutpoints(cuts[t], v);
                        }
                    }

                    int row = pm.getRowIndex(y, discVals, contBins);
                    counts[row][yVal] += 1.0;
                }

                // Convert to probabilities with Dirichlet(alpha)
                for (int row = 0; row < rows; row++) {
                    double s = 0.0;
                    for (int k = 0; k < K; k++) s += counts[row][k] + alpha;
                    for (int k = 0; k < K; k++) im.setProbability(y, row, k, (counts[row][k] + alpha) / s);
                }
            }

            private void fitContinuousChild(HybridCgPm pm, HybridCgIm im, DataSet data, int y, int[] colIndex) {
                int[] dps = pm.getDiscreteParents(y);
                int[] cps = pm.getContinuousParents(y);
                int rows = pm.getNumRows(y); // product over discrete-parents only
                int m = cps.length;

                // Group rows by discrete-parent configuration
                Map<IntVector, List<Integer>> groups = new HashMap<>();
                for (int r = 0; r < data.getNumRows(); r++) {
                    int[] discVals = new int[dps.length];
                    for (int i = 0; i < dps.length; i++) discVals[i] = data.getInt(r, colIndex[dps[i]]);
                    groups.computeIfAbsent(new IntVector(discVals), k -> new ArrayList<>()).add(r);
                }

                // For each group, OLS Y ~ [1, continuous parents]; variance from residuals. Optionally share variance.
                double pooledSS = 0.0;
                int pooledDF = 0;
                double[] sigma2Row = new double[rows];
                Arrays.fill(sigma2Row, Double.NaN);

                for (Map.Entry<IntVector, List<Integer>> e : groups.entrySet()) {
                    int row = pm.getRowIndex(y, e.getKey().values, null);
                    List<Integer> cases = e.getValue();
                    int n = cases.size();
                    if (n == 0) continue;

                    // Build X (n x (m+1)), yvec (n)
                    double[][] X = new double[n][m + 1];
                    double[] yv = new double[n];
                    for (int i = 0; i < n; i++) {
                        int r = cases.get(i);
                        X[i][0] = 1.0;
                        for (int t = 0; t < m; t++) X[i][1 + t] = data.getDouble(r, colIndex[cps[t]]);
                        yv[i] = data.getDouble(r, colIndex[y]);
                    }

                    // OLS via EJML
                    SimpleMatrix Xm = new SimpleMatrix(X);
                    SimpleMatrix ym = new SimpleMatrix(n, 1, true, yv);
                    SimpleMatrix beta = Xm.pseudoInverse().mult(ym); // (m+1) x 1

                    double intercept = beta.get(0);
                    im.setIntercept(y, row, intercept);
                    for (int t = 0; t < m; t++) im.setCoefficient(y, row, t, beta.get(1 + t));

                    // Residual variance
                    SimpleMatrix resid = ym.minus(Xm.mult(beta));
                    double rss = resid.elementPower(2.0).elementSum();
                    int df = Math.max(1, n - (m + 1));
                    double s2 = rss / df;
                    im.setVariance(y, row, s2);

                    pooledSS += rss;
                    pooledDF += df;
                    sigma2Row[row] = s2;
                }

                if (shareVarianceAcrossRows) {
                    double s2 = pooledDF > 0 ? (pooledSS / pooledDF) : 1.0;
                    for (int row = 0; row < rows; row++) im.setVariance(y, row, s2);
                }
            }
        }

        // ======== Small helper for map keys ========
        private static final class IntVector {
            final int[] values;

            IntVector(int[] v) {
                this.values = v.clone();
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof IntVector iv && Arrays.equals(values, iv.values);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(values);
            }
        }
    }
}

