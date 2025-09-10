package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.StrictMath.log;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Checks conditional independence using Fisher's Z on (optionally regularized) correlations.
 * Regularization options:
 *  - NONE (default)
 *  - RIDGE: add a diagonal ridge to the correlation submatrix before inversion
 *  - LEDOIT_WOLF: shrink correlation submatrix toward identity using a Ledoit–Wolf style intensity
 */
public final class IndTestFisherZ implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable,
        RawMarginalIndependenceTest {

    /** Shrinkage mode. */
    public enum ShrinkageMode { NONE, RIDGE, LEDOIT_WOLF }

    private final Map<String, Integer> indexMap;
    private final Map<String, Node> nameMap;
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    private final int sampleSize;

    private ICovarianceMatrix cor = null;
    private List<Node> variables;
    private double alpha;
    private DataSet dataSet;
    private boolean verbose = false;
    private double r = Double.NaN;
    private List<Integer> rows = null;

    /** Passed through to precision-based partial corr util (unchanged behavior). */
    private double lambda = 0.0;

    /** Ridge amount (diagonal Tikhonov) for RIDGE mode. */
    private double ridge = 0.0;

    /** Shrinkage mode selector (default NONE). */
    private ShrinkageMode shrinkageMode = ShrinkageMode.LEDOIT_WOLF;

    /** Last LW delta used (for debugging / toString). */
    private double lastLedoitWolfDelta = Double.NaN;

    private int nEff;

    /* ======================= Constructors ======================= */

    public IndTestFisherZ(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!dataSet.existsMissingValue()) {
            this.cor = new CorrelationMatrix(dataSet);
            this.variables = this.cor.getVariables();
            this.indexMap = indexMap(this.variables);
            this.nameMap = nameMap(this.variables);
            setAlpha(alpha);
        } else {
            if (!(alpha >= 0 && alpha <= 1)) {
                throw new IllegalArgumentException("Alpha mut be in [0, 1]");
            }

            List<Node> nodes = dataSet.getVariables();

            this.variables = Collections.unmodifiableList(nodes);
            this.indexMap = indexMap(this.variables);
            this.nameMap = nameMap(this.variables);
            setAlpha(alpha);
        }
    }

    public IndTestFisherZ(DataSet dataSet, double alpha, double ridge) {
        this(dataSet, alpha);
        setRidge(ridge);
        setShrinkageMode(ShrinkageMode.RIDGE);
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
        setRidge(ridge);
        setShrinkageMode(ShrinkageMode.RIDGE);
    }

    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha) {
        this.cor = new CorrelationMatrix(covMatrix); // correlation
        this.variables = covMatrix.getVariables();
        this.indexMap = indexMap(this.variables);
        this.nameMap = nameMap(this.variables);
        this.sampleSize = covMatrix.getSampleSize();
        setEffectiveSampleSize(-1);
        setAlpha(alpha);
    }

    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha, double ridge) {
        this(covMatrix, alpha);
        setRidge(ridge);
        setShrinkageMode(ShrinkageMode.RIDGE);
    }

    /* ======================= API ======================= */

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!this.variables.contains(var)) {
                throw new IllegalArgumentException("All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.indexMap.get(vars.get(i).getName());
        }

        ICovarianceMatrix newCovMatrix = this.cor.getSubmatrix(indices);

        IndTestFisherZ t = new IndTestFisherZ(newCovMatrix, getAlpha());
        t.setLambda(this.lambda);
        t.setRidge(this.ridge);
        t.setShrinkageMode(this.shrinkageMode);
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
            throw new RuntimeException("Undefined p-value encountered in for test: " + LogUtilsSearch.independenceFact(x, y, z));
        } else {
            IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);

            if (this.verbose) {
                if (independent) {
                    TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
                }
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
            r = getR(x, y, z, rows);
            n = rows.size();
        }

        this.r = r;
        double q = .5 * (log(1.0 + abs(r)) - log(1.0 - abs(r)));
        double df = n - 3. - z.size();

        if (df < 1) {
            throw new IllegalArgumentException("The degrees of freedom for independence fact " + x + " _||_ " + y +
                                               " | " + z + " nonpositive, n = " + n + " df = " + df);
        }

        double fisherZ = sqrt(df) * q;

        return 2 * (1.0 - this.normal.cumulativeProbability(fisherZ));
    }

    public int getEffectiveSampleSize() { return nEff; }

    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.nEff = effectiveSampleSize < 0 ? this.sampleSize : effectiveSampleSize;
    }

    public double getBic() {
        return -getEffectiveSampleSize() * FastMath.log(1.0 - this.r * this.r) - FastMath.log(getEffectiveSampleSize());
    }

    public double getAlpha() { return this.alpha; }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }
        this.alpha = alpha;
    }

    public List<Node> getVariables() { return this.variables; }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.cor.setVariables(variables);
    }

    public Node getVariable(String name) { return this.nameMap.get(name); }

    public DataSet getData() { return this.dataSet; }

    public ICovarianceMatrix getCov() { return this.cor; }

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

    public boolean isVerbose() { return this.verbose; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @Override
    public String toString() {
        DecimalFormat f1 = new DecimalFormat("0.0###");
        DecimalFormat f2 = new DecimalFormat("0.0#####");
        String base = "Fisher Z, alpha = " + f1.format(getAlpha());
        base += ", shrinkage=" + shrinkageMode;
        if (shrinkageMode == ShrinkageMode.RIDGE && ridge > 0.0) base += "(ridge=" + f2.format(ridge) + ")";
        if (shrinkageMode == ShrinkageMode.LEDOIT_WOLF && !Double.isNaN(lastLedoitWolfDelta))
            base += "(delta=" + f2.format(lastLedoitWolfDelta) + ")";
        return base;
    }

    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];
        for (int j = 0; j < parents.length; j++) {
            parents[j] = indexMap.get(z.get(j).getName());
        }

        if (parents.length > 0) {
            Matrix Czz = this.cor.getSelection(parents, parents);
            try {
                // No shrinkage here: we want actual singularity for determinism detection.
                Czz.inverse();
            } catch (SingularMatrixException e) {
                System.out.println(LogUtilsSearch.determinismDetected(new HashSet<>(z), x));
                return true;
            }
        }

        return false;
    }

    /* ======================= Core ======================= */

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
            corSub = MatrixUtils.convertCovToCorr(cov);
        }

        // Apply selected regularization on the correlation submatrix
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
                    double denom = 0.0;
                    double num = 0.0;
                    for (int i = 0; i < p; i++) {
                        double riiMinus1 = corSub.get(i, i) - 1.0;
                        denom += riiMinus1 * riiMinus1;
                        for (int j = i + 1; j < p; j++) {
                            double rij = corSub.get(i, j);
                            double diff = rij; // target off-diagonal = 0
                            denom += 2.0 * diff * diff; // count both (i,j) and (j,i)
                            // Gaussian approx Var(r_ij) ~ (1 - rho^2)^2 / (n - 1)
                            double var = (1.0 - rij * rij);
                            var = var * var / (n - 1.0);
                            num += 2.0 * var; // for symmetric pair
                        }
                    }
                    double delta = 0.0;
                    if (denom > 0.0) {
                        delta = Math.min(1.0, Math.max(0.0, num / denom));
                    }
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

        return StatUtils.partialCorrelationPrecisionMatrix(corSub, 0);
    }

    private double getR(Node x, Node y, Set<Node> z, List<Integer> rows) {
        return partialCorrelation(x, y, z, rows);
    }

    private ICovarianceMatrix covMatrix() { return this.cor; }

    /* ======================= Helpers ======================= */

    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<>();
        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }
        return nameMap;
    }

    private Map<String, Integer> indexMap(List<Node> variables) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i).getName(), i);
        }
        return indexMap;
    }

    private List<Integer> listRows() {
        if (this.rows != null) {
            return this.rows;
        }
        List<Integer> rows = new ArrayList<>();
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            rows.add(k);
        }
        return rows;
    }

    public List<Integer> getRows() { return rows; }

    public void setRows(List<Integer> rows) {
        if (dataSet == null) {
            return;
        }
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }
            this.rows = rows;
            cor = null;
        }
    }

    public double getRho() {
        return r;
    }

    /** Singularity lambda for the precision-based partial correlation utility. */
    public void setLambda(double lambda) { this.lambda = lambda; }

    /** Ridge setter/getter (used when shrinkageMode == RIDGE). */
    public void setRidge(double ridge) {
        if (ridge < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
    }
    public double getRidge() { return this.ridge; }

    /** Shrinkage mode setter/getter. */
    public void setShrinkageMode(ShrinkageMode mode) {
        if (mode == null) mode = ShrinkageMode.NONE;
        this.shrinkageMode = mode;
    }
    public ShrinkageMode getShrinkageMode() { return this.shrinkageMode; }

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
        test.setLambda(this.lambda);
        test.setRidge(this.ridge);
        test.setShrinkageMode(this.shrinkageMode);

        return test.getPValue(_x, _y, new HashSet<>());
    }
}