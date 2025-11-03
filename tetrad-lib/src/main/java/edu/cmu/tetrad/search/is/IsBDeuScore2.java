package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.special.Gamma;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Instance‑Specific BDeu (IS‑BDeu) score.
 *
 * <p>Population term: standard BDeu on training data (Dirichlet‑uniform prior).
 * Instance‑specific term: single‑row correction favoring the test case’s parent
 * configuration; plus a simple structure prior for edits vs. a population family.</p>
 */
public class IsBDeuScore2 implements IsScore {

    private static final int MISSING = -99;

    // ------------ Data ------------
    private final int[][] data;               // shape: p × n (var × row)
    private final int sampleSize;
    private final int[][] test;               // shape: p × 1 (single instance)
    private final int[] numCategories;        // length p
    private List<Node> variables;             // length p, name/order must match matrices

    // ------------ Hyper‑params ------------
    private double samplePrior = 1.0;         // equivalent sample size multiplier
    private double structurePrior = 1.0;      // kept for API parity (not directly used below)
    private double k_addition  = 0.1;
    private double k_deletion  = 0.1;
    private double k_reorient  = 0.1;

    private static final boolean verbose = false;

    // ============================== Ctor ===============================

    /**
     * Constructs an instance of the IsBDeuScore2 class with the specified training and test datasets.
     *
     * @param dataSet the training dataset, which must contain discrete variables. It is used to extract variable
     *                information, sampleSize, and data in the form of variable vectors.
     * @param testCase the test dataset, which must be a single row and align with the training dataset in terms
     *                 of the number of variables and their discrete nature. It is used to create test variable
     *                 vectors for computation.
     *
     * @throws NullPointerException if either the dataSet or testCase is null.
     * @throws IllegalArgumentException if any of the following conditions are met:
     *                                  - The training dataset contains variables that are not discrete.
     *                                  - The number of variables in the test case does not match the training
     *                                    dataset.
     *                                  - The test case is not a single-row dataset.
     */
    public IsBDeuScore2(final DataSet dataSet, final DataSet testCase) {
        if (dataSet == null) throw new NullPointerException("Dataset was not provided.");
        if (testCase == null) throw new NullPointerException("Test case was not provided.");

        this.variables = dataSet.getVariables();

        // training matrix
        if (dataSet instanceof BoxDataSet box) {
            DataBox db = box.getDataBox();
            if (!(db instanceof VerticalIntDataBox)) db = new VerticalIntDataBox(db);
            VerticalIntDataBox vbox = (VerticalIntDataBox) db;
            this.data = vbox.getVariableVectors();
            this.sampleSize = vbox.numRows();
        } else {
            int p = dataSet.getNumColumns(), n = dataSet.getNumRows();
            this.data = new int[p][n];
            for (int j = 0; j < p; j++) for (int i = 0; i < n; i++) this.data[j][i] = dataSet.getInt(i, j);
            this.sampleSize = n;
        }

        // categories + discrete guard
        final int p = variables.size();
        this.numCategories = new int[p];
        for (int i = 0; i < p; i++) {
            Node v = variables.get(i);
            if (!(v instanceof DiscreteVariable dv)) {
                throw new IllegalArgumentException("All variables must be discrete for IsBDeuScore2: " + v);
            }
            numCategories[i] = dv.getNumCategories();
        }

        // test (must be single row)
        if (testCase.getNumColumns() != p) {
            throw new IllegalArgumentException("Test case variable count != training variable count.");
        }
        if (testCase instanceof BoxDataSet tbox) {
            DataBox tb = tbox.getDataBox();
            if (!(tb instanceof VerticalIntDataBox)) tb = new VerticalIntDataBox(tb);
            VerticalIntDataBox vtb = (VerticalIntDataBox) tb;
            this.test = vtb.getVariableVectors();
        } else {
            if (testCase.getNumRows() != 1) {
                throw new IllegalArgumentException("Instance‑specific score expects a SINGLE‑ROW test case.");
            }
            this.test = new int[p][1];
            for (int j = 0; j < p; j++) this.test[j][0] = testCase.getInt(0, j);
        }
        if (this.test.length != p || this.test[0].length != 1) {
            throw new IllegalArgumentException("Instance‑specific score expects a SINGLE‑ROW test case.");
        }
    }

    // ============================== IsScore ==============================

    @Override
    public double localScore(final int node, final int[] parents_is, final int[] parents_pop, final int[] children_pop) {
        // Node arity
        final int K = numCategories[node];

        // POP parent dims & states
        final int[] dims_p = getDimensions(parents_pop);
        final int r_p = numStates(parents_pop, dims_p);

        // Counts
        final int[][] np_jk = new int[r_p][K];
        final int[]   np_j  = new int[r_p];

        // IS bin for the test case
        final int[] parentValuesTest = new int[parents_is.length];
        for (int i = 0; i < parents_is.length; i++) parentValuesTest[i] = test[parents_is[i]][0];

        final int[] y = data[node];

        // Iterate rows; if test parents match, push to IS cell; else to POP table
        ROW: for (int i = 0; i < sampleSize; i++) {
            // IS parents for the row
            final int[] parentValuesIS = new int[parents_is.length];
            for (int p = 0; p < parents_is.length; p++) {
                int val = data[parents_is[p]][i];
                if (val == MISSING) continue ROW;
                parentValuesIS[p] = val;
            }

            final int childValue = y[i];
            if (childValue == MISSING) continue;

            if (parents_is.length > 0 && Arrays.equals(parentValuesIS, parentValuesTest)) {
                // contributed to IS term below via ni_jk/ni_j
                // We don't need to keep the per‑state breakdown for IS: only the single matched row bucket.
                // We accumulate counts directly as ni_jk/ni_j.
            } else {
                // To POP counts (needs POP parents present)
                final int[] parentValuesPop = new int[parents_pop.length];
                for (int p = 0; p < parents_pop.length; p++) {
                    int val = data[parents_pop[p]][i];
                    if (val == MISSING) continue ROW;
                    parentValuesPop[p] = val;
                }
                final int j = rowIndex(dims_p, parentValuesPop);
                np_jk[j][childValue]++;
                np_j[j]++;
            }
        }

        // Separate pass to compute IS counts (only the single matched configuration)
        int[] ni_jk = new int[K];
        int   ni_j  = 0;
        if (parents_is.length > 0) {
            ROW2: for (int i = 0; i < sampleSize; i++) {
                final int[] parentValuesIS = new int[parents_is.length];
                for (int p = 0; p < parents_is.length; p++) {
                    int val = data[parents_is[p]][i];
                    if (val == MISSING) continue ROW2;
                    parentValuesIS[p] = val;
                }
                if (Arrays.equals(parentValuesIS, parentValuesTest)) {
                    final int yv = y[i];
                    if (yv == MISSING) continue;
                    ni_jk[yv]++; ni_j++;
                }
            }
        }

        // Build priors over the union of IS and POP parents (uniform over configurations)
        final int[] parents_all = unionSorted(parents_pop, parents_is);
        final int[] dims_all = getDimensions(parents_all);
        final int r_all = numStates(parents_all, dims_all);
        final Map<List<Integer>, Double> row_priors = new HashMap<>(r_all * 2);
        for (int i = 0; i < r_all; i++) {
            int[] rowVals = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowVals).boxed().collect(Collectors.toList()), 1.0 / r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0;

        // IS term (only if there are IS parents)
        if (parents_is.length > 0) {
            double rowPrior_i = computeRowPrior(parents_is, parentValuesTest, parents_all, row_priors);
            rowPrior_i = getSamplePrior() * rowPrior_i;
            double cellPrior_i = rowPrior_i / K;

            for (int k = 0; k < K; k++) scoreIS += Gamma.logGamma(cellPrior_i + ni_jk[k]);
            scoreIS -= K * Gamma.logGamma(cellPrior_i);
            scoreIS -= Gamma.logGamma(rowPrior_i + ni_j);
            scoreIS += Gamma.logGamma(rowPrior_i);
        }

        // POP term: loop over all POP parent configurations
        for (int j = 0; j < r_p; j++) {
            int[] parentValuesPop = getParentValuesForCombination(j, dims_p);
            double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
            rowPrior_p = getSamplePrior() * rowPrior_p;
            double cellPrior_p = rowPrior_p / K;

            if (rowPrior_p > 0) {
                scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
                for (int k = 0; k < K; k++) {
                    scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
                    scorePop -= Gamma.logGamma(cellPrior_p);
                }
                scorePop += Gamma.logGamma(rowPrior_p);
            }
        }

        // Add structure prior
        double score = scorePop + scoreIS + priorForStructure(node, parents_is, parents_pop, children_pop);
        return score;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z, int[] z_pop, int[] child_pop) {
        return localScore(y, append(z, x), z_pop, child_pop) - localScore(y, z, z_pop, child_pop);
    }

