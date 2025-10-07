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
 * For Z≠∅: splits the data into rows matching the instance’s values on Z (IS) and the rest (POP),
 * then applies BDeu sequentially to propagate the prior.</p>
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
        for (int i = 0; i < data.getNumRows(); i++) for (int j = 0; j < data.getNumColumns(); j++) dataArr[i][j] = data.getInt(i, j);
        this.testArr = new int[1][test.getNumColumns()];
        for (int j = 0; j < test.getNumColumns(); j++) testArr[0][j] = test.getInt(0, j);

        // nodes & indices
        this.nodes = data.getVariables();
        this.indices = new HashMap<>(nodes.size() * 2);
        for (int c = 0; c < nodes.size(); c++) indices.put(nodes.get(c), c);

        // strict category alignment guard
        for (int c = 0; c < nodes.size(); c++) {
            Node tv = data.getVariable(c), iv = test.getVariable(c);
            if (!tv.getName().equals(iv.getName())) throw new IllegalArgumentException("Variable name mismatch at column " + c);
            if (tv instanceof DiscreteVariable tdv) {
                if (!(iv instanceof DiscreteVariable idv)) throw new IllegalArgumentException("Train discrete but instance not: " + tv.getName());
                if (!tdv.getCategories().equals(idv.getCategories())) throw new IllegalArgumentException("Category labels differ for " + tv.getName());
            }
        }
    }

    // ----------------------------- API -----------------------------

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) { throw new UnsupportedOperationException("Subset unsupported"); }

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

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return checkIndependence(x, y, z.toArray(new Node[0]));
    }

    @Override public List<Node> getVariables() { return nodes; }

    @Override public Node getVariable(String name) {
        for (Node n : nodes) if (name.equals(n.getName())) return n; return null; }

    @Override public List<String> getVariableNames() {
        List<String> names = new ArrayList<>(nodes.size()); for (Node n : nodes) names.add(n.getName()); return names; }

    @Override public double getAlpha() { throw new UnsupportedOperationException(); }
    @Override public void setAlpha(double alpha) { throw new UnsupportedOperationException(); }

    @Override public DataModel getData() { return data; }
    @Override public ICovarianceMatrix getCov() { return null; }

    @Override public List<DataSet> getDataSets() { return Collections.singletonList(data); }
    @Override public int getSampleSize() { return data.getNumRows(); }

    public double getPosterior() { return posterior; }
    @Override public boolean isVerbose() { return verbose; }
    @Override public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void setThreshold(boolean thresholdMode) { this.threshold = thresholdMode; }
    public void setCutoff(double cutoff) { this.cutoff = cutoff; }

    /** Expose (read‑only) cache for diagnostics. */
    public Map<IndependenceFact, Double> getH() { return Collections.unmodifiableMap(cache); }

    // -------------------------- Internals ---------------------------

    private int requireIndex(Node n) {
        Integer c = indices.get(n); if (c == null) throw new IllegalArgumentException("Unknown node: " + n); return c; }

    /** Compute P(independent | D) comparing X ⟂ Y | Z vs X→Y | Z with BDeu log scores and prior. */
    private double computeInd(final BDeuScoreWOprior bdeu, final double priorProb, final Node x, final Node y, final Node... z) {
        if (!(priorProb > 0.0 && priorProb < 1.0)) throw new IllegalArgumentException("prior must be in (0,1)");

        final List<Node> scope = new ArrayList<>(); scope.add(x); scope.add(y); Collections.addAll(scope, z);

        // independent BN: Z→X and Z→Y (no X–Y arc)
        Graph indBN = new EdgeListGraph(scope);
        for (Node n : z) { indBN.addDirectedEdge(n, x); indBN.addDirectedEdge(n, y); }
        double lnPInd = Math.log(priorProb);
        double lnScoreInd = scoreDag(indBN, bdeu);
        double lnAllInd = lnScoreInd + lnPInd;

        // dependent BN: add X→Y (could also consider Y→X; BDeu is decomposable but we pick one direction)
        Graph depBN = new EdgeListGraph(scope);
        depBN.addDirectedEdge(x, y);
        for (Node n : z) { depBN.addDirectedEdge(n, x); depBN.addDirectedEdge(n, y); }
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
            int[] paIdx = new int[parents.size()]; int c = 0; for (Node p : parents) paIdx[c++] = requireIndex(p);
            int yIdx = requireIndex(y);
            score += bdeu.localScore(yIdx, paIdx);
        }
        return score;
    }

    private static double logSumExp(double a, double b) {
        if (a < b) { double t = a; a = b; b = t; }
        double diff = b - a;
        return (diff < -745.0) ? a : a + Math.log1p(Math.exp(diff));
    }

    /** Split {@code data} into rows matching the instance’s Z values (→ dataIS) and the rest (→ dataPOP). */
    private void splitDataOnZ(DataSet dataIS, DataSet dataPOP, int[] zCols) {
        final int n = data.getNumRows();
        Set<Integer> is = new HashSet<>();
        for (int r = 0; r < n; r++) {
            boolean match = true;
            for (int k = 0; k < zCols.length; k++) {
                int col = zCols[k];
                int vInst = testArr[0][col];
                int vRow  = dataArr[r][col];
                if (vInst == MISSING || vRow == MISSING || vRow != vInst) { match = false; break; }
            }
            if (match) is.add(r);
        }
        int[] isRows  = is.stream().mapToInt(Integer::intValue).sorted().toArray();
        int[] popRows = new int[n - isRows.length];
        for (int r = 0, c = 0; r < n; r++) if (!is.contains(r)) popRows[c++] = r;
        Arrays.sort(popRows);

        dataIS.removeRows(popRows);
        dataPOP.removeRows(isRows);
    }
}
