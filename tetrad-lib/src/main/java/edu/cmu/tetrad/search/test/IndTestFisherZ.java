/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Vector;
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
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author josephramsey
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public final class IndTestFisherZ implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable {
    /**
     * A hash from variable names to indices.
     */
    private final Map<String, Integer> indexMap;
    /**
     * A hash from variable names to variables.
     */
    private final Map<String, Node> nameMap;
    /**
     * The standard normal distribution.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The sample size to use; if not set, the sample size of the data set is used.
     */
    private int sampleSize;
    /**
     * The correlation matrix.
     */
    private ICovarianceMatrix cor = null;
    /**
     * The variables of the covariance data, in order. (Unmodifiable list.)
     */
    private List<Node> variables;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * Stores a reference to the data set passed in through the constructor.
     */
    private DataSet dataSet;
    /**
     * Matrix from of the data.a
     */
    private Matrix data;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * The correlation coefficient for the last test.
     */
    private double r = Double.NaN;
    /**
     * The rows used in the test.
     */
    private List<Integer> rows = null;
    /**
     * Lambda for regularization.
     */
    private double lambda = 0.0;


    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestFisherZ(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.sampleSize = dataSet.getNumRows();

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

    /**
     * Constructs a new Fisher Z independence test with the listed arguments.
     *
     * @param data      A 2D continuous data set with no missing values.
     * @param variables A list of variables, a subset of the variables of <code>data</code>.
     * @param alpha     The alpha level of the test.
     */
    public IndTestFisherZ(Matrix data, List<Node> variables, double alpha) {
        this.dataSet = new BoxDataSet(new VerticalDoubleDataBox(data.transpose().toArray()), variables);
        this.cor = SimpleDataLoader.getCorrelationMatrix(this.dataSet);
        this.variables = Collections.unmodifiableList(variables);
        this.indexMap = indexMap(variables);
        this.nameMap = nameMap(variables);
        this.sampleSize = data.getNumRows();
        setAlpha(alpha);
    }

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     *
     * @param covMatrix The covariance matrix.
     * @param alpha     The alpha level of the test.
     */
    public IndTestFisherZ(ICovarianceMatrix covMatrix, double alpha) {
        this.cor = new CorrelationMatrix(covMatrix);
        this.variables = covMatrix.getVariables();
        this.indexMap = indexMap(this.variables);
        this.nameMap = nameMap(this.variables);
        this.sampleSize = covMatrix.getSampleSize();
        setAlpha(alpha);
    }

    /**
     * Creates a new independence test instance for a subset of the variables.
     *
     * @see IndependenceTest
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!this.variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = this.indexMap.get(vars.get(i).getName());
        }

        ICovarianceMatrix newCovMatrix = this.cor.getSubmatrix(indices);

        double alphaNew = getAlpha();
        return new IndTestFisherZ(newCovMatrix, alphaNew);
    }

    /**
     * Determines whether variable x _||_ y | z given a list of conditioning variables z.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        IndependenceResult _result = facts.get(new IndependenceFact(x, y, z));

        if (_result != null) {
            return _result;
        }


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
            facts.put(new IndependenceFact(x, y, z), result);

            if (this.verbose) {
                if (independent) {
                    TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
                }
            }

            return result;
        }
    }

    /**
     * Returns the p-value for x _||_ y | z.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of conditioning variables.
     * @return The p-value.
     * @throws SingularMatrixException If a singularity occurs when invering a matrix.
     */
    public double getPValue(Node x, Node y, Set<Node> z) throws SingularMatrixException {
        double r;
        int n;

        if (covMatrix() != null) {
            r = partialCorrelation(x, y, z, rows, lambda);
            n = sampleSize();
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
                                               " | " + z + " nonpositive.");
        }

        double fisherZ = sqrt(df) * q;

        return 2 * (1.0 - this.normal.cumulativeProbability(fisherZ));
    }

    /**
     * Returns the BIC score for this test.
     *
     * @return The BIC score.
     */
    public double getBic() {
        return -sampleSize() * FastMath.log(1.0 - this.r * this.r) - FastMath.log(sampleSize());
    }

    /**
     * Gets the model significance level.
     *
     * @return This alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the variables to a new list of the same size. Useful if multiple independence tests are needed with
     * object-identical sets of variables.
     *
     * @param variables The new list of variables.
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.cor.setVariables(variables);
    }

    /**
     * Returns the variable with the given name.
     */
    public Node getVariable(String name) {
        return this.nameMap.get(name);
    }

    /**
     * Returns the data set being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns the correlation matrix being analyzed.
     *
     * @return This correlation matrix.
     */
    public ICovarianceMatrix getCov() {
        return this.cor;
    }

    /**
     * Returns the (singleton) list of datasets being analyzed.
     */
    @Override
    public List<DataSet> getDataSets() {
        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(this.dataSet);
        return dataSets;
    }

    /**
     * Returns the sample size.
     */
    @Override
    public int getSampleSize() {
        if (dataSet != null) return dataSet.getNumRows();
        else return this.cor.getSampleSize();
    }

    /**
     * Sets the sample size to use for the independence test, which may be different from the sample size of the data
     * set or covariance matrix. If not set, the sample size of the data set or covariance matrix is used.
     *
     * @param effectiveSampleSize The sample size to use.
     */
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        if (effectiveSampleSize < 1) {
            throw new IllegalArgumentException("Sample size must be positive.");
        }

        this.sampleSize = effectiveSampleSize;
    }

    /**
     * Returns true iff verbose output should be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of the Fisher Z independence test. The string includes the value of alpha.
     *
     * @return A string representing the Fisher Z independence test.
     */
    public String toString() {
        return "Fisher Z, alpha = " + new DecimalFormat("0.0###").format(getAlpha());
    }

    /**
     * Determines if a given Node x is determined by a list of Nodes z.
     *
     * @param z the list of Nodes
     * @param x the Node to check if it is determined
     * @return true if x is determined by z, false otherwise
     * @throws UnsupportedOperationException if the operation is not supported
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = indexMap.get(z.get(j).getName());
        }

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            Matrix Czz = this.cor.getSelection(parents, parents);

            try {

                // Don't do regularization here; we're trying to test determination.
                Czz.inverse();
            } catch (SingularMatrixException e) {
                System.out.println(LogUtilsSearch.determinismDetected(new HashSet<>(z), x));
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true just in case the varialbe in zList determine xVar.
     *
     * @return True, if so.
     */
    private boolean determinesPseudoinverse(List<Node> zList, Node xVar) {
        if (zList == null) {
            throw new NullPointerException();
        }

        if (zList.isEmpty()) {
            return false;
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int size = zList.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);
        Vector x = this.data.getColumn(xIndex);

        for (int i = 0; i < zList.size(); i++) {
            zCols[i] = getVariables().indexOf(zList.get(i));
        }

        CovarianceMatrix cov = new CovarianceMatrix(dataSet);

        int[] rows;

        if (this.rows == null) {
            rows = new int[this.data.getNumRows()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = i;
            }
        } else {
            rows = new int[this.rows.size()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = this.rows.get(i);
            }
        }

        SemBicScore.CovAndCoefs covAndCoefsX = SemBicScore.getCovAndCoefs(xIndex, zCols, this.data,
                cov, true, lambda);

        Matrix selection = data.view(rows, zCols).mat();
        Vector xPred = selection.times(covAndCoefsX.b()).getColumn(0);
        Vector xRes = xPred.minus(x);

        double SSE = 0;

        for (int i = 0; i < xRes.size(); i++) {
            SSE += xRes.get(i) * xRes.get(i);
        }

        double variance = SSE / (this.data.getNumRows() - (zList.size() + 1));

        boolean determined = variance < getAlpha();

        if (determined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(xVar).append(
                    " is determined by {");

            for (int i = 0; i < zList.size(); i++) {
                sb.append(zList.get(i));

                if (i < zList.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

            sb.append(" SSE = ").append(NumberFormatUtil.getInstance().getNumberFormat().format(SSE));

            if (verbose) {
                TetradLogger.getInstance().log(sb.toString());
            }
        }

        return determined;
    }

    /**
     * Calculates the partial correlation between two nodes, given a set of conditioning variables and a list of rows.
     * If the correlation matrix is already available, it selects the necessary subset. Otherwise, it calculates the
     * covariance matrix from the provided rows and converts it to a correlation matrix.
     *
     * @param x      The first node.
     * @param y      The second node.
     * @param _z     The set of conditioning variables.
     * @param rows   The list of rows to use for calculating the covariance matrix, if necessary.
     * @param lambda Singularity lambda.
     * @return The partial correlation value.
     * @throws SingularMatrixException If a singularity occurs when inverting a matrix.
     */
    private double partialCorrelation(Node x, Node y, Set<Node> _z, List<Integer> rows, double lambda) throws SingularMatrixException {
        List<Node> z = new ArrayList<>(_z);

        int[] indices = new int[z.size() + 2];
        indices[0] = this.indexMap.get(x.getName());
        indices[1] = this.indexMap.get(y.getName());
        for (int i = 0; i < z.size(); i++) indices[i + 2] = this.indexMap.get(z.get(i).getName());

        Matrix cor;

        if (this.cor != null) {
            cor = this.cor.getSelection(indices, indices);
        } else {
            Matrix cov = SemBicScore.getCov(rows, indices, indices, this.dataSet, null);
            cor = MatrixUtils.convertCovToCorr(cov);
        }

        return StatUtils.partialCorrelationPrecisionMatrix(cor, this.lambda);
    }

    /**
     * Returns the partial correlation value between two nodes, given a set of conditioning variables and a list of
     * rows.
     *
     * @param x    The first node.
     * @param y    The second node.
     * @param z    The set of conditioning variables.
     * @param rows The list of rows to use for calculating the covariance matrix, if necessary.
     * @return The partial correlation value.
     * @throws SingularMatrixException If a singularity occurs when inverting a matrix.
     */
    private double getR(Node x, Node y, Set<Node> z, List<Integer> rows) {
        return partialCorrelation(x, y, z, rows, lambda);
    }

    /**
     * Returns the sample size. If the dataSet is not null, it returns the number of rows in the dataSet. Otherwise, it
     * returns the sample size from the covariance matrix.
     *
     * @return The sample size.
     */
    private int sampleSize() {
        return this.sampleSize;
    }

    /**
     * Returns the covariance matrix being analyzed.
     *
     * @return The covariance matrix.
     */
    private ICovarianceMatrix covMatrix() {
        return this.cor;
    }

    /**
     * Returns a mapping of variable names to Node objects.
     *
     * @param variables A list of Node objects representing variables.
     * @return A map containing variable names as keys and Node objects as values.
     */
    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    /**
     * Returns a mapping of variable names to their indices in the given list of Node objects.
     *
     * @param variables The list of Node objects representing variables.
     * @return A map containing variable names as keys and their indices as values.
     */
    private Map<String, Integer> indexMap(List<Node> variables) {
        Map<String, Integer> indexMap = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i).getName(), i);
        }

        return indexMap;
    }

    /**
     * Retrieves the rows from the dataSet that contain valid values for all variables.
     *
     * @return a list of row indices that contain valid values for all variables
     */
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

    /**
     * Returns the rows used in the test.
     *
     * @return The rows used in the test.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test. Otherwise, all rows are used, except those with missing
     * values.
     */
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

    /**
     * Sets the Singularity lambda.
     *
     * @param lambda Singularity lambda.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }
}




