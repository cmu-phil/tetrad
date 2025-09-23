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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Instance-specific discrete BIC score for FGES-like local scoring.
 * <p>
 * Uses standard discrete BIC: log-likelihood from counts minus 0.5 * penaltyDiscount * (#params) * log(N),
 * where #params = r_p * (K - 1), r_p the product of category counts of POP parents, K child categories.
 *
 * The instance-specific component affects structure prior (added/removed/reversed parents vs population)
 * but does not change the BIC likelihood formula itself.
 */
public class ISBicScore implements ISScore {

    /** Training data as [var][row] integer-coded categories (use -99 for missing). */
    private final int[][] data;

    /** Test (instance) data as [var][0]; must have exactly one row. */
    private final int[][] test;

    /** Number of rows in training data. */
    private final int sampleSize;

    /** Number of categories per variable. */
    private final int[] numCategories;

    /** Variable list (names/order must match data/test). */
    private List<Node> variables;

    /** Penalty discount multiplier for the BIC penalty term. */
    private double penaltyDiscount = 1.0;

    /** Edge-change penalties used in structure prior (log-domain combination). */
    private final double k_addition  = 0.1;
    private final double k_deletion  = 0.1;
    private final double k_reorient  = 0.1;

    public ISBicScore(DataSet dataSet, DataSet testCase) {
        if (dataSet == null || testCase == null) {
            throw new NullPointerException("Training dataset and test case must be non-null.");
        }

        // ---- Build training matrix
        this.variables = dataSet.getVariables();

        if (dataSet instanceof BoxDataSet box) {
            DataBox db = box.getDataBox();
            if (!(db instanceof VerticalIntDataBox)) {
                throw new IllegalArgumentException("ISBicScore expects VerticalIntDataBox for discrete data.");
            }
            VerticalIntDataBox vbox = (VerticalIntDataBox) db;
            this.data = vbox.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            // Build ints
            int p = dataSet.getNumColumns(), n = dataSet.getNumRows();
            this.data = new int[p][n];
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    this.data[j][i] = dataSet.getInt(i, j);
                }
            }
            this.sampleSize = n;
        }

        // ---- Categories
        int p = variables.size();
        this.numCategories = new int[p];
        for (int i = 0; i < p; i++) {
            if (!(variables.get(i) instanceof DiscreteVariable dv)) {
                throw new IllegalArgumentException("All variables must be discrete for ISBicScore: " + variables.get(i));
            }
            numCategories[i] = dv.getNumCategories();
        }

        // ---- Build test (single row)
        if (testCase.getNumColumns() != p) {
            throw new IllegalArgumentException("Test case variable count != training variable count.");
        }
        if (testCase instanceof BoxDataSet tbox) {
            DataBox tb = tbox.getDataBox();
            if (!(tb instanceof VerticalIntDataBox)) {
                tb = new VerticalIntDataBox(tb);
            }
            VerticalIntDataBox vtb = (VerticalIntDataBox) tb;
            this.test = vtb.getVariableVectors();
        } else {
            this.test = new int[p][];
            for (int j = 0; j < p; j++) {
                this.test[j] = new int[testCase.getNumRows()];
                for (int i = 0; i < testCase.getNumRows(); i++) {
                    this.test[j][i] = testCase.getInt(i, j);
                }
            }
        }
        if (this.test.length != p || this.test[0].length != 1) {
            throw new IllegalArgumentException("Instance-specific score expects a SINGLE-ROW test case.");
        }
    }

    // =========================== ISScore API ===========================

    @Override
    public double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        // Guards
        if (!(variables.get(node) instanceof DiscreteVariable)) {
            throw new IllegalArgumentException("Not discrete: " + variables.get(node));
        }
        for (int t : parents_is) {
            if (!(variables.get(t) instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Not discrete: " + variables.get(t));
            }
        }

        final int K = numCategories[node];

        // Product of category counts for POP parents
        final int[] dims_p = getDimensions(parents_pop);
        final int r_p = computeAllParentStates(parents_pop, dims_p);

        // POP counts
        final int[][] np_jk = new int[r_p][K];
        final int[]   np_j  = new int[r_p];

        // (We read the instance parents, but for BIC they only affect structure prior, not LL.)
        final int[] parentValuesTest = new int[parents_is.length];
        for (int i = 0; i < parents_is.length; i++) parentValuesTest[i] = test[parents_is[i]][0];

        final int[] y = data[node];

        // Count loop (skip row if any referenced var is missing)
        ROW:
        for (int i = 0; i < sampleSize; i++) {
            // Need POP parents available for POP counts
            for (int p = 0; p < parents_pop.length; p++) {
                if (data[parents_pop[p]][i] == -99) continue ROW;
            }
            int yv = y[i];
            if (yv == -99) continue;

            int[] parentValuesPop = new int[parents_pop.length];
            for (int p = 0; p < parents_pop.length; p++) {
                parentValuesPop[p] = data[parents_pop[p]][i];
            }
            int j = getRowIndex(dims_p, parentValuesPop);
            np_jk[j][yv]++;
            np_j[j]++;
        }

        // Log-likelihood: sum_jk n_jk log(n_jk / n_j)
        double llPop = 0.0;
        for (int j = 0; j < r_p; j++) {
            int n_j = np_j[j];
            if (n_j == 0) continue;
            for (int k = 0; k < K; k++) {
                int n_jk = np_jk[j][k];
                if (n_jk == 0) continue;
                llPop += n_jk * (Math.log(n_jk) - Math.log(n_j));
            }
        }

        // Parameters: r_p * (K - 1)
        final int nParams = r_p * (K - 1);
        final double bicPop = llPop - 0.5 * penaltyDiscount * nParams * Math.log(sampleSize);

        // Instance-specific structure prior (edge edits relative to POP)
        final double structIS = getPriorForStructure(node, parents_is, parents_pop, children_pop);

        // Optional population structural prior (disabled by default)
        final double structPop = 0.0;

        return bicPop + structIS + structPop;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {
        double s1 = localScore(y, append(z, x), z_pop, child_pop);
        double s2 = localScore(y, z, z_pop, child_pop);
        return s1 - s2;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable mismatch at index " + i);
            }
        }
        this.variables = variables;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    // BIC does not use these priors; provide safe no-ops/zeros.
    @Override public double getStructurePrior() { return 0.0; }
    @Override public void setStructurePrior(double structurePrior) { /* no-op */ }
    @Override public double getSamplePrior() { return 0.0; }
    @Override public void setSamplePrior(double samplePrior) { /* no-op */ }

    @Override
    public Node getVariable(String targetName) {
        for (Node n : variables) if (n.getName().equals(targetName)) return n;
        return null;
    }

    @Override
    public int getMaxDegree() {
        // Keep large default, or consider Math.ceil(log N) for symmetry with BDeu
        return 1000;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        // For BIC, localScore1 == localScore (no structure prior change); keep identical for simplicity.
        return localScore(node, parents_is, parents_pop, children_pop);
    }

    public double getPenaltyDiscount() { return penaltyDiscount; }
    public void setPenaltyDiscount(double penaltyDiscount) { this.penaltyDiscount = penaltyDiscount; }

    public double getKAddition() { return k_addition; }
    public double getKDeletion() { return k_deletion; }
    public double getKReorientation() { return k_reorient; }

    // =========================== helpers ===========================

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    private int[] getDimensions(int[] parents) {
        int[] dims = new int[parents.length];
        for (int p = 0; p < parents.length; p++) {
            dims[p] = numCategories[parents[p]];
        }
        return dims;
    }

    private int computeAllParentStates(int[] parents, int[] dims) {
        int r = 1;
        for (int i = 0; i < parents.length; i++) r *= dims[i];
        return r;
    }

    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];
        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }
        return values;
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    private double getPriorForStructure(int nodeIndex, int[] parents, int[] parents_pop, int[] children_pop) {
        List<Integer> added = new ArrayList<>();
        List<Integer> reversed = new ArrayList<>();

        List<Integer> popParents = IntStream.of(parents_pop).boxed().toList();
        List<Integer> popChildren = IntStream.of(children_pop).boxed().toList();

        for (int p : parents) {
            if (!popParents.contains(p)) {
                if (popChildren.contains(p)) reversed.add(p);
                else added.add(p);
            }
        }

        List<Integer> isParents = IntStream.of(parents).boxed().toList();
        List<Integer> removed = new ArrayList<>();
        for (int p : parents_pop) if (!isParents.contains(p)) removed.add(p);

        return added.size()   * Math.log(getKAddition())
               + removed.size() * Math.log(getKDeletion())
               + reversed.size()* Math.log(getKReorientation());
    }
}