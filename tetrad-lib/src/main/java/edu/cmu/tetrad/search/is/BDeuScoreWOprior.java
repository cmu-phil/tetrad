package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import org.apache.commons.math3.special.Gamma;

import java.util.List;

/**
 * BDeu score (Dirichlet–multinomial, decomposable) with tunable equivalent sample size ("samplePrior")
 * and no structure prior. Counts skip rows with missing in child or any parent (missing sentinel = -99).
 */
public class BDeuScoreWOprior implements Score {

    private static final int MISSING = -99;

    private List<Node> variables;        // schema (all discrete)
    private final int[][] data;          // [var][row] category indices
    private final int sampleSize;        // number of rows

    private double samplePrior = 1.0;    // equivalent sample size (ESS)
    private double structurePrior = 1.0; // unused here, kept for API parity

    private final int[] numCategories;   // arity per variable

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
        for (int i = 0; i < variables.size(); i++) this.numCategories[i] = ((DiscreteVariable) variables.get(i)).getNumCategories();
    }

    private DiscreteVariable getVariable(int i) { return (DiscreteVariable) variables.get(i); }

    // ============================== Score ==============================

    @Override
    public double localScore(int node, int[] parents) {
        final int K = numCategories[node];

        // parent arities & number of parent configs r
        final int P = parents.length;
        final int[] dims = new int[P];
        int r = 1;
        for (int p = 0; p < P; p++) { dims[p] = numCategories[parents[p]]; r *= dims[p]; }
        if (r <= 0) r = 1; // guard though r>=1 by construction

        // counts
        final int[][] n_jk = new int[r][K];
        final int[]   n_j  = new int[r];
        final int[] parentValues = new int[P];

        final int[][] paCols = new int[P][];
        for (int p = 0; p < P; p++) paCols[p] = data[parents[p]];
        final int[] childCol = data[node];

        ROW: for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < P; p++) {
                int v = paCols[p][i];
                if (v == MISSING) continue ROW;     // skip if any parent missing
                parentValues[p] = v;
            }
            int y = childCol[i];
            if (y == MISSING) continue ROW;         // skip if child missing
            int j = getRowIndex(dims, parentValues);
            n_jk[j][y]++; n_j[j]++;
        }

        // BDeu: ESS distributed uniformly over r parent rows and K child cells
        final double rowPrior  = samplePrior / (double) r;
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

    @Override
    public double localScoreDiff(int x, int y, int[] z) { return localScore(y, append(z, x)) - localScore(y, z); }

    @Override
    public double localScoreDiff(int x, int y) { return localScore(y, x) - localScore(y); }

    @Override
    public double localScore(int node, int parent) { return localScore(node, new int[]{parent}); }

    @Override
    public double localScore(int node) { return localScore(node, new int[0]); }

    // Convenience for callers that also need counts (e.g., CI tests)
    public CountObjects localCounts(int node, int[] parents) {
        final int K = numCategories[node];
        final int P = parents.length;
        final int[] dims = new int[P];
        int r = 1; for (int p = 0; p < P; p++) { dims[p] = numCategories[parents[p]]; r *= dims[p]; }
        if (r <= 0) r = 1;

        final int[][] n_jk = new int[r][K];
        final int[]   n_j  = new int[r];
        final int[] parentValues = new int[P];
        final int[][] paCols = new int[P][]; for (int p = 0; p < P; p++) paCols[p] = data[parents[p]];
        final int[] childCol = data[node];

        ROW: for (int i = 0; i < sampleSize; i++) {
            for (int p = 0; p < P; p++) { int v = paCols[p][i]; if (v == MISSING) continue ROW; parentValues[p] = v; }
            int y = childCol[i]; if (y == MISSING) continue ROW;
            int j = getRowIndex(dims, parentValues);
            n_jk[j][y]++; n_j[j]++;
        }
        return new CountObjects(n_j, n_jk);
    }

    public CountObjects localCounts(int node) { return localCounts(node, new int[0]); }
    public CountObjects localCounts(int node, int parent) { return localCounts(node, new int[]{parent}); }

    // ============================= utils =============================

    private static int getRowIndex(int[] dim, int[] values) { int rowIndex = 0; for (int i = 0; i < dim.length; i++) { rowIndex *= dim[i]; rowIndex += values[i]; } return rowIndex; }

    // ============================== getters ==========================

    public double getStructurePrior() { return structurePrior; }
    public double getSamplePrior() { return samplePrior; }
    public void setStructurePrior(double structurePrior) { this.structurePrior = structurePrior; }
    public void setSamplePrior(double samplePrior) { this.samplePrior = samplePrior; }

    @Override public List<Node> getVariables() { return this.variables; }
    public void setVariables(List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(this.variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable at index " + i + " has different name (schema mismatch).");
            }
        }
        this.variables = variables;
    }

    public Node getVariable(String targetName) { for (Node node : variables) if (node.getName().equals(targetName)) return node; return null; }

    @Override public int getSampleSize() { return sampleSize; }
    @Override public int getMaxDegree() { return (int) Math.ceil(Math.log(Math.max(2, sampleSize))); }
    @Override public boolean determines(List<Node> z, Node y) { return false; }

    public static class CountObjects {
        public final int[] n_j;        // length r
        public final int[][] n_jk;     // [r][K]
        public CountObjects(final int[] n_j, final int[][] n_jk) { this.n_j = n_j; this.n_jk = n_jk; }
    }
}
