package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Instance-Specific BIC (IS-BIC) score for discrete data.
 *
 * <p>Implements {@link IsScore} for discrete variables. Likelihood is the usual
 * BIC/MDL population term; instance-specificity is injected via a simple
 * structure prior that rewards/penalizes edits (add/remove/reorient) relative
 * to a population family.</p>
 */
public class IsBicScore implements IsScore {

    /** Training data as [var][row] integer-coded categories (use -99 for missing). */
    private final int[][] data;                   // shape: p × n

    /** Test (instance) data as [var][1]; must have exactly one row. */
    private final int[][] test;                   // shape: p × 1

    /** Backing datasets (kept for API compatibility / debugging). */
    private final DataSet trainDataSet;
    private final DataSet testDataSet;

    /** Number of rows in training data. */
    private final int sampleSize;

    /** Number of categories per variable. */
    private final int[] numCategories;            // length p

    /** Variables (names/order must match data/test). */
    private List<Node> variables;                 // length p

    /** BIC penalty discount multiplier. */
    private double penaltyDiscount = 1.0;

    /** Structure-prior weights (>0). */
    private double kAddition   = 0.1;
    private double kDeletion   = 0.1;
    private double kReorient   = 0.1;

    /** Missing value sentinel used by VerticalIntDataBox. */
    private static final int MISSING = -99;

    // ============================== Ctor ===============================

