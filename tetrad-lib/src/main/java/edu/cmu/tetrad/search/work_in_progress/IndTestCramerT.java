///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks conditional independence for continuous variables using Cramer's T-test formula (Cramer, Mathematical Methods
 * of Statistics (1951), page 413).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Deprecated(since = "7.9", forRemoval = false)
public final class IndTestCramerT implements IndependenceTest {

    /**
     * Formats as 0.0000.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The correlation matrix.
     */
    private final ICovarianceMatrix covMatrix;

    /**
     * The variables of the correlation matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;
    /**
     * The data set over which conditional independence judgements are being formed.
     */
    private DataSet dataSet;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * The last used partial correlation distribution function.
     */
    private PartialCorrelationPdf pdf;
    /**
     * The cutoff value for 'alpha' area in the two tails of the partial correlation distribution function.
     */
    private double cutoff;
    /**
     * The last calculated partial correlation, needed to calculate relative strength.
     */
    private double storedR;
    private boolean verbose;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set with all continuous columns.
     * @param alpha   the alpha level of the test.
     */
    public IndTestCramerT(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        this.dataSet = dataSet;
        this.covMatrix = new CorrelationMatrix(dataSet);
        this.variables =
                Collections.unmodifiableList(this.covMatrix.getVariables());
        setAlpha(alpha);
    }

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.CorrelationMatrix} object
     * @param alpha     a double
     */
    public IndTestCramerT(CorrelationMatrix covMatrix, double alpha) {
        this.covMatrix = covMatrix;
        this.variables =
                Collections.unmodifiableList(covMatrix.getVariables());
        setAlpha(alpha);
    }


    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param alpha     a double
     */
    public IndTestCramerT(ICovarianceMatrix covMatrix, double alpha) {
        CorrelationMatrix corrMatrix = new CorrelationMatrix(covMatrix);
        this.variables =
                Collections.unmodifiableList(corrMatrix.getVariables());
        this.covMatrix = corrMatrix;
        setAlpha(alpha);
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * This method performs an independence test based on a given sublist of variables.
     *
     * @param vars The sublist of variables to perform the independence test on.
     * @return An IndependenceTest object representing the results of the test.
     * @throws IllegalArgumentException If the sublist of variables is empty or contains variables that are not original
     *                                  variables.
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
            indices[i] = this.variables.indexOf(vars.get(i));
        }

        ICovarianceMatrix newCorrMatrix = this.covMatrix.getSubmatrix(indices);

        double alphaNew = getAlpha();
        return new IndTestCramerT(newCorrMatrix, alphaNew);
    }

    /**
     * Checks the independence between two nodes given a set of conditioning nodes.
     *
     * @param x  The first node.
     * @param y  The second node.
     * @param _z The set of conditioning nodes.
     * @return The result of the independence check.
     * @throws NullPointerException     If _z is null or contains null elements.
     * @throws IllegalArgumentException If the submatrix contains missing values.
     * @throws RuntimeException         If the submatrix is singular or the p-value is undefined.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (_z == null) {
            throw new NullPointerException();
        }

        for (Node node : _z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> z = new ArrayList<>();
        Collections.sort(z);

        // Precondition: this.variables, this.corrMatrix properly set up.
        //
        // Postcondition: this.storedR and this.func should be the
        // most recently calculated partial correlation and
        // partial correlation distribution function, respectively.
        //
        // PROCEDURE:
        // calculate the partial correlation of x and y given z
        // by finding the submatrix of variables x, y, z1...zn,
        // inverting it, and examining the value at position (0, 1)
        // in the inverted matrix.  the partial correlation is
        // -1 * this value / the square root of the outerProduct of the
        // diagonal elements on the same row and column as this
        // value.
        //
        // Design consideration:
        // Minimize object creation by reusing submatrix and condition
        // arrays and inverting submatrix in place.

        // Create index array for the given variables.
        int size = z.size() + 2;
        int[] indices = new int[size];

        indices[0] = getVariables().indexOf(x);
        indices[1] = getVariables().indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = getVariables().indexOf(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.
        Matrix matrix = covMatrix().getMatrix();
        Matrix submatrix =
                matrix.view(indices, indices).mat();

        // Check for missing values.
        if (DataUtils.containsMissingValue(submatrix)) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        try {
            submatrix = submatrix.inverse();
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, _z));
        }

        double a = -1.0 * submatrix.get(0, 1);
        double b = FastMath.sqrt(submatrix.get(0, 0) * submatrix.get(1, 1));

        this.storedR = a / b; // Store R so P value can be calculated.

        if (FastMath.abs(this.storedR) > 1) {
            this.storedR = FastMath.signum(this.storedR);
        }

        if (Double.isNaN(this.storedR)) {
            throw new IllegalArgumentException("Conditional correlation cannot be computed: " + LogUtilsSearch.independenceFact(x, y, _z));
        }

        // Determine whether this partial correlation is statistically
        // nondifferent from zero.
        boolean independent = isZero(this.storedR, size, getAlpha());
        double pValue = getPValue();

        if (Double.isNaN(pValue)) {
            throw new RuntimeException("Undefined p-value encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
        }

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, alpha - pValue);
    }

    /**
     * Calculates the p-value for the independence test. The p-value is calculated by integrating the probability
     * density function (pdf) over the range of storedR (absolute value) to 1.0 with 100 intervals, and then multiplying
     * the result by 2.0.
     *
     * @return the p-value for the independence test.
     */
    public double getPValue() {
        return 2.0 * Integrator.getArea(pdf(), FastMath.abs(this.storedR), 1.0, 100);
    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level, must be between 0.0 and 1.0 (inclusive).
     * @throws IllegalArgumentException If the significance level is out of range.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    private ICovarianceMatrix covMatrix() {
        return this.covMatrix;
    }

    /**
     * Retrieves the list of variables used in the independence test.
     *
     * @return A list of Node objects representing the variables used in the test.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines whether the given variables are conditionally independent.
     *
     * @param z The set of conditioning nodes.
     * @param x The target node.
     * @return true if the variables are conditionally independent, false otherwise.
     * @throws UnsupportedOperationException If a matrix operation fails.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = this.covMatrix.getVariables().indexOf(z.get(j));
        }

        int i = this.covMatrix.getVariables().indexOf(x);

        Matrix matrix2D = this.covMatrix.getMatrix();
        double variance = matrix2D.get(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            Matrix Czz =
                    matrix2D.view(parents, parents).mat();
            Matrix inverse;
            try {
                inverse = Czz.inverse();
//                inverse = MatrixUtils.ginverse(Czz);
            } catch (Exception e) {
                return true;
            }

            Vector Cyz = matrix2D.getColumn(i);
            Cyz = Cyz.getSelection(parents);
            Vector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 0.01;
    }

    /**
     * Retrieves the dataset used in the independence test.
     *
     * @return The dataset used in the independence test.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representation of the object.
     */
    public String toString() {
        return "Partial Correlation T Test, alpha = " + IndTestCramerT.nf.format(getAlpha());
    }

    //==========================PRIVATE METHODS============================//

    /**
     * Tests whether the given correlation is statistically non-different from zero by determining whether |storedR| <=
     * the cutoff calculated by placing an area equal to the significance level in the two tails of the relevant partial
     * correlation distribution function.
     *
     * @param r     the sample correlation.
     * @param k     the number of compared variables.
     * @param alpha the alpha level.
     * @return true if the sample correlation is statically non-different from zero, false if not.
     */
    private boolean isZero(double r, int k, double alpha) {
        if (pdf() == null || pdf().getK() != k) {
            this.cutoff = cutoff(k, alpha);
        }
        return FastMath.abs(r) <= this.cutoff;
    }

    private double cutoff(int k, double alpha) {
        this.pdf = new PartialCorrelationPdf(sampleSize() - 1, k);
        final double upperBound = 1.0;
        final double delta = 0.00001;
        return CutoffFinder.getCutoff(pdf(), upperBound, alpha, delta);
    }

    private int sampleSize() {
        return covMatrix().getSampleSize();
    }

    private PartialCorrelationPdf pdf() {
        return this.pdf;
    }

    /**
     * Determines if verbose output is enabled or disabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose flag to determine if verbose output should be enabled or disabled.
     *
     * @param verbose True if the verbose output should be enabled, false otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





