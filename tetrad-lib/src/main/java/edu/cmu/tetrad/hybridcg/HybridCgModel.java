///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

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

    // ======== PM (parametric model/skeleton & shapes) ========
    public static final class HybridCgPm implements Pm, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Graph dag;
        private final Node[] nodes;                // fixed node order

        // Variable typing and categories (for discrete variables only)
        private final boolean[] isDiscrete;
        private final List<String>[] categories;   // null for continuous; else size = cardinality

        // Parent lists split by type, pointing to indices into nodes[]
        private final int[][] discParents;
        private final int[][] contParents;

        // For DISCRETE children ONLY: if a continuous parent exists, we discretize it.
        // Store per (child, contParentIndexInOrder) the monotonically increasing cutpoints.
        // bins = cutpoints.length + 1
        private final double[][][] contParentCutpointsForDiscreteChild;

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

        public Graph getGraph() {
            return dag;
        }

        public Node[] getNodes() {
            return nodes.clone();
        }

        public int indexOf(Node v) {
            for (int i = 0; i < nodes.length; i++) if (nodes[i].equals(v)) return i;
            return -1;
        }

        public boolean isDiscrete(int nodeIndex) {
            return isDiscrete[nodeIndex];
        }

        public List<String> getCategories(int nodeIndex) {
            return categories[nodeIndex];
        }

        public int getCardinality(int nodeIndex) {
            return categories[nodeIndex].size();
        }

        public int[] getDiscreteParents(int nodeIndex) {
            return discParents[nodeIndex];
        }

        public int[] getContinuousParents(int nodeIndex) {
            return contParents[nodeIndex];
        }

        /**
         * For a DISCRETE child that has k continuous parents, provide cutpoints for each parent. bins =
         * cutpoints.length + 1. All arrays are copied defensively.
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

        public Optional<double[][]> getContParentCutpointsForDiscreteChild(int nodeIndex) {
            return Optional.ofNullable(contParentCutpointsForDiscreteChild[nodeIndex]);
        }

        /**
         * Number of rows in the local table for nodeIndex (shape contract).
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
         * Dimensions (radices) for mixed row indexing: [disc parents..., cont-parent-bins...]
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
         * Mixed row indexer: map parent <em>state indices</em> to a row number.
         *
         * @param nodeIndex   the child
         * @param discVals    length = #discParents(child), each in [0, card-1]
         * @param contBinVals length = #contParents(child) <b>iff child is discrete</b>; bin index in [0, bins-1]
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
         * Discretize a continuous parent value for a DISCRETE child using the stored cutpoints.
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
    }

    // ======== IM (numbers) ========
    public static final class HybridCgIm implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final HybridCgPm pm;

        // Continuous child: for node y, params[y] is rows x (m+2) where m = #cont parents; cols = [intercept, coeffs..., variance]
        private final double[][][] contParams; // null for discrete children

        // Discrete child: probs[y] is rows x card(y)
        private final double[][][] discProbs;   // null for continuous children

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

        // ======== Convenience: compute row indices on-the-fly for a data case ========
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
                for (int t = 0; t < cps.length; t++)
                    contBins[t] = HybridEstimator.binFromCutpoints(cuts[t], data.getDouble(row, colIndex[cps[t]]));
            }
            return pm.getRowIndex(nodeIndex, discVals, contBins);
        }

        public HybridCgPm getPm() {
            return pm;
        }

        // ===== Discrete child accessors =====
        public double getProbability(int nodeIndex, int rowIndex, int yCategory) {
            return discProbs[nodeIndex][rowIndex][yCategory];
        }

        public void setProbability(int nodeIndex, int rowIndex, int yCategory, double p) {
            discProbs[nodeIndex][rowIndex][yCategory] = p;
        }

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

        // ===== Continuous child accessors =====
        public double getIntercept(int nodeIndex, int rowIndex) {
            return contParams[nodeIndex][rowIndex][0];
        }

        public void setIntercept(int nodeIndex, int rowIndex, double v) {
            contParams[nodeIndex][rowIndex][0] = v;
        }

        // ===== DataSet builder =====

        public double getCoefficient(int nodeIndex, int rowIndex, int contParentOrderIndex) {
            return contParams[nodeIndex][rowIndex][1 + contParentOrderIndex];
        }

        public void setCoefficient(int nodeIndex, int rowIndex, int contParentOrderIndex, double v) {
            contParams[nodeIndex][rowIndex][1 + contParentOrderIndex] = v;
        }

        public double getVariance(int nodeIndex, int rowIndex) {
            int m = pm.getContinuousParents(nodeIndex).length;
            return contParams[nodeIndex][rowIndex][1 + m];
        }

        public void setVariance(int nodeIndex, int rowIndex, double v) {
            int m = pm.getContinuousParents(nodeIndex).length;
            contParams[nodeIndex][rowIndex][1 + m] = v;
        }

        /**
         * Convert a sampled matrix into a Tetrad DataSet with the provided node ordering. The node list must be a
         * permutation of the PM's nodes; types (discrete/continuous) are taken from the PM.
         */
        public DataSet toDataSet(Sample sample, List<Node> nodeOrder) {
            Objects.requireNonNull(sample, "sample");
            Objects.requireNonNull(nodeOrder, "nodeOrder");
            if (nodeOrder.size() != pm.nodes.length) throw new IllegalArgumentException("nodeOrder size mismatch");

            int n = sample.rows;
            // Build a MixedDataBox so we can store ints for discrete and doubles for continuous
            MixedDataBox box = new MixedDataBox(nodeOrder, n);
            DataSet ds = new BoxDataSet(box, nodeOrder);

            // Map external order to pm indices once
            int[] pmIndex = new int[nodeOrder.size()];
            for (int j = 0; j < nodeOrder.size(); j++) {
                pmIndex[j] = pm.indexOf(nodeOrder.get(j));
                if (pmIndex[j] < 0) throw new IllegalArgumentException("node not in PM: " + nodeOrder.get(j));
            }

            for (int r = 0; r < n; r++) {
                for (int j = 0; j < nodeOrder.size(); j++) {
                    int y = pmIndex[j];
                    if (pm.isDiscrete[y]) {
                        int val = sample.discrete[y][r];
                        ds.setInt(r, j, val);
                    } else {
                        double val = sample.continuous[y][r];
                        ds.setDouble(r, j, val);
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

        // ===== Sampler =====
        public static final class Sample {
            public final double[][] continuous;
            public final int[][] discrete;
            public final int rows;

            Sample(double[][] contCols, int[][] discCols, int n) {
                this.continuous = contCols;
                this.discrete = discCols;
                this.rows = n;
            }
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

            public HybridCgIm mle(HybridCgPm pm, DataSet data) {
                HybridCgIm im = new HybridCgIm(pm);
                Node[] nodes = pm.nodes;

                // Precompute variable columns and discrete code maps
                int[] colIndex = new int[nodes.length];
                for (int j = 0; j < nodes.length; j++) colIndex[j] = data.getColumn(nodes[j]);

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

