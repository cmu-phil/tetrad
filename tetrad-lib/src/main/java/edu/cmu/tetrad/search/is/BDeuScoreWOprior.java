package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import org.apache.commons.math3.special.Gamma;

import java.util.List;

/**
 * BDeu score (Dirichlet–multinomial, decomposable) with tunable equivalent sample size ("samplePrior") and no structure
 * prior. Counts skip rows with missing in child or any parent (missing sentinel = -99).
 */
public class BDeuScoreWOprior implements Score {

    private static final int MISSING = -99;
    private final int[][] data;          // [var][row] category indices
    private final int sampleSize;        // number of rows
    private final int[] numCategories;   // arity per variable
    private List<Node> variables;        // schema (all discrete)
    private double samplePrior = 1.0;    // equivalent sample size (ESS)
    private double structurePrior = 1.0; // unused here, kept for API parity

    /**
     * Constructs a BDeuScoreWOprior object for scoring discrete data based on the BDeu scoring metric.
     *
     * @param dataSet the dataset to be scored. Must not be null and must contain only discrete variables. Each variable
     *                should be of type DiscreteVariable, and the data should be discrete.
     * @throws NullPointerException     if the provided dataset is null.
     * @throws IllegalArgumentException if the dataset is not discrete or contains non-discrete variables.
     */
    public BDeuScoreWOprior(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("Data was not provided.");
        if (!dataSet.isDiscrete()) throw new IllegalArgumentException("BDeuScoreWOprior requires discrete data.");

        this.variables = dataSet.getVariables();
        // sanity: all variables are DiscreteVariable
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            if (!(dataSet.getVariable(j) instanceof DiscreteVariable))
                throw new IllegalArgumentException("Non-discrete variable: " + dataSet.getVariable(j));
        }

        // Materialize as [var][row] int arrays
        if (dataSet instanceof BoxDataSet box) {
            DataBox db = box.getDataBox();
            if (!(db instanceof VerticalIntDataBox)) db = new VerticalIntDataBox(db);
            VerticalIntDataBox vbox = (VerticalIntDataBox) db;
            this.data = vbox.getVariableVectors();   // [var][row]
            this.sampleSize = vbox.numRows();
        } else {
            int p = dataSet.getNumColumns(), n = dataSet.getNumRows();
            this.data = new int[p][n];
            for (int j = 0; j < p; j++) for (int i = 0; i < n; i++) this.data[j][i] = dataSet.getInt(i, j);
            this.sampleSize = n;
        }