    @Override public List<Node> getVariables() { return variables; }

    /**
     * Sets the list of variables for the current instance.
     * Throws an IllegalArgumentException if there is a mismatch in variable names
     * between the provided list and the existing list at any index.
     *
     * @param variables the list of Node objects representing the variables to be set.
     */
    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable mismatch at index " + i);
            }
        }
        this.variables = variables;
    }

    /**
     * Retrieves the sample size used in the computation or analysis.
     *
     * @return the sample size as an integer.
     */
    @Override public int getSampleSize() { return sampleSize; }

    /**
     * Retrieves the required effect/gain threshold for determining an effect edge.
     *
     * @param bump required effect/gain threshold
     * @return true if the bump is greater than 0, indicating an effect edge.
     */
    @Override public boolean isEffectEdge(double bump) { return bump > 0; }

    /**
     * Retrieves the required effect/gain threshold for determining an effect edge.
     *
     * @return true if the bump is greater than 0, indicating an effect edge.
     */
    @Override public DataSet getDataSet() { throw new UnsupportedOperationException(); }

    /**
     * Retrieves the required effect/gain threshold for determining an effect edge.
     *
     * @return true if the bump is greater than 0, indicating an effect edge.
     */
    @Override public double getStructurePrior() { return structurePrior; }

    /**
     * Sets the structure prior value for this instance.
     *
     * @param v the structure prior value to be set
     */
    @Override public void setStructurePrior(double v) { this.structurePrior = v; }

    /**
     * Retrieves the prior probability assigned to the sample during computations or analysis.
     *
     * @return the sample prior as a double value.
     */
    @Override public double getSamplePrior() { return samplePrior; }

    /**
     * Sets the prior probability assigned to the sample during computations or analysis.
     *
     * @param v the sample prior value to be set
     */
    @Override public void setSamplePrior(double v) { this.samplePrior = v; }

    /**
     * Retrieves the value of the k_addition parameter.
     *
     * @return the k_addition value as a double.
     */
    public double getKAddition() { return k_addition; }

    /**
     * Sets the value of the k_addition parameter.
     *
     * @param v the k_addition value to be set
     */
    public void setKAddition(double v) { this.k_addition = v; }

    /**
     * Retrieves the value of the k_deletion parameter.
     *
     * @return the k_deletion value as a double.
     */
    public double getKDeletion() { return k_deletion; }

    /**
     * Sets the value of the k_deletion parameter.
     *
     * @param v the k_deletion value to be set
     */
    public void setKDeletion(double v) { this.k_deletion = v; }

    /**
     * Retrieves the value of the k_reorientation parameter.
     *
     * @return the k_reorientation value as a double.
     */
    public double getKReorientation() { return k_reorient; }

    /**
     * Sets the value of the k_reorientation parameter.
     *
     * @param v the k_reorientation value to be set
     */
    public void setKReorientation(double v) { this.k_reorient = v; }

    /**
     * Retrieves the variable node with the specified name.
     *
     * @param targetName variable name
     * @return the variable node with the specified name, or null if not found
     */
    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) if (node.getName().equals(targetName)) return node;
        return null;
    }

    /**
     * Retrieves the maximum degree of the variables in the dataset.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() { return (int) Math.ceil(Math.log(Math.max(2, sampleSize))); }

    /**
     * Determines if variable y is determined by variables z.
     *
     * @param z list of parent variables
     * @param y target variable
     * @return true if y is determined by z, false otherwise
     */
    @Override
    public boolean determines(List<Node> z, Node y) { return false; }

    /**
     * Local score without structure prior (pure IS+POP BDeu likelihoods).
     */
    @Override
    public double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop) {
        // Same as localScore but without the priorForStructure additive term.
        final int K = numCategories[node];
        final int[] dims_p = getDimensions(parents_pop);
        final int r_p = numStates(parents_pop, dims_p);
        final int[][] np_jk = new int[r_p][K];
        final int[]   np_j  = new int[r_p];

        final int[] parentValuesTest = new int[parents_is.length];
        for (int i = 0; i < parents_is.length; i++) parentValuesTest[i] = test[parents_is[i]][0];

        final int[] y = data[node];

        ROW: for (int i = 0; i < sampleSize; i++) {
            final int[] parentValuesIS = new int[parents_is.length];
            for (int p = 0; p < parents_is.length; p++) {
                int val = data[parents_is[p]][i];
                if (val == MISSING) continue ROW;
                parentValuesIS[p] = val;
            }
            final int childValue = y[i];
            if (childValue == MISSING) continue;

            if (parents_is.length > 0 && Arrays.equals(parentValuesIS, parentValuesTest)) {
                // defer to second pass below
            } else {
                final int[] parentValuesPop = new int[parents_pop.length];
                for (int p = 0; p < parents_pop.length; p++) {
                    int val = data[parents_pop[p]][i];
                    if (val == MISSING) continue ROW;
                    parentValuesPop[p] = val;
                }
                final int j = rowIndex(dims_p, parentValuesPop);
                np_jk[j][childValue]++;
                np_j[j]++;
            }
        }

        int[] ni_jk = new int[K]; int ni_j = 0;
        if (parents_is.length > 0) {
            ROW2: for (int i = 0; i < sampleSize; i++) {
                final int[] parentValuesIS = new int[parents_is.length];
                for (int p = 0; p < parents_is.length; p++) {
                    int val = data[parents_is[p]][i];
                    if (val == MISSING) continue ROW2;
                    parentValuesIS[p] = val;
                }
                if (Arrays.equals(parentValuesIS, parentValuesTest)) {
                    final int yv = y[i];
                    if (yv == MISSING) continue;
                    ni_jk[yv]++; ni_j++;
                }
            }
        }

        final int[] parents_all = unionSorted(parents_pop, parents_is);
        final int[] dims_all = getDimensions(parents_all);
        final int r_all = numStates(parents_all, dims_all);
        final Map<List<Integer>, Double> row_priors = new HashMap<>(r_all * 2);
        for (int i = 0; i < r_all; i++) {
            int[] rowVals = getParentValuesForCombination(i, dims_all);
            row_priors.put(Arrays.stream(rowVals).boxed().collect(Collectors.toList()), 1.0 / r_all);
        }

        double scoreIS = 0.0, scorePop = 0.0;
        if (parents_is.length > 0) {
            double rowPrior_i = computeRowPrior(parents_is, parentValuesTest, parents_all, row_priors);
            rowPrior_i = getSamplePrior() * rowPrior_i;
            double cellPrior_i = rowPrior_i / K;
            for (int k = 0; k < K; k++) scoreIS += Gamma.logGamma(cellPrior_i + ni_jk[k]);
            scoreIS -= K * Gamma.logGamma(cellPrior_i);
            scoreIS -= Gamma.logGamma(rowPrior_i + ni_j);
            scoreIS += Gamma.logGamma(rowPrior_i);
        }
        for (int j = 0; j < r_p; j++) {
            int[] parentValuesPop = getParentValuesForCombination(j, dims_p);
            double rowPrior_p = computeRowPrior(parents_pop, parentValuesPop, parents_all, row_priors);
            rowPrior_p = getSamplePrior() * rowPrior_p;
            double cellPrior_p = rowPrior_p / K;
            if (rowPrior_p > 0) {
                scorePop -= Gamma.logGamma(rowPrior_p + np_j[j]);
                for (int k = 0; k < K; k++) {
                    scorePop += Gamma.logGamma(cellPrior_p + np_jk[j][k]);
                    scorePop -= Gamma.logGamma(cellPrior_p);
                }
                scorePop += Gamma.logGamma(rowPrior_p);
            }
        }
        return scorePop + scoreIS;
    }

    // ============================== Internals ==============================

    private static int rowIndex(int[] dim, int[] values) {
        int idx = 0; for (int i = 0; i < dim.length; i++) { idx = idx * dim[i] + values[i]; } return idx; }

    private int[] getDimensions(int[] parents) {
        int[] dims = new int[parents.length];
        for (int p = 0; p < parents.length; p++) dims[p] = numCategories[parents[p]]; return dims; }

    private int numStates(int[] parents, int[] dims) {
        int r = 1; for (int i = 0; i < parents.length; i++) r *= dims[i]; return r; }

    private int[] append(int[] parents, int extra) {
        int[] out = Arrays.copyOf(parents, parents.length + 1); out[parents.length] = extra; return out; }

    private static int[] unionSorted(int[] a, int[] b) {
        return IntStream.concat(IntStream.of(a), IntStream.of(b)).distinct().sorted().toArray(); }

    private double priorForStructure(int nodeIndex, int[] parents, int[] parents_pop, int[] children_pop) {
        List<Integer> added = new ArrayList<>();
        List<Integer> reversed = new ArrayList<>();
        List<Integer> popParents = IntStream.of(parents_pop).boxed().toList();
        List<Integer> popChildren = IntStream.of(children_pop).boxed().toList();

        for (int p : parents) {
            if (!popParents.contains(p)) {
                if (popChildren.contains(p)) reversed.add(p); else added.add(p);
            }
        }

        List<Integer> isParents = IntStream.of(parents).boxed().toList();
        List<Integer> removed = new ArrayList<>();
        for (int p : parents_pop) if (!isParents.contains(p)) removed.add(p);

        if (verbose) {
            System.out.println("node: " + nodeIndex);
            System.out.println("parents is:   " + Arrays.toString(parents));
            System.out.println("parents pop:  " + Arrays.toString(parents_pop));
            System.out.println("children pop: " + Arrays.toString(children_pop));
            System.out.println("added=" + added + ", removed=" + removed + ", reversed=" + reversed);
        }
        return added.size()   * Math.log(getKAddition())
               + removed.size() * Math.log(getKDeletion())
               + reversed.size()* Math.log(getKReorientation());
    }

    private double computeRowPrior(int[] parents, int[] parent_values, int[] parents_all, Map<List<Integer>, Double> row_priors) {
        double rowPrior = 0.0;
        int[] indices = findIndices(parents, parents_all);
        for (Map.Entry<List<Integer>, Double> e : row_priors.entrySet()) {
            List<Integer> key = e.getKey();
            boolean eq = true;
            for (int i = 0; i < parents.length; i++) {
                if (!key.get(indices[i]).equals(parent_values[i])) { eq = false; break; }
            }
            if (eq) {
                rowPrior += e.getValue();
                // consume this mass so we don't double count if called multiple times
                row_priors.put(key, 0.0);
            }
        }
        return rowPrior;
    }

    private int[] findIndices(int[] parents, int[] parents_all) {
        int[] idx = new int[parents.length];
        for (int i = 0; i < parents.length; i++) {
            for (int j = 0; j < parents_all.length; j++) { if (parents_all[j] == parents[i]) { idx[i] = j; break; } }
        }
        return idx;
    }

    // Exposed for tests / debugging

    /**
     * Retrieves the parent values for a given node index, row index, and dimensions.
     *
     * @param nodeIndex index of the node
     * @param rowIndex index of the row
     * @param dims dimensions of the data
     * @return array of parent values
     */
    public int[] getParentValues(int nodeIndex, int rowIndex, int[] dims) { return getParentValuesForCombination(rowIndex, dims); }

    /**
     * Retrieves the parent values for a given row index and dimensions.
     *
     * @param rowIndex index of the row
     * @param dims dimensions of the data
     * @return array of parent values
     */
    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];
        for (int i = dims.length - 1; i >= 0; i--) { values[i] = rowIndex % dims[i]; rowIndex /= dims[i]; }
        return values;
    }
}
