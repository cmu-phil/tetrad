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
     * @param dataSet  training data (discrete)
     * @param testCase single-row test case, same variables/order as {@code dataSet}
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

    @Override
    public double localScoreDiff(final int x, final int y, final int[] z, final int[] zPOP, final int[] childPOP) {
        final int[] zPlusX = appendUniqueSorted(z, x);
        return localScore(y, zPlusX, zPOP, childPOP) - localScore(y, z, zPOP, childPOP);
    }

    @Override
    public List<Node> getVariables() { return variables; }

    /** Preserve name/order but allow identity replacement (e.g., to carry labels). */
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

    @Override
    public DataSet getDataSet() { return trainDataSet; }

    @Override public double getStructurePrior() { return 0.0; }
    @Override public void setStructurePrior(double structurePrior) { /* not used in BIC */ }
    @Override public double getSamplePrior() { return 0.0; }
    @Override public void setSamplePrior(double samplePrior) { /* not used in BIC */ }

    @Override
    public Node getVariable(String targetName) {
        for (Node n : variables) if (n.getName().equals(targetName)) return n;
        return null;
    }

    @Override
    public int getMaxDegree() { return 1000; }

    @Override
    public boolean determines(List<Node> z, Node y) { return false; }

    /** For BIC, the version without structure prior is just the BIC term. */
    @Override
    public double localScore1(int node, int[] parentsIS, int[] parentsPOP, int[] childrenPOP) {
        return bicLocal(node, parentsPOP);
    }

    // ============================ Tunables =============================

    public double getPenaltyDiscount() { return penaltyDiscount; }
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount < 0) throw new IllegalArgumentException("penaltyDiscount must be >= 0");
        this.penaltyDiscount = penaltyDiscount;
    }

    public double getKAddition() { return kAddition; }
    public void setKAddition(double kAddition) { this.kAddition = requirePositive(kAddition, "kAddition"); }

    public double getKDeletion() { return kDeletion; }
    public void setKDeletion(double kDeletion) { this.kDeletion = requirePositive(kDeletion, "kDeletion"); }

    public double getKReorientation() { return kReorient; }
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

    // Exposed for tests / debugging
    public int[] getParentValuesForCombination(int rowIndex, int[] dims) {
        int[] values = new int[dims.length];
        for (int i = dims.length - 1; i >= 0; i--) { values[i] = rowIndex % dims[i]; rowIndex /= dims[i]; }
        return values;
    }
}
