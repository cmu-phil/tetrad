package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * Probabilistic conditional‐independence test using BDeu (instance‑specific split on Z).
 *
 * <p>For Z=∅: compares the two DAGs X←Z→Y (empty here) vs X→Y with BDeu scores and prior P(independent)=prior.
 * For Z≠∅: splits the data into rows matching the instance’s values on Z (IS) and the rest (POP), then applies BDeu
 * sequentially to propagate the prior.</p>
 */
public class IndTestProbabilisticISBDeu implements IndependenceTest {

    private static final int MISSING = -99;

    private final DataSet data;           // training (discrete)
    private final DataSet test;           // single‐row (discrete)
    private final int[][] dataArr;        // [row][col]
    private final int[][] testArr;        // [row][col] (1×p)

    private final List<Node> nodes;
    private final Map<Node, Integer> indices; // node → column index

    private final Map<IndependenceFact, Double> cache = new HashMap<>();

    private double prior = 0.5;   // P(independent) prior
    private boolean threshold = false;
    private double cutoff = 0.5;
    private boolean verbose = false;

    private double posterior;     // last computed P(independent | D)

    // ----------------------------- Ctor -----------------------------

    /**
     * Constructs an IndTestProbabilisticISBDeu object for probabilistic independence testing based on BDeu scoring,
     * using a specified prior probability. Validates the consistency and format of the training data (`dataSet`) and
     * testing data (`test`), ensuring they are discrete datasets with aligned schema, variable names, and categories.
     *
     * @param dataSet the training dataset, which must be discrete and non-null
     * @param test    the test dataset, which must be discrete, non-null, with exactly one row and column schema
     *                matching the training dataset
     * @param prior   the prior probability to be used in BDeu scoring
     * @throws NullPointerException     if either the training or test dataset is null
     * @throws IllegalArgumentException if the datasets are not discrete, if the test dataset does not have exactly one
     *                                  row, if the column schema of the datasets does not match, or if the variable
     *                                  names or category labels between datasets differ
     */
    public IndTestProbabilisticISBDeu(final DataSet dataSet, final DataSet test, final double prior) {
        if (dataSet == null || test == null) throw new NullPointerException("data and test must be non‑null");
        if (!dataSet.isDiscrete() || !test.isDiscrete()) throw new IllegalArgumentException("Discrete data required");
        if (test.getNumRows() != 1) throw new IllegalArgumentException("Test dataset must have exactly one row");
        if (dataSet.getNumColumns() != test.getNumColumns()) throw new IllegalArgumentException("Schema mismatch");
        this.prior = prior;
        this.data = dataSet;
        this.test = test;

        // arrays as [row][col]
        this.dataArr = new int[data.getNumRows()][data.getNumColumns()];
        for (int i = 0; i < data.getNumRows(); i++)
            for (int j = 0; j < data.getNumColumns(); j++) dataArr[i][j] = data.getInt(i, j);
        this.testArr = new int[1][test.getNumColumns()];
        for (int j = 0; j < test.getNumColumns(); j++) testArr[0][j] = test.getInt(0, j);

        // nodes & indices
        this.nodes = data.getVariables();
        this.indices = new HashMap<>(nodes.size() * 2);
        for (int c = 0; c < nodes.size(); c++) indices.put(nodes.get(c), c);

        // strict category alignment guard
        for (int c = 0; c < nodes.size(); c++) {
            Node tv = data.getVariable(c), iv = test.getVariable(c);
            if (!tv.getName().equals(iv.getName()))
                throw new IllegalArgumentException("Variable name mismatch at column " + c);
            if (tv instanceof DiscreteVariable tdv) {
                if (!(iv instanceof DiscreteVariable idv))
                    throw new IllegalArgumentException("Train discrete but instance not: " + tv.getName());
                if (!tdv.getCategories().equals(idv.getCategories()))
                    throw new IllegalArgumentException("Category labels differ for " + tv.getName());
            }
        }
    }

    // ----------------------------- API -----------------------------

    private static double logSumExp(double a, double b) {
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        double diff = b - a;
        return (diff < -745.0) ? a : a + Math.log1p(Math.exp(diff));
    }

    /**
     * Returns an independence test for a subset of variables. The method is not supported and will throw an
     * UnsupportedOperationException when invoked.
     *
     * @param vars the subset of variables to be tested for independence, represented as a list of nodes
     * @return nothing, as the method is not implemented and will always throw an exception
     * @throws UnsupportedOperationException if the method is invoked
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Subset unsupported");
    }

    /**
     * Evaluates the independence between two nodes, x and y, given a set of conditioning nodes, z. The method
     * calculates a probabilistic measure of independence using Bayesian Dirichlet equivalent uniform (BDeu) scoring and
     * potentially splits the dataset based on the values of the conditioning nodes. It caches the result for efficiency
     * and determines independence based on a specified threshold or a random draw.
     *
     * @param x the first node whose independence with the second node is being tested, must not be null
     * @param y the second node whose independence with the first node is being tested, must not be null
     * @param z an optional vararg array of conditioning nodes, can be empty but must not contain null values; these are
     *          the nodes upon which the independence test will condition
     * @return an IndependenceResult object containing the independence fact, the computed probability, and whether the
     * nodes are considered independent based on the threshold
     */
    @Override
    public IndependenceResult checkIndependence(final Node x, final Node y, final Node... z) {
        final IndependenceFact fact = new IndependenceFact(x, y, z);
        Double cached = cache.get(fact);
        double pInd;
        if (cached != null) {
            pInd = cached;
        } else {
            // convert Z to column indices
            final int[] zCols = new int[z.length];
            for (int i = 0; i < z.length; i++) zCols[i] = requireIndex(z[i]);

            if (zCols.length == 0) {
                BDeuScoreWOprior bdeu = new BDeuScoreWOprior(this.data);
                pInd = computeInd(bdeu, this.prior, x, y, z);
            } else {
                // Split on instance’s Z values
                DataSet dataIS = new BoxDataSet((BoxDataSet) this.data);
                DataSet dataPOP = new BoxDataSet((BoxDataSet) this.data);
                splitDataOnZ(dataIS, dataPOP, zCols);

                BDeuScoreWOprior bdeuPOP = new BDeuScoreWOprior(dataPOP);
                double priorP = computeInd(bdeuPOP, this.prior, x, y, z);

                BDeuScoreWOprior bdeuIS = new BDeuScoreWOprior(dataIS);
                pInd = computeInd(bdeuIS, priorP, x, y, z);
            }
            cache.put(fact, pInd);
        }

        this.posterior = pInd;
        final boolean independent = threshold ? (pInd >= cutoff) : (RandomUtil.getInstance().nextDouble() < pInd);
        return new IndependenceResult(fact, independent, pInd, Double.NaN);
    }

    /**
     * Checks the probabilistic independence between two nodes, x and y, given a set of conditioning nodes, z. The
     * method leverages Bayesian scoring and calculates independence based on the provided threshold or cutoff.
     *
     * @param x the first node to test for independence, must not be null
     * @param y the second node to test for independence, must not be null
     * @param z the set of conditioning nodes on which the independence is conditioned, must not be null but may be
     *          empty
     * @return an IndependenceResult object representing the independence fact, including the computed probability and
     * the determination of independence
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return checkIndependence(x, y, z.toArray(new Node[0]));
    }

    /**
     * Retrieves the list of variables represented as nodes in the model.
     *
     * @return a list of nodes representing the variables in the model
     */
    @Override
    public List<Node> getVariables() {
        return nodes;
    }

    /**
     * Retrieves a node corresponding to the given variable name. Iterates over the list of nodes to find a match where
     * the name of the node matches the specified variable name. If no match is found, returns null.
     *
     * @param name the name of the variable for which the corresponding node is to be retrieved, must not be null
     * @return the node corresponding to the provided variable name if found; otherwise, null
     */
    @Override
    public Node getVariable(String name) {
        for (Node n : nodes) if (name.equals(n.getName())) return n;
        return null;
    }

    /**
     * Retrieves the names of the variables represented in the model.
     *
     * @return a list of strings, where each string is the name of a variable
     */
    @Override
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>(nodes.size());
        for (Node n : nodes) names.add(n.getName());
        return names;
    }

    /**
     * Retrieves the alpha value used in the model computation.
     *
     * @return the alpha value as a double
     */
    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the alpha value used in the model computation.
     *
     * @param alpha This level.
     */
    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the data model used in the computation.
     *
     * @return the data model
     */
    @Override
    public DataModel getData() {
        return data;
    }

    /**
     * Retrieves the covariance matrix used in the computation.
     *
     * @return the covariance matrix
     */
    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    /**
     * Retrieves the list of data sets used in the computation.
     *
     * @return a list containing the data set
     */
    @Override
    public List<DataSet> getDataSets() {
        return Collections.singletonList(data);
    }

    /**
     * Retrieves the sample size of the data set.
     *
     * @return the sample size
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Retrieves the posterior probability of the model.
     *
     * @return the posterior probability
     */
    public double getPosterior() {
        return posterior;
    }

    /**
     * Determines if verbose mode is enabled for logging or output.
     *
     * @return true if verbose mode is enabled; false otherwise
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbose mode for the model. When enabled, additional logging or output may be provided to indicate the
     * progression or internal workings of the computations.
     *
     * @param verbose a boolean value indicating whether verbose mode should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the threshold mode for the model, determining whether a specific threshold-based mechanism is enabled during
     * independence testing or computation.
     *
     * @param thresholdMode a boolean value indicating the threshold mode; true to enable threshold-based computations,
     *                      false to disable
     */
    public void setThreshold(boolean thresholdMode) {
        this.threshold = thresholdMode;
    }

    /**
     * Sets the cutoff value used in the model. The cutoff is a threshold determining specific conditions or criteria
     * during computations.
     *
     * @param cutoff the cutoff value to be set, represented as a double
     */
    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    // -------------------------- Internals ---------------------------

    /**
     * Retrieves an unmodifiable map of independence facts and their associated probabilities.
     * The map is cached to improve efficiency in probabilistic independence testing.
     *
     * @return an unmodifiable map where keys represent instances of {@code IndependenceFact} and
     * values are their corresponding probabilities as {@code Double}.
     */
    public Map<IndependenceFact, Double> getH() {
        return Collections.unmodifiableMap(cache);
    }

    private int requireIndex(Node n) {
        Integer c = indices.get(n);
        if (c == null) throw new IllegalArgumentException("Unknown node: " + n);
        return c;
    }

    /**
     * Compute P(independent | D) comparing X ⟂ Y | Z vs X→Y | Z with BDeu log scores and prior.
     */
    private double computeInd(final BDeuScoreWOprior bdeu, final double priorProb, final Node x, final Node y, final Node... z) {
        if (!(priorProb > 0.0 && priorProb < 1.0)) throw new IllegalArgumentException("prior must be in (0,1)");

        final List<Node> scope = new ArrayList<>();
        scope.add(x);
        scope.add(y);
        Collections.addAll(scope, z);

        // independent BN: Z→X and Z→Y (no X–Y arc)
        Graph indBN = new EdgeListGraph(scope);
        for (Node n : z) {
            indBN.addDirectedEdge(n, x);
            indBN.addDirectedEdge(n, y);
        }
        double lnPInd = Math.log(priorProb);
        double lnScoreInd = scoreDag(indBN, bdeu);
        double lnAllInd = lnScoreInd + lnPInd;

        // dependent BN: add X→Y (could also consider Y→X; BDeu is decomposable but we pick one direction)
        Graph depBN = new EdgeListGraph(scope);
        depBN.addDirectedEdge(x, y);
        for (Node n : z) {
            depBN.addDirectedEdge(n, x);
            depBN.addDirectedEdge(n, y);
        }
        double lnPDep = Math.log(1.0 - priorProb);                // FIX: was log(1 - log(prior))
        double lnScoreDep = scoreDag(depBN, bdeu);
        double lnAllDep = lnScoreDep + lnPDep;

        // log P(ind | D) = logsumexp(lnAllInd) – logsumexp(lnAllInd, lnAllDep)
        double lnDen = logSumExp(lnAllInd, lnAllDep);
        return Math.exp(lnAllInd - lnDen);
    }

    private double scoreDag(Graph dag, BDeuScoreWOprior bdeu) {
        double score = 0.0;
        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int[] paIdx = new int[parents.size()];
            int c = 0;
            for (Node p : parents) paIdx[c++] = requireIndex(p);
            int yIdx = requireIndex(y);
            score += bdeu.localScore(yIdx, paIdx);
        }
        return score;
    }

    /**
     * Split {@code data} into rows matching the instance’s Z values (→ dataIS) and the rest (→ dataPOP).
     */
    private void splitDataOnZ(DataSet dataIS, DataSet dataPOP, int[] zCols) {
        final int n = data.getNumRows();
        Set<Integer> is = new HashSet<>();
        for (int r = 0; r < n; r++) {
            boolean match = true;
            for (int k = 0; k < zCols.length; k++) {
                int col = zCols[k];
                int vInst = testArr[0][col];
                int vRow = dataArr[r][col];
                if (vInst == MISSING || vRow == MISSING || vRow != vInst) {
                    match = false;
                    break;
                }
            }
            if (match) is.add(r);
        }
        int[] isRows = is.stream().mapToInt(Integer::intValue).sorted().toArray();
        int[] popRows = new int[n - isRows.length];
        for (int r = 0, c = 0; r < n; r++) if (!is.contains(r)) popRows[c++] = r;
        Arrays.sort(popRows);

        dataIS.removeRows(popRows);
        dataPOP.removeRows(isRows);
    }
}