    /**
     * Constructs an instance of the IsBicScore class. This class calculates a Bayesian Information Criterion (BIC)
     * score for a discrete dataset, taking into account training data and a single-row test case.
     *
     * @param dataSet the training dataset, which must be non-null and contain only discrete variables.
     * @param testCase the test case dataset, expected to be a single-row dataset with the same number and type of
     *                 variables as the training dataset. Must be non-null.
     * @throws NullPointerException if either the training dataset or the test case is null.
     * @throws IllegalArgumentException if:
     *                                  - The training dataset does not use a VerticalIntDataBox for discrete data.
     *                                  - Not all variables in the training dataset are discrete.
     *                                  - The number of variables in the test case does not match the number of
     *                                    variables in the training dataset.
     *                                  - The test case does not consist of a single row.
     *                                  - The training dataset sample size is less than or equal to 0.
     */
    public IsBicScore(final DataSet dataSet, final DataSet testCase) {
        if (dataSet == null || testCase == null) {
            throw new NullPointerException("Training dataset and test case must be non-null.");
        }

        this.trainDataSet = dataSet;
        this.testDataSet = testCase;
        this.variables = dataSet.getVariables();

        // ---- Build training matrix
        if (dataSet instanceof BoxDataSet box) {
            DataBox db = box.getDataBox();
            if (!(db instanceof VerticalIntDataBox)) {
                throw new IllegalArgumentException("IsBicScore expects VerticalIntDataBox for discrete data.");
            }
            VerticalIntDataBox vbox = (VerticalIntDataBox) db;
            this.data = vbox.getVariableVectors();
            this.sampleSize = dataSet.getNumRows();
        } else {
            int p = dataSet.getNumColumns(), n = dataSet.getNumRows();
            this.data = new int[p][n];
            for (int j = 0; j < p; j++) for (int i = 0; i < n; i++) this.data[j][i] = dataSet.getInt(i, j);
            this.sampleSize = n;
        }

        // ---- Categories (and discrete guard)
        final int p = variables.size();
        this.numCategories = new int[p];
        for (int j = 0; j < p; j++) {
            Node v = variables.get(j);
            if (!(v instanceof DiscreteVariable dv)) {
                throw new IllegalArgumentException("All variables must be discrete for IsBicScore: " + v);
            }
            numCategories[j] = dv.getNumCategories();
        }

        // ---- Build test (single row)
        if (testCase.getNumColumns() != p) {
            throw new IllegalArgumentException("Test case variable count != training variable count.");
        }
        if (testCase instanceof BoxDataSet tbox) {
            DataBox tb = tbox.getDataBox();
            if (!(tb instanceof VerticalIntDataBox)) tb = new VerticalIntDataBox(tb);
            VerticalIntDataBox vtb = (VerticalIntDataBox) tb;
            this.test = vtb.getVariableVectors();
        } else {
            this.test = new int[p][1];
            if (testCase.getNumRows() != 1) {
                throw new IllegalArgumentException("Instance-specific score expects a SINGLE-ROW test case.");
            }
            for (int j = 0; j < p; j++) this.test[j][0] = testCase.getInt(0, j);
        }
        if (this.test.length != p || this.test[0].length != 1) {
            throw new IllegalArgumentException("Instance-specific score expects a SINGLE-ROW test case.");
        }

        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Training sample size must be > 0 for BIC.");
        }
    }

    // ============================ IsScore ==============================

    /**
     * Computes the local score for a given node in a Bayesian network based on the provided parent
     * and child configurations.
     *
     * The local score is determined by combining the likelihood (population) with a Bayesian
     * Information Criterion (BIC) penalty, and an instance-specific structure prior. By default,
     * no additional population structural prior is applied.
     *
     * @param node the target node for which the local score is being computed; must be a discrete variable
     * @param parentsIS the array of parent nodes, specific to the instance; must consist of discrete variables
     * @param parentsPOP the array of parent nodes based on population data; must consist of discrete variables
     * @param childrenPOP the array of child nodes based on population data; must consist of discrete variables
     * @return the computed local score for the given node
     */
    @Override
    public double localScore(final int node, final int[] parentsIS, final int[] parentsPOP, final int[] childrenPOP) {
        requireDiscrete(node);
        requireDiscrete(parentsIS);
        requireDiscrete(parentsPOP);

        // Likelihood (population) + BIC penalty
        final double bicPop = bicLocal(node, parentsPOP);

        // Instance-specific structure prior
        final double structIS = structurePrior(node, parentsIS, parentsPOP, childrenPOP);

        // No extra population structural prior by default
        return bicPop + structIS;
    }

    /**
     * Computes the difference in local scores for a given variable y, when a variable x
     * is added to a conditioning set z, compared to the original set z.
     *
     * @param x the variable to add to the conditioning set z
     * @param y the variable for which the local score difference is computed
     * @param z the original conditioning set
     * @param zPOP the population data related to the original conditioning set
     * @param childPOP the population data related to the child variable
     * @return the difference in local scores between the sets with and without the variable x
     */
    @Override
    public double localScoreDiff(final int x, final int y, final int[] z, final int[] zPOP, final int[] childPOP) {
        final int[] zPlusX = appendUniqueSorted(z, x);
        return localScore(y, zPlusX, zPOP, childPOP) - localScore(y, z, zPOP, childPOP);
    }

    /**
     * Retrieves the list of variable nodes.
     *
     * @return a list of Node objects representing the variables
     */
    @Override
    public List<Node> getVariables() { return variables; }

    /**
     * Sets the list of variables for this object. The provided list must match
     * the existing variables in both size and order, verified by comparing the
     * names of each corresponding variable.
     *
     * @param variables the new list of variables to be set. Each variable in
     *                  the list must correspond to the current variables in size
     *                  and name order.
     * @throws IllegalArgumentException if the size of the provided list does not
     *                                  match the existing list of variables or
     *                                  if any variable name does not match the
     *                                  name at the corresponding index.
     */
    public void setVariables(final List<Node> variables) {
        if (variables.size() != this.variables.size()) {
            throw new IllegalArgumentException("Variable size mismatch.");
        }
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable mismatch at index " + i);
            }
        }
        this.variables = variables;
    }

    @Override
    public boolean isEffectEdge(double bump) { return bump > 0.0; }

    @Override
    public int getSampleSize() { return sampleSize; }

    /**
     * Retrieves the training data set that is utilized within this instance.
     *
     * @return the current training {@code DataSet} object.
     */
    @Override
    public DataSet getDataSet() { return trainDataSet; }

    /**
     * Retrieves the structure prior value, which is used in the scoring
     * mechanisms of the class to reflect prior beliefs or assumptions
     * about the structure of the model.
     *
     * @return the structure prior value as a double.
     */
    @Override public double getStructurePrior() { return 0.0; }

    /**
     * Sets the structure prior value. This method is not utilized in the BIC scoring mechanism.
     *
     * @param structurePrior the structure prior value to be set, typically a double representing
     *                        prior information about the model structure
     */
    @Override public void setStructurePrior(double structurePrior) { /* not used in BIC */ }

    /**
     * Retrieves the sample prior value, which is used in the scoring mechanisms
     * of the class to represent prior assumptions or knowledge about the sample distribution.
     *
     * @return the sample prior value as a double.
     */
    @Override public double getSamplePrior() { return 0.0; }

    /**
     * Sets the sample prior value. This method is not utilized in the BIC scoring mechanism.
     *
     * @param samplePrior the sample prior value to be set, typically a double representing
     *                    prior knowledge or assumptions about the sample distribution
     */
    @Override public void setSamplePrior(double samplePrior) { /* not used in BIC */ }

    /**
     * Searches the list of variables for a node with the specified name and returns it.
     *
     * @param targetName the name of the variable to retrieve
     * @return the {@code Node} with the specified name if found; otherwise, {@code null}
     */
    @Override
    public Node getVariable(String targetName) {
        for (Node n : variables) if (n.getName().equals(targetName)) return n;
        return null;
    }

    /**
     * Retrieves the maximum degree allowed for a node in the context of this implementation.
     *
     * @return the maximum degree, represented as an integer, which determines the
     *         maximum number of edges connected to a node.
     */
    @Override
    public int getMaxDegree() { return 1000; }

    /**
     * Determines whether the given set of nodes {@code z} implies or determines
     * the state or value of the node {@code y} based on certain criteria.
     *
     * @param z the list of nodes that represent the set of potential determinants
     * @param y the target node to be evaluated for dependency or determination
     * @return {@code true} if the set of nodes {@code z} determines the node {@code y};
     *         otherwise, {@code false}
     */
    @Override
    public boolean determines(List<Node> z, Node y) { return false; }

    /**
     * Computes the local score for a given node using the BIC metric.
     *
     * @param node the index of the node for which the local score is calculated
     * @param parentsIS an array representing the parent nodes in the IS set (not used in this implementation)
     * @param parentsPOP an array representing the parent nodes in the POP set
     * @param childrenPOP an array representing the children nodes in the POP set (not used in this implementation)
     * @return the local score as a double value
     */
    @Override
    public double localScore1(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP) {
        return bicLocal(node, parentsPOP);
    }

    // ============================ Tunables =============================

    /**
     * Retrieves the penalty discount value, which is used in scoring mechanisms
     * within the class to adjust the influence of complexity penalties.
     *
     * @return the penalty discount value as a double.
     */
    public double getPenaltyDiscount() { return penaltyDiscount; }

    /**
     * Sets the penalty discount value, which is used in scoring mechanisms
     * within the class to adjust the influence of complexity penalties.
     *
     * @param penaltyDiscount the penalty discount value, must be >= 0
     * @throws IllegalArgumentException if the penalty discount value is negative
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount < 0) throw new IllegalArgumentException("penaltyDiscount must be >= 0");
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Retrieves the K addition value, which is used in the scoring mechanisms
     * within the class to adjust the evaluation of certain configurations or parameters.
     *
     * @return the K addition value as a double.
     */
    public double getKAddition() { return kAddition; }

    /**
     * Sets the K addition value, which is used to adjust the evaluation of certain
     * configurations or parameters in scoring mechanisms within the class.
     *
     * @param kAddition the K addition value, must be > 0
     * @throws IllegalArgumentException if the K addition value is not positive
     */
    public void setKAddition(double kAddition) { this.kAddition = requirePositive(kAddition, "kAddition"); }

    /**
     * Retrieves the K deletion value, which is used in the scoring mechanisms
     * within the class to adjust the evaluation of certain configurations or parameters.
     *
     * @return the K deletion value as a double.
     */
    public double getKDeletion() { return kDeletion; }

    /**
     * Sets the K deletion value, which is used in the scoring mechanisms within
     * the class to adjust the evaluation of certain configurations or parameters.
     *
     * @param kDeletion the K deletion value, must be > 0
     * @throws IllegalArgumentException if the K deletion value is not positive
     */
    public void setKDeletion(double kDeletion) { this.kDeletion = requirePositive(kDeletion, "kDeletion"); }

    /**
     * Retrieves the K reorientation value, which is used in the scoring mechanisms
     * within the class to adjust the evaluation of certain configurations or parameters.
     *
     * @return the K reorientation value as a double.
     */
    public double getKReorientation() { return kReorient; }

    /**
     * Sets the K reorientation value, which is used in the scoring mechanisms within
     * the class to adjust the evaluation of certain configurations or parameters.
     *
     * @param kReorient the K reorientation value, must be > 0
     * @throws IllegalArgumentException if the K reorientation value is not positive
     */
    public void setKReorientation(double kReorient) { this.kReorient = requirePositive(kReorient, "kReorientation"); }

    // ============================ Internals ============================

    private double bicLocal(final int node, final int[] parentsPOP) {
        final int K = numCategories[node];
        final int[] dims = dims(parentsPOP);
        final int r_p = numStates(dims);

        final int[][] n_jk = new int[r_p][K];
        final int[]   n_j  = new int[r_p];

        final int[] y = data[node];

        // Count loop (skip row if any referenced var is missing)
        ROW: for (int i = 0; i < sampleSize; i++) {
            for (int p : parentsPOP) if (data[p][i] == MISSING) continue ROW;
            final int yv = y[i];
            if (yv == MISSING) continue;

            final int[] parentVals = new int[parentsPOP.length];
            for (int t = 0; t < parentsPOP.length; t++) parentVals[t] = data[parentsPOP[t]][i];
            final int j = rowIndex(dims, parentVals);
            n_jk[j][yv]++;
            n_j[j]++;
        }

        // Log-likelihood: sum_jk n_jk log(n_jk / n_j)
        double ll = 0.0;
        for (int j = 0; j < r_p; j++) {
            final int nj = n_j[j];
            if (nj == 0) continue;
            for (int k = 0; k < K; k++) {
                final int njk = n_jk[j][k];
                if (njk == 0) continue;
                ll += njk * (Math.log(njk) - Math.log(nj));
            }
        }

        // Parameters: r_p * (K - 1)
        final int nParams = r_p * (K - 1);
        return ll - 0.5 * penaltyDiscount * nParams * Math.log(sampleSize);
    }

    private double structurePrior(final int nodeIndex, final int[] parentsIS,
                                  final int[] parentsPOP, final int[] childrenPOP) {
        // De-dup & sort for determinism
        final int[] isP  = uniqueSorted(parentsIS);
        final int[] popP = uniqueSorted(parentsPOP);
        final int[] popC = uniqueSorted(childrenPOP);

        final Set<Integer> popParentsSet  = toSet(popP);
        final Set<Integer> popChildrenSet = toSet(popC);
        final Set<Integer> isParentsSet   = toSet(isP);

        int added = 0, removed = 0, reversed = 0;

        for (int p : isP) {
            if (!popParentsSet.contains(p)) {
                if (popChildrenSet.contains(p)) reversed++; else added++;
            }
        }
        for (int p : popP) if (!isParentsSet.contains(p)) removed++;

        return added   * Math.log(kAddition)
               + removed * Math.log(kDeletion)
               + reversed* Math.log(kReorient);
    }

    // ----------------------- Small utilities -----------------------

    private void requireDiscrete(int var) {
        if (!(variables.get(var) instanceof DiscreteVariable))
            throw new IllegalArgumentException("Not discrete: " + variables.get(var));
    }

    private void requireDiscrete(int[] vars) { for (int v : vars) requireDiscrete(v); }

    private static double requirePositive(double v, String name) {
        if (!(v > 0.0)) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }

    private int[] dims(int[] parents) {
        int[] d = new int[parents.length];
        for (int i = 0; i < parents.length; i++) d[i] = numCategories[parents[i]];
        return d;
    }

    private static int numStates(int[] dims) {
        int r = 1; for (int d : dims) r *= d; return r; }

    private static int rowIndex(int[] dims, int[] values) {
        int idx = 0;
        for (int i = 0; i < dims.length; i++) { idx = idx * dims[i] + values[i]; }
        return idx;
    }

    /** return a sorted array with duplicates removed. */
    private static int[] uniqueSorted(int[] a) {
        if (a == null || a.length == 0) return new int[0];
        return IntStream.of(a).distinct().sorted().toArray();
    }

    /** return a sorted array with {@code extra} appended if absent. */
    private static int[] appendUniqueSorted(int[] a, int extra) {
        if (a == null || a.length == 0) return new int[]{extra};
        boolean present = false; for (int v : a) if (v == extra) { present = true; break; }
        if (present) return a;
        int[] out = Arrays.copyOf(a, a.length + 1); out[a.length] = extra; Arrays.sort(out); return out;
    }

    private static Set<Integer> toSet(int[] arr) {
        Set<Integer> s = new HashSet<>(arr.length * 2);
        for (int v : arr) s.add(v);
        return s;
    }

    /**
     * Computes the parent values for a given row index based on the provided dimensions.
     * This method determines how a row index maps to its corresponding parent values
     * for a specific configuration of dimensions, assuming a multi-dimensional
     * discrete variable space.
     *
     * @param rowIndex the row index for which to compute parent values
     * @param dims an array representing the dimensions of the discrete variables
     *             (e.g., the number of states for each variable)
     * @return an array of parent values derived from the given row index, where each
     *         value corresponds to a specific dimension
     */
    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];
        for (int i = dims.length - 1; i >= 0; i--) { values[i] = rowIndex % dims[i]; rowIndex /= dims[i]; }
        return values;
    }
}
