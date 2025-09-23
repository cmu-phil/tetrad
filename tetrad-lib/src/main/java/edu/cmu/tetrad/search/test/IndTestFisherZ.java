/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

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
 * Fisher's Z CI test with shrinkage (RIDGE/LedoitâWolf) and optional pseudoinverse fallback.
 */
public final class IndTestFisherZ implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable, RawMarginalIndependenceTest {

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
     * LedoitâWolf / Ridge / None.
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

    /**
     * Constructs an independence test using the Fisher Z test statistic.
     * The test evaluates the independence of variables given a dataset and a significance level (alpha).
     * The dataset must be continuous and should not contain missing values for certain operations.
     *
     * @param dataSet the dataset used for the independence test; must be continuous
     * @param alpha the significance level for the Fisher Z test; must be in the range [0, 1]
     * @throws IllegalArgumentException if the dataset is not continuous
     * @throws IllegalArgumentException if the alpha value is not in the range [0, 1]
     */
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

    /**
     * Constructor for IndTestFisherZ which initializes the independence test using
     * the Fisher Z test with specified parameters.
     *
     * @param dataSet the dataset on which the independence test will be performed
     * @param alpha the significance level for determining independence
     * @param ridge the ridge parameter used for regularization in the test
     */
    public IndTestFisherZ(DataSet dataSet, double alpha, double ridge) {
        this(dataSet, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    /**
     * Constructs an instance of the IndTestFisherZ class, which is a statistical
     * test for conditional independence based on the Fisher Z-test.
     *
     * @param data      The data matrix, where rows represent samples and columns
     *                  represent variables.
     * @param variables A list of variables corresponding to the columns in the data
     *                  matrix. The order must match the column order in the data matrix.
     * @param alpha     The significance level (type I error rate) for the Fisher Z-test.
     */
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

    /**
     * Constructor for the IndTestFisherZ class, which performs a Fisher Z independence test
     * with ridge regularization applied to handle issues with covariance matrix inversion.
     *
     * @param data The data matrix containing the dataset.
     * @param variables The list of nodes corresponding to the variables in the dataset.
     * @param alpha The significance level for the independence test.
     * @param ridge The ridge parameter for regularization of the covariance matrix.
     */
    public IndTestFisherZ(Matrix data, List<Node> variables, double alpha, double ridge) {
        this(data, variables, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    /**
     * Constructs an instance of IndTestFisherZ using the given covariance matrix and significance level.
     *
     * @param covMatrix the covariance matrix used to compute correlations and perform the Fisher's Z test
     * @param alpha the significance level for independence tests
     */
    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha) {
        this.cor = new CorrelationMatrix(covMatrix);
        this.variables = covMatrix.getVariables();
        this.indexMap = indexMap(this.variables);
        this.nameMap = nameMap(this.variables);
        this.sampleSize = covMatrix.getSampleSize();
        setEffectiveSampleSize(-1);
        setAlpha(alpha);
    }

    /**
     * Constructor for the IndTestFisherZ class. It initializes the test with a given
     * covariance matrix, significance level, and ridge parameter. This test determines
     * the conditional independence of variables using the Fisher Z test.
     *
     * @param covMatrix the covariance matrix used for calculating correlations.
     * @param alpha the significance level for testing conditional independence.
     * @param ridge the ridge parameter used for regularization in the shrinkage mode.
     */
    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha, double ridge) {
        this(covMatrix, alpha);
        setShrinkageMode(ShrinkageMode.RIDGE);
        setRidge(ridge);
    }

    /**
     * Compute partial corr between index 0 (x) and 1 (y) from precision Î©.
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

    /**
     * Returns a new IndependenceTest instance for a subset of variables.
     * This method verifies that the given variables are part of the original
     * variable set, then creates a submatrix of the covariance matrix and
     * constructs an IndependenceTestFisherZ object for the subset.
     *
     * @param vars A list of variables for which the subset independence test
     *             will be created. All variables in this list must be part of
     *             the original set of variables.
     * @return An IndependenceTest instance that operates on the given subset
     *         of variables.
     * @throws IllegalArgumentException If the provided subset is empty or
     *                                  contains variables not in the original
     *                                  variable set.
     */
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

    /**
     * Checks the independence of two nodes given a conditioning set and returns the result.
     *
     * @param x the first node to be tested for independence
     * @param y the second node to be tested for independence
     * @param z the set of nodes conditioning the independence test
     * @return an IndependenceResult containing the results of the independence test, including
     *         whether x and y are independent given z, the p-value of the test, and other test details
     * @throws RuntimeException if a singular matrix is encountered during computation
     *                          or if the p-value is undefined
     */
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

    /**
     * Calculates the p-value for the partial correlation between two nodes conditioned on a set of other nodes.
     *
     * @param x the first node involved in the correlation.
     * @param y the second node involved in the correlation.
     * @param z the set of conditioning nodes.
     * @return the p-value for the partial correlation between nodes x and y given the conditioning set z.
     * @throws SingularMatrixException if the covariance matrix inversion fails during computation.
     * @throws IllegalArgumentException if the degrees of freedom (df) are non-positive.
     */
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

    /**
     * Retrieves the effective sample size.
     *
     * @return the effective sample size as an integer.
     */
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size. If the provided effective sample size is negative,
     * it will default to the sample size.
     *
     * @param effectiveSampleSize the effective sample size to set;
     *                            if negative, the sample size will be used instead.
     */
    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.nEff = effectiveSampleSize < 0 ? this.sampleSize : effectiveSampleSize;
    }

    /**
     * Computes and returns the Bayesian Information Criterion (BIC) value.
     *
     * @return the BIC value as a double calculated based on the effective sample size
     *         and the correlation coefficient squared.
     */
    public double getBic() {
        return -getEffectiveSampleSize() * FastMath.log(1.0 - this.r * this.r) - FastMath.log(getEffectiveSampleSize());
    }

    /**
     * Retrieves the significance level.
     *
     * @return the significance level as a double.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     * Validates that the provided significance level is within the valid range (0.0 to 1.0).
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Significance out of range: " + alpha);
        this.alpha = alpha;
    }

    /**
     * Retrieves the list of variables.
     *
     * @return the list of variables.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the list of variables for the instance.
     * Validates that the size of the provided variable list matches the current size
     * of the instance's internal variable list, and updates internal state accordingly.
     *
     * @param variables the list of variables to set; must match the size of the current variable list
     * @throws IllegalArgumentException if the size of the provided variable list does not match the current variable list size
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.cor.setVariables(variables);
    }

    /**
     * Retrieves a variable by its name from the internal mapping of variable names to nodes.
     *
     * @param name the name of the variable to retrieve; must match an existing key in the name map
     * @return the node corresponding to the given name, or null if no such node exists
     */
    public Node getVariable(String name) {
        return this.nameMap.get(name);
    }

    /**
     * Retrieves the data set associated with the current instance.
     *
     * @return the DataSet object representing the data in this instance
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Retrieves the covariance matrix used by this instance.
     *
     * @return an object implementing the ICovarianceMatrix interface,
     * representing the covariance matrix associated with this instance
     */
    public ICovarianceMatrix getCov() {
        return this.cor;
    }

    /**
     * Retrieves a list of data sets associated with this instance.
     *
     * @return a list containing the data set associated with this instance
     */
    @Override
    public List<DataSet> getDataSets() {
        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(this.dataSet);
        return dataSets;
    }

    /**
     * Retrieves the sample size of the data set associated with this instance.
     *
     * @return the number of rows in the data set, or the sample size of the covariance matrix if no data set is available
     */
    @Override
    public int getSampleSize() {
        if (dataSet != null) return dataSet.getNumRows();
        else return this.cor.getSampleSize();
    }

    /**
     * Retrieves the verbosity setting for this instance.
     *
     * @return true if verbose output is enabled, false otherwise
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbosity setting for this instance.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of this instance, including details about the test configuration.
     *
     * @return a string representation of this instance
     */
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
     * Determines whether the given set of nodes, z, has a deterministic relationship with the specified node, x.
     * Specifically, this method checks if the covariance matrix derived from z is invertible.
     * If the matrix is singular (non-invertible), it indicates a determinism detected between the nodes.
     *
     * @param z the list of nodes to analyze as a potential set of deterministic parents for the node x
     * @param x the node to check for a deterministic relationship with the set of nodes z
     * @return true if a deterministic relationship exists (when the covariance matrix is singular), false otherwise
     * @throws UnsupportedOperationException if the operation is not supported due to some internal state or configuration
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

    /**
     * Retrieves the list of row indices currently associated with this instance.
     *
     * @return a list of integers representing the row indices
     */
    public List<Integer> getRows() {
        return rows;
    }

    /* ======================= Helpers & setters ======================= */

    /**
     * Sets the list of row indices for the instance. Validates the provided list to ensure
     * all elements are non-negative and non-null. Resets internal correlation state
     * if the rows are updated.
     *
     * @param rows the list of row indices to set. Each element must be non-null and non-negative.
     *             If the provided list is null, the current row list is set to null.
     * @throws NullPointerException if any element in the rows list is null.
     * @throws IllegalArgumentException if any element in the rows list is negative.
     */
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
     * Sets the value of the lambda parameter for this instance. Lambda is used
     * for regularization or other purposes within the class, depending on the
     * context of its implementation.
     *
     * @param lambda the value to set for the lambda parameter
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    /**
     * Retrieves the current value of the ridge parameter associated with this instance.
     *
     * @return the ridge parameter as a double value
     */
    public double getRidge() {
        return this.ridge;
    }

    /**
     * Sets the value of the ridge parameter for this instance.
     * The ridge parameter is commonly used for regularization purposes.
     * The provided value must be non-negative.
     *
     * @param ridge the value to set for the ridge parameter; must be greater than or equal to 0
     * @throws IllegalArgumentException if the ridge parameter is negative
     */
    public void setRidge(double ridge) {
        if (ridge < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
    }

    /**
     * Retrieves the current shrinkage mode used by this instance.
     *
     * @return the shrinkage mode, represented as a {@code ShrinkageMode} enum value, which indicates the type of
     *         regularization or adjustment (if any) applied in computations.
     */
    public ShrinkageMode getShrinkageMode() {
        return this.shrinkageMode;
    }

    /**
     * Sets the shrinkage mode for the instance. The shrinkage mode determines the type of
     * regularization or adjustment applied during calculations, if any. If the provided mode
     * is null, the shrinkage mode defaults to {@code ShrinkageMode.NONE}.
     *
     * @param mode the shrinkage mode to set, represented as a {@code ShrinkageMode} enum value.
     *             It can be {@code ShrinkageMode.NONE}, {@code ShrinkageMode.RIDGE}, or
     *             {@code ShrinkageMode.LEDOIT_WOLF}.
     */
    public void setShrinkageMode(ShrinkageMode mode) {
        if (mode == null) mode = ShrinkageMode.NONE;
        this.shrinkageMode = mode;
    }

    /**
     * Checks whether the pseudoinverse is being used in computations.
     *
     * @return true if the pseudoinverse is enabled, false otherwise
     */
    public boolean isUsePseudoinverse() {
        return this.usePseudoinverse;
    }

    /**
     * Sets whether to use the pseudoinverse in computations.
     *
     * @param use true to enable pseudoinverse, false to disable
     */
    public void setUsePseudoinverse(boolean use) {
        this.usePseudoinverse = use;
    }

    /**
     * Gets the tolerance for the pseudoinverse computation.
     *
     * @return the tolerance value
     */
    public double getPinvTolerance() {
        return this.pinvTolerance;
    }

    /**
     * Sets the tolerance for the pseudoinverse computation.
     *
     * @param tol the tolerance value
     */
    public void setPinvTolerance(double tol) {
        if (tol <= 0) throw new IllegalArgumentException("pinvTolerance must be > 0");
        this.pinvTolerance = tol;
    }

    /**
     * Gets the last computed partial correlation.
     *
     * @return the last computed partial correlation
     */
    public double getLastR() {
        return this.r;
    }

    /**
     * Gets the last computed partial correlation.
     *
     * @return the last computed partial correlation
     */
    public double getRho() {
        return r;
    }

    /**
     * Computes the p-value for the statistical test of independence between two variables.
     *
     * @param x the array of values representing the first variable
     * @param y the array of values representing the second variable
     * @return the computed p-value indicating the strength of independence between the two variables
     */
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
    public enum ShrinkageMode {
        /**
         * Represents the absence of any shrinkage mode. This value indicates that no shrinkage adjustment is applied in
         * the context of the enum it belongs to.
         */
        NONE,
        /**
         * Represents the Ridge shrinkage mode. Typically utilized in computational scenarios such as statistical
         * regression or covariance estimation where regularization is applied to stabilize computations and handle
         * ill-conditioned problems. This mode introduces a penalty proportional to the square of the magnitude of
         * coefficients, aiding in reducing overfitting.
         */
        RIDGE,
        /**
         * Represents the Ledoit-Wolf shrinkage mode. This mode is commonly used in statistical covariance estimation to
         * improve the stability of covariance matrix calculations by applying shrinkage. The Ledoit-Wolf method
         * adaptively determines the optimal shrinkage intensity to balance bias and variance, making it particularly
         * useful for high-dimensional data or scenarios with limited sample sizes.
         */
        LEDOIT_WOLF
    }

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