        this.numCategories = new int[variables.size()];
        for (int i = 0; i < variables.size(); i++)
            this.numCategories[i] = ((DiscreteVariable) variables.get(i)).getNumCategories();
    }

    private static int getRowIndex(int[] dim, int[] values) {
        int rowIndex = 0;
        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }
        return rowIndex;
    }

    // ============================== Score ==============================

    private DiscreteVariable getVariable(int i) {
        return (DiscreteVariable) variables.get(i);
    }

    /**
     * Calculates the local score for a given node and its parent set based on the BDeu scoring metric.
     *
     * @param node    the index of the target node whose local score is to be computed.
     * @param parents an array of indices representing the parent nodes of the target node.
     * @return the local score of the given node and its parent set, based on the BDeu metric.
     */
    @Override
    public double localScore(int node, int[] parents) {
        final int K = numCategories[node];

        // parent arities & number of parent configs r
        final int P = parents.length;
        final int[] dims = new int[P];
        int r = 1;
        for (int p = 0; p < P; p++) {
            dims[p] = numCategories[parents[p]];
            r *= dims[p];
        }
        if (r <= 0) r = 1; // guard though r>=1 by construction

        // counts
        final int[][] n_jk = new int[r][K];
        final int[] n_j = new int[r];
        final int[] parentValues = new int[P];

        final int[][] paCols = new int[P][];
        for (int p = 0; p < P; p++) paCols[p] = data[parents[p]];
        final int[] childCol = data[node];

        ROW:
        for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < P; p++) {
                int v = paCols[p][i];
                if (v == MISSING) continue ROW;     // skip if any parent missing
                parentValues[p] = v;
            }
            int y = childCol[i];
            if (y == MISSING) continue ROW;         // skip if child missing
            int j = getRowIndex(dims, parentValues);
            n_jk[j][y]++;
            n_j[j]++;
        }

        // BDeu: ESS distributed uniformly over r parent rows and K child cells
        final double rowPrior = samplePrior / (double) r;
        final double cellPrior = samplePrior / (double) (K * r);

        double s = 0.0;
        for (int j = 0; j < r; j++) {
            s -= Gamma.logGamma(rowPrior + n_j[j]);
            for (int k = 0; k < K; k++) s += Gamma.logGamma(cellPrior + n_jk[j][k]);
        }
        s += r * Gamma.logGamma(rowPrior);
        s -= (long) K * r * Gamma.logGamma(cellPrior);
        return s;
    }

    /**
     * Calculates the difference in the local score of a given node when an additional node is appended to its parent
     * set, based on the BDeu scoring metric.
     *
     * @param x the index of the additional node to be appended to the parent set.
     * @param y the index of the target node whose local score difference is to be computed.
     * @param z an array of indices representing the initial parent set of the target node.
     * @return the difference in the local score of the target node when the additional node is appended to its parent
     * set.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the difference in the local score of a target node when considering a single node as its parent, based
     * on the BDeu scoring metric.
     *
     * @param x the index of the node to be considered as a potential parent of the target node.
     * @param y the index of the target node whose local score difference is to be computed.
     * @return the difference in the local score of the target node when the node x is considered as its parent.
     */
    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    /**
     * Calculates the local score for a given node and a single parent based on the BDeu scoring metric.
     *
     * @param node   the index of the target node whose local score is to be computed.
     * @param parent the index of the parent node of the target node.
     * @return the local score of the given node and its parent based on the BDeu metric.
     */
    @Override
    public double localScore(int node, int parent) {
        return localScore(node, new int[]{parent});
    }

    /**
     * Calculates the local score for a given node based on the BDeu scoring metric. This is a simplified form of the
     * method that does not consider any parent nodes.
     *
     * @param node the index of the target node whose local score is to be computed.
     * @return the local score of the given node based on the BDeu metric.
     */
    @Override
    public double localScore(int node) {
        return localScore(node, new int[0]);
    }

    /**
     * Computes the local counts for a given node and its parent set. This method also computes the joint counts between
     * a node and its parents based on the provided data. The resulting counts can be used for further analysis, such as
     * statistical scoring or validations.
     *
     * @param node    the index of the target node whose local counts are to be computed
     * @param parents an array of indices representing the parent nodes of the target node
     * @return a CountObjects instance containing the computed counts, including the joint counts of the node with its
     * parents
     */
    // Convenience for callers that also need counts (e.g., CI tests)
    public CountObjects localCounts(int node, int[] parents) {
        final int K = numCategories[node];
        final int P = parents.length;
        final int[] dims = new int[P];
        int r = 1;
        for (int p = 0; p < P; p++) {
            dims[p] = numCategories[parents[p]];
            r *= dims[p];
        }
        if (r <= 0) r = 1;

        final int[][] n_jk = new int[r][K];
        final int[] n_j = new int[r];
        final int[] parentValues = new int[P];
        final int[][] paCols = new int[P][];
        for (int p = 0; p < P; p++) paCols[p] = data[parents[p]];
        final int[] childCol = data[node];

        ROW:
        for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < P; p++) {
                int v = paCols[p][i];
                if (v == MISSING) continue ROW;
                parentValues[p] = v;
            }
            int y = childCol[i];
            if (y == MISSING) continue ROW;
            int j = getRowIndex(dims, parentValues);
            n_jk[j][y]++;
            n_j[j]++;
        }
        return new CountObjects(n_j, n_jk);
    }

    /**
     * Calculates and returns the local count of objects for a given node.
     *
     * @param node the identifier of the node whose local counts are to be calculated
     * @return a CountObjects instance representing the local counts for the given node
     */
    public CountObjects localCounts(int node) {
        return localCounts(node, new int[0]);
    }

    // ============================= utils =============================

    /**
     * Computes the local counts for a given node and its single parent.
     * This method calculates relevant data needed for further statistical analysis and comparisons.
     *
     * @param node   the index of the target node whose local counts are to be computed
     * @param parent the index of the parent node of the target node
     * @return a CountObjects instance containing the computed counts for the target node and its parent
     */
    public CountObjects localCounts(int node, int parent) {
        return localCounts(node, new int[]{parent});
    }

    // ============================== getters ==========================

    /**
     * Retrieves the structure prior value used in the BDeu scoring metric.
     *
     * @return the structure prior value as a double.
     */
    public double getStructurePrior() {
        return structurePrior;
    }

    /**
     * Sets the structure prior value for the BDeu scoring metric.
     *
     * @param structurePrior the structure prior value to set.
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Retrieves the sample prior value used for scoring in the BDeu scoring metric.
     *
     * @return the sample prior value as a double.
     */
    public double getSamplePrior() {
        return samplePrior;
    }

    /**
     * Sets the sample prior value for the BDeu scoring metric.
     *
     * @param samplePrior the sample prior value to set. It should typically be a non-negative value that determines the
     *                    influence of prior knowledge on the scoring metric.
     */
    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    /**
     * Retrieves the list of variables associated with the scoring metric.
     *
     * @return a list of nodes representing the variables used in the BDeu scoring metric.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the list of variables for the scoring metric. This method ensures that
     * the provided variables match the existing schema by comparing the names of each variable
     * at the corresponding indices. If a mismatch is detected, an exception is thrown.
     *
     * @param variables the list of variables to set. Each variable must have
     *                  the same name as the corresponding variable in the
     *                  existing schema.
     * @throws IllegalArgumentException if the provided list contains a variable
     *                                  with a name that does not match the
     *                                  existing schema at the same index.
     */
    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable at index " + i + " has different name (schema mismatch).");
            }
        }
        this.variables = variables;
    }

    /**
     * Retrieves a Node from the list of variables that matches the specified target name.
     *
     * @param targetName the name of the target variable to retrieve. Must not be null.
     * @return the Node with the specified target name, or null if no match is found.
     */
    public Node getVariable(String targetName) {
        for (Node node : variables) if (node.getName().equals(targetName)) return node;
        return null;
    }

    /**
     * Retrieves the sample size used in the BDeu scoring metric. The sample size is
     * typically the number of data points in the dataset being scored.
     *
     * @return the sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Calculates the maximum degree for the current dataset. The degree is determined
     * based on the sample size and is computed as the ceiling of the base-2 logarithm
     * of the sample size, ensuring a minimum value of 2.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(2, sampleSize)));
    }

    /**
     * Determines whether a given list of nodes (z) allows the determination of another node (y)
     * based on specific criteria within the scoring framework.
     *
     * @param z a list of nodes to be evaluated as possible determiners of the target node y.
     * @param y the target node to determine based on the list of nodes z.
     * @return true if the list of nodes z determines the node y, false otherwise.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    /**
     * The CountObjects class encapsulates the counts of objects at different levels of a hierarchy, represented with
     * one-dimensional and two-dimensional arrays. It provides a way to store and access these counts in the context of
     * calculations or applications that require hierarchical data.
     */
    public static class CountObjects {

        /**
         * An array of integers representing the count of objects at the first level of a hierarchy. The length of the
         * array corresponds to the number of elements at that level, denoted as {@code r}. Each element in the array
         * stores the count of objects for a specific category or group.
         */
        public final int[] n_j;        // length r

        /**
         * A two-dimensional array of integers representing the count of objects at the second level of a hierarchy. The
         * array is structured such that each row corresponds to an element of {@code n_j}. Each element in a row stores
         * the count of objects for a specific subcategory or subgroup. The number of rows in the array is equal to
         * {@code r}, and the number of columns in each row is {@code K}.
         */
        public final int[][] n_jk;     // [r][K]

        /**
         * Constructs a CountObjects instance with the given arrays representing object counts.
         *
         * @param n_j  an array of integers representing the count of objects at the first level, must not be null
         * @param n_jk a two-dimensional array of integers representing the count of objects at the second level, where
         *             each row corresponds to an element of {@code n_j}, must not be null
         */
        public CountObjects(final int[] n_j, final int[][] n_jk) {
            this.n_j = n_j;
            this.n_jk = n_jk;
        }
    }
}
