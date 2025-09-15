package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.StrictMath.log;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Fisher's Z CI test with shrinkage (RIDGE/Ledoit–Wolf) and optional pseudoinverse fallback.
 */
public final class IndTestFisherZ implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable,
        RawMarginalIndependenceTest {

    private final Map<String, Integer> indexMap;
    private final Map<String, Node> nameMap;
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    private final int sampleSize;
    private ICovarianceMatrix cor = null;
    private List<Node> variables;
    private double alpha;
    private DataSet dataSet;
    private boolean verbose = false;
    private double r = Double.NaN;                 // last partial correlation
    private List<Integer> rows = null;
    /**
     * Kept for back-compat only (ignored in new path).
     */
    private double lambda = 0.0;
    /**
     * Ridge amount for RIDGE mode.
     */
    private double ridge = 0.0;
    /**
     * Ledoit–Wolf / Ridge / None.
     */
    private ShrinkageMode shrinkageMode = ShrinkageMode.NONE;
    /**
     * Last LW delta used (debugging only).
     */
    private double lastLedoitWolfDelta = Double.NaN;
    /**
     * Pseudoinverse controls (OFF by default).
     */
    private boolean usePseudoinverse = false;
    private double pinvTolerance = 1e-7;
    private int nEff;

    public IndTestFisherZ(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        if (!(dataSet.isContinuous())) throw new IllegalArgumentException("Data set must be continuous.");

        if (!dataSet.existsMissingValue()) {
            this.cor = new CorrelationMatrix(dataSet);
            this.variables = this.cor.getVariables();
            this.indexMap = indexMap(this.variables);
            this.nameMap = nameMap(this.variables);
            setAlpha(alpha);
        } else {
            if (!(alpha >= 0 && alpha <= 1)) throw new IllegalArgumentException("Alpha must be in [0,1]");
            List<Node> nodes = dataSet.getVariables();
            this.variables = Collections.unmodifiableList(nodes);
            this.indexMap = indexMap(this.variables);
            this.nameMap = nameMap(this.variables);
            setAlpha(alpha);
        }
    }

    /* ======================= Constructors ======================= */

    public IndTestFisherZ(DataSet dataSet, double alpha, double ridge) {
        this(dataSet, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    public IndTestFisherZ(Matrix data, List<Node> variables, double alpha) {
        this.dataSet = new BoxDataSet(new VerticalDoubleDataBox(data.transpose().toArray()), variables);
        this.cor = SimpleDataLoader.getCorrelationMatrix(this.dataSet);
        this.variables = Collections.unmodifiableList(variables);
        this.indexMap = indexMap(variables);
        this.nameMap = nameMap(variables);
        this.sampleSize = data.getNumRows();
        setEffectiveSampleSize(-1);
        setAlpha(alpha);
    }

    public IndTestFisherZ(Matrix data, List<Node> variables, double alpha, double ridge) {
        this(data, variables, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha) {
        this.cor = new CorrelationMatrix(covMatrix);
        this.variables = covMatrix.getVariables();
        this.indexMap = indexMap(this.variables);
        this.nameMap = nameMap(this.variables);
        this.sampleSize = covMatrix.getSampleSize();
        setEffectiveSampleSize(-1);
        setAlpha(alpha);
    }

    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha, double ridge) {
        this(covMatrix, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    /**
     * Compute partial corr between index 0 (x) and 1 (y) from precision Ω.
     */
    private static double partialFromPrecision(RealMatrix P) {
        double w11 = P.getEntry(0, 0);
        double w22 = P.getEntry(1, 1);
        double w12 = P.getEntry(0, 1);
        if (w11 <= 0 || w22 <= 0) throw new RuntimeException("Nonpositive diagonal in precision.");
        return -w12 / Math.sqrt(w11 * w22);
    }

    /* ======================= API ======================= */

    private static RealMatrix toReal(Matrix m) {
        return new Array2DRowRealMatrix(m.toArray(), true);
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) throw new IllegalArgumentException("Subset may not be empty.");
        for (Node var : vars)
            if (!this.variables.contains(var)) throw new IllegalArgumentException("All vars must be original vars");

        int[] indices = new int[vars.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = this.indexMap.get(vars.get(i).getName());
        ICovarianceMatrix newCovMatrix = this.cor.getSubmatrix(indices);

        IndTestFisherZ t = new IndTestFisherZ(newCovMatrix, getAlpha());
        t.setLambda(this.lambda); // legacy no-op
        t.setRidge(this.ridge);
        t.setShrinkageMode(this.shrinkageMode);
        t.setUsePseudoinverse(this.usePseudoinverse);
        t.setPinvTolerance(this.pinvTolerance);
        t.setEffectiveSampleSize(getEffectiveSampleSize());
        t.setVerbose(this.verbose);
        return t;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p;
        try {
            p = getPValue(x, y, z);
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singular matrix encountered for test: " + LogUtilsSearch.independenceFact(x, y, z));
        }

        boolean independent = p > this.alpha;

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value for test: " + LogUtilsSearch.independenceFact(x, y, z));
        } else {
            IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
            if (this.verbose && independent) {
                TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
            }
            return result;
        }
    }

    public double getPValue(Node x, Node y, Set<Node> z) throws SingularMatrixException {
        double r;
        int n;
        if (covMatrix() != null) {
            r = partialCorrelation(x, y, z, rows);
            n = getEffectiveSampleSize();
        } else {
            List<Integer> rows = listRows();
            r = partialCorrelation(x, y, z, rows);
            n = rows.size();
        }

        this.r = r;
        double q = .5 * (log(1.0 + abs(r)) - log(1.0 - abs(r)));
        double df = n - 3. - z.size();
        if (df < 1) {
            throw new IllegalArgumentException("Nonpositive df for " + x + " _||_ " + y + " | " + z + " (n=" + n + ", df=" + df + ")");
        }
        double fisherZ = sqrt(df) * q;
        return 2 * (1.0 - this.normal.cumulativeProbability(fisherZ));
    }

    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.nEff = effectiveSampleSize < 0 ? this.sampleSize : effectiveSampleSize;
    }

    public double getBic() {
        return -getEffectiveSampleSize() * FastMath.log(1.0 - this.r * this.r) - FastMath.log(getEffectiveSampleSize());
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Significance out of range: " + alpha);
        this.alpha = alpha;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.cor.setVariables(variables);
    }

    public Node getVariable(String name) {
        return this.nameMap.get(name);
    }

    public DataSet getData() {
        return this.dataSet;
    }

    public ICovarianceMatrix getCov() {
        return this.cor;
    }

    @Override
    public List<DataSet> getDataSets() {
        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(this.dataSet);
        return dataSets;
    }

    @Override
    public int getSampleSize() {
        if (dataSet != null) return dataSet.getNumRows();
        else return this.cor.getSampleSize();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String toString() {
        DecimalFormat f1 = new DecimalFormat("0.0###");
        DecimalFormat f2 = new DecimalFormat("0.0#####");
        String base = "Fisher Z, alpha = " + f1.format(getAlpha());
        base += ", shrinkage=" + shrinkageMode;
        if (shrinkageMode == ShrinkageMode.RIDGE && ridge > 0.0) base += "(ridge=" + f2.format(ridge) + ")";
        if (shrinkageMode == ShrinkageMode.LEDOIT_WOLF && !Double.isNaN(lastLedoitWolfDelta))
            base += "(delta=" + f2.format(lastLedoitWolfDelta) + ")";
        if (usePseudoinverse) base += ", pinv tol=" + f2.format(pinvTolerance);
        return base;
    }

    /* ======================= Core ======================= */

    /**
     * Determinism detection unchanged (no shrinkage here; we want true singularity).
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];
        for (int j = 0; j < parents.length; j++) parents[j] = indexMap.get(z.get(j).getName());
        if (parents.length > 0) {
            Matrix Czz = this.cor.getSelection(parents, parents);
            try {
                Czz.inverse();
            } catch (SingularMatrixException e) {
                TetradLogger.getInstance().log(LogUtilsSearch.determinismDetected(new HashSet<>(z), x));
                return true;
            }
        }
        return false;
    }

    private double partialCorrelation(Node x, Node y, Set<Node> _z, List<Integer> rows) throws SingularMatrixException {
        List<Node> z = new ArrayList<>(_z);

        int[] indices = new int[z.size() + 2];
        indices[0] = this.indexMap.get(x.getName());
        indices[1] = this.indexMap.get(y.getName());
        for (int i = 0; i < z.size(); i++) indices[i + 2] = this.indexMap.get(z.get(i).getName());

        Matrix corSub;
        if (this.cor != null) {
            corSub = this.cor.getSelection(indices, indices); // correlation submatrix
        } else {
            Matrix cov = SemBicScore.getCov(rows, indices, indices, this.dataSet, null);
            corSub = edu.cmu.tetrad.util.MatrixUtils.convertCovToCorr(cov);
        }

        // Apply shrinkage
        switch (this.shrinkageMode) {
            case RIDGE -> {
                if (this.ridge > 0.0) {
                    Matrix tmp = corSub.copy();
                    for (int i = 0; i < tmp.getNumRows(); i++) {
                        tmp.set(i, i, tmp.get(i, i) + this.ridge);
                    }
                    corSub = tmp;
                }
            }
            case LEDOIT_WOLF -> {
                int p = corSub.getNumRows();
                int n = (this.cor != null ? getEffectiveSampleSize() : (rows == null ? getSampleSize() : rows.size()));
                if (p >= 2 && n > 1) {
                    double denom = 0.0, num = 0.0;
                    for (int i = 0; i < p; i++) {
                        double riiMinus1 = corSub.get(i, i) - 1.0;
                        denom += riiMinus1 * riiMinus1;
                        for (int j = i + 1; j < p; j++) {
                            double rij = corSub.get(i, j);
                            denom += 2.0 * rij * rij;
                            double var = (1.0 - rij * rij);
                            var = var * var / (n - 1.0);
                            num += 2.0 * var;
                        }
                    }
                    double delta = 0.0;
                    if (denom > 0.0) delta = Math.min(1.0, Math.max(0.0, num / denom));
                    this.lastLedoitWolfDelta = delta;
                    if (delta > 0.0) {
                        Matrix I = Matrix.identity(p);
                        Matrix shrunk = corSub.copy().scalarMult(1.0 - delta).plus(I.scalarMult(delta));
                        corSub = shrunk;
                    }
                } else {
                    this.lastLedoitWolfDelta = Double.NaN;
                }
            }
            case NONE -> { /* no-op */ }
        }

        // Try standard inversion via Cholesky; fallback to pseudoinverse if requested.
        try {
            return partialViaCholesky(corSub);
        } catch (RuntimeException e) {
            if (!usePseudoinverse) {
                // Mirror previous behavior: surface as singular unless pinv allowed
                throw new SingularMatrixException();
            }
            return partialViaEigenPinv(corSub, pinvTolerance);
        }
    }

    /**
     * Fast path: Cholesky on SPD correlation; throws if not SPD.
     */
    private double partialViaCholesky(Matrix corSub) {
        RealMatrix A = toReal(corSub);
        // The small "relativeSymmetryThreshold" & "absolutePositivityThreshold" keep it strict.
        CholeskyDecomposition chol = new CholeskyDecomposition(A, 1e-10, 1e-12);
        RealMatrix L = chol.getL();
        // Precision = (L^{-T} L^{-1})
        DecompositionSolver solver = chol.getSolver();
        RealMatrix I = MatrixUtilsCommons.identity(A.getRowDimension());
        RealMatrix P = solver.solve(I); // this gives A^{-1}
        return partialFromPrecision(P);
    }

    /**
     * Robust path: symmetric eigen pinv with relative cutoff.
     */
    private double partialViaEigenPinv(Matrix corSub, double tolRel) {
        RealMatrix A = toReal(corSub);
        EigenDecomposition eig = new EigenDecomposition(SymmetricMatrixUtils.forceSymmetric(A));
        double[] vals = eig.getRealEigenvalues();
        RealMatrix V = eig.getV();

        double maxEig = 0.0;
        for (double v : vals) maxEig = Math.max(maxEig, Math.abs(v));
        double cut = tolRel * (maxEig > 0 ? maxEig : 1.0);

        // Build precision = V diag(1/max(eig,cut)) V^T
        int p = vals.length;
        double[][] Dinv = new double[p][p];
        for (int i = 0; i < p; i++) {
            double v = vals[i];
            double adj = Math.abs(v) < cut ? cut : v;
            Dinv[i][i] = 1.0 / adj;
        }
        RealMatrix Pinv = V.multiply(new Array2DRowRealMatrix(Dinv)).multiply(V.transpose());

        return partialFromPrecision(Pinv);
    }

    private ICovarianceMatrix covMatrix() {
        return this.cor;
    }

    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<>();
        for (Node node : variables) nameMap.put(node.getName(), node);
        return nameMap;
    }

    private Map<String, Integer> indexMap(List<Node> variables) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) indexMap.put(variables.get(i).getName(), i);
        return indexMap;
    }

    private List<Integer> listRows() {
        if (this.rows != null) return this.rows;
        List<Integer> rows = new ArrayList<>();
        for (int k = 0; k < this.dataSet.getNumRows(); k++) rows.add(k);
        return rows;
    }

    public List<Integer> getRows() {
        return rows;
    }

    /* ======================= Helpers & setters ======================= */

    public void setRows(List<Integer> rows) {
        if (dataSet == null) return;
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }
            this.rows = rows;
            cor = null; // recompute from rows
        }
    }

    /**
     * Back-compat no-op for external code that still calls setLambda.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public double getRidge() {
        return this.ridge;
    }

    public void setRidge(double ridge) {
        if (ridge < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
    }

    public ShrinkageMode getShrinkageMode() {
        return this.shrinkageMode;
    }

    public void setShrinkageMode(ShrinkageMode mode) {
        if (mode == null) mode = ShrinkageMode.NONE;
        this.shrinkageMode = mode;
    }

    public boolean isUsePseudoinverse() {
        return this.usePseudoinverse;
    }

    /**
     * NEW: pseudoinverse controls
     */
    public void setUsePseudoinverse(boolean use) {
        this.usePseudoinverse = use;
    }

    public double getPinvTolerance() {
        return this.pinvTolerance;
    }

    public void setPinvTolerance(double tol) {
        if (tol <= 0) throw new IllegalArgumentException("pinvTolerance must be > 0");
        this.pinvTolerance = tol;
    }

    /**
     * Optional: expose last partial correlation for logging.
     */
    public double getLastR() {
        return this.r;
    }

    public double getRho() {
        return r;
    }

    @Override
    public double computePValue(double[] x, double[] y) {
        double[][] combined = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            combined[i][0] = x[i];
            combined[i][1] = y[i];
        }
        Node _x = new ContinuousVariable("X_computePValue");
        Node _y = new ContinuousVariable("Y_computePValue");
        List<Node> nodes = new ArrayList<>();
        nodes.add(_x);
        nodes.add(_y);
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(combined), nodes);

        IndTestFisherZ test = new IndTestFisherZ(dataSet, alpha);
        test.setRidge(this.ridge);
        test.setShrinkageMode(this.shrinkageMode);
        test.setUsePseudoinverse(this.usePseudoinverse);
        test.setPinvTolerance(this.pinvTolerance);

        return test.getPValue(_x, _y, new HashSet<>());
    }

    /**
     * Shrinkage mode.
     */
    public enum ShrinkageMode {NONE, RIDGE, LEDOIT_WOLF}

    private static class MatrixUtilsCommons {
        static RealMatrix identity(int n) {
            return MatrixUtilsCommonsDiag.identity(n);
        }
    }

    private static class MatrixUtilsCommonsDiag {
        static RealMatrix identity(int n) {
            double[][] a = new double[n][n];
            for (int i = 0; i < n; i++) a[i][i] = 1.0;
            return new Array2DRowRealMatrix(a, false);
        }
    }

    private static class SymmetricMatrixUtils {
        static RealMatrix forceSymmetric(RealMatrix A) {
            int n = A.getRowDimension();
            double[][] s = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    s[i][j] = 0.5 * (A.getEntry(i, j) + A.getEntry(j, i));
                }
            }
            return new Array2DRowRealMatrix(s, false);
        }
    }
}