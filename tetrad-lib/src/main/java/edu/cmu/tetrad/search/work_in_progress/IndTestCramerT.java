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
import edu.cmu.tetrad.search.IndependenceTest;
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
 */
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
     * Creates a new IndTestCramerT instance for a subset of the variables.
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
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x  the one variable being compared.
     * @param y  the second variable being compared.
     * @param _z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
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
        Matrix submatrix =
                covMatrix().getMatrix().getSelection(indices, indices);

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
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, alpha - pValue);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return 2.0 * Integrator.getArea(pdf(), FastMath.abs(this.storedR), 1.0, 100);
    }

    /**
     * @return the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level for future tests.
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
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name, or null if there is no such variable.
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
                    matrix2D.getSelection(parents, parents);
            Matrix inverse;
            try {
                inverse = Czz.inverse();
//                inverse = MatrixUtils.ginverse(Czz);
            } catch (Exception e) {
                return true;
            }

            Vector Cyz = matrix2D.getColumn(i);
            Cyz = Cyz.viewSelection(parents);
            Vector b = inverse.times(Cyz);

            variance -= Cyz.dotProduct(b);
        }

        return variance < 0.01;
    }

    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * @return a string representation of this test.
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

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





