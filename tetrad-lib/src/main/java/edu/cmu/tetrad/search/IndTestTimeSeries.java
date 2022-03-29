///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.IndexedMatrix;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.ProbUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>Checks independence facts for time series data. The method is described in Alessio Moneta, "Graphical Models for
 * Structural Vector Autoregressions."</p>
 *
 * @author Joseph Ramsey
 * @deprecated
 */
public final class IndTestTimeSeries implements IndependenceTest {

    /**
     * The vars of the correlation matrix, in order.
     */
    private final List<Node> vars;

    /**
     * Input time series data, n times x k vars.
     */
    private final Matrix data;

    /**
     * The number of time points for which data is available.
     */
    private final int numTimeSteps;

    /**
     * The number of vars.
     */
    private final int numVars;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The number of indices k for which the k-th row of the data is regressed onto rows k - 1, k - 2, ..., k -
     * numLags().
     */
    private int numReps;

    /**
     * <p>One plus the the number of previous rows in the data that each examined row is regressed onto.</p>
     */
    private int numLags;

    /**
     * Stored Sigma_u; this only needs to be calculated once but gets reused a lot.
     */
    private transient double[][] sigmaU;

    /**
     * Stored omega; this only needs to be calculated once.
     */
    private transient double[][] omega;

    /**
     * An indexed version of sigmaU; used repeatedly to calculate basic tau gradients.
     */
    private transient IndexedMatrix indexedCorr;

    /**
     * True if the stationary algorithm is to be used; false if the non- stationary algorithm is to be used.
     *
     * @serial
     */
    private boolean stationary;

    /**
     * @serial
     */
    private double chiSquare;
    private boolean verbose;

    //=============================CONSRUCTORS============================//

    /**
     * Constructs a new independence test based on Moneta, "Graphical Models for Structural Vector Autoregressions."
     *
     * @param data Row major matrix of data for each of the variables in vars for each time step. Dimensions
     *             numTimeSteps x numVars. Time steps are assumed to be in increasing order, so that time(data[i][]) <
     *             time(data[j][]) for i < j.
     * @param vars The variables over which the data is (repeatedly) measured. The number of variables must equal the
     *             number of columns in the data-- that is, vars.size() == data[i].length for each i.
     */
    public IndTestTimeSeries(Matrix data, List<Node> vars) {
        if (data == null) {
            throw new NullPointerException("Data must not be null.");
        }

        if (vars == null) {
            throw new NullPointerException(
                    "Variables must not be a null list.");
        }
        for (int i = 0; i < vars.size(); i++) {
            if (vars.get(i) == null) {
                throw new NullPointerException(
                        "Variable at index " + i + " must not be null.");
            }
        }

        this.data = data;
        numTimeSteps = this.data.rows();
        numVars = this.data.columns();
        this.vars = Collections.unmodifiableList(vars);
        setNumLags(1);
        setAlpha(0.05);
    }

    //===========================PUBLIC METHODS============================//

    /**
     * Required by IndependenceTest.
     */
    public IndependenceTest indTestSubset(List vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable data given a list of up to four conditioning vars z;
     * beyond four conditioning variables, false is always returned.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning vars.
     * @return true iff x _||_ data | z.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int[] indices = this.createIndexArray(z, x, y);
        return this.isIndependent(indices);
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return this.isIndependent(x, y, zList);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !this.isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return this.isDependent(x, y, zList);
    }

    /**
     * @return true iff according to the test var(indices[0]) _||_ var(indices[1] | var(indices[2], ...,
     * var(indices[indices.length - 1]).
     */
    public boolean isIndependent(int[] indices) {

        // Tests whether the given index array is legal.
        this.setIndices(indices);

        // We only test independence up to 4 conditioning vars.
        if (indices.length > 6) {
            return false;
        }

        //        int i = 1;
        //        testPrint("xPrime" + i, xPrime(i));

        //        System.out.println("---->" + getNumReps());

        // Calculate chi square value.
        double temp = Math.pow(this.tau(), 2.0);
        double numerator = this.getNumReps() * temp;

        //        System.out.println("Numerator = " + numerator);
        double[][] gradTau = this.gradTau();
        double[][] gradTauPrime = MatrixUtils.transpose(gradTau);
        double[][] prod1 = MatrixUtils.product(gradTauPrime, this.omega());
        double[][] prod2 = MatrixUtils.product(prod1, gradTau);
        assert (MatrixUtils.hasDimensions(prod2, 1, 1));
        double denominator = prod2[0][0];

        System.out.println("ratio w/o T = " + temp / denominator);

        //        System.out.println("Denominator = " + denominator);
        double chiSquare = numerator / denominator;

        this.chiSquare = chiSquare;

        //        System.out.println("chi square = " + chiSquare);

        // Compare chi square value to cutoff.
        double pValue = 1.0 - ProbUtils.chisqCdf(chiSquare, 1);
        return pValue > alpha;
    }
//
//    private void testPrint(String message, double[][] arr) {
//        System.out.println(message);
//        System.out.println(MatrixUtils.toString(arr,
//                new DecimalFormat("0.00E00")));
//    }
//
//    private void testPrint(String message, double[] arr) {
//        System.out.println(message);
//        System.out.println(
//                ArrUtils.toString(arr, new DecimalFormat("0.00E00")));
//    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = this.getVariables();
        List<String> variableNames = new ArrayList<>();

        for (Node variable : variables) {
            variableNames.add(variable.getName());
        }

        return variableNames;
    }

    public boolean determines(List<Node> z, Node x1) {
        throw new UnsupportedOperationException(
                "This independence test does not " +
                        "test whether Z determines X for list Z of variable and variable X.");
    }

    public double getAlpha() {
        return alpha;
    }

    public Node getVariable(String name) {
        for (int i = 0; i < this.getVariables().size(); i++) {
            Node variable = this.getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * @return the (unmodifiable) list of vars.
     */
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Sets the significance level for statistical tests. By default, this is 0.05.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException(
                    "Alpha must be in [0.0, 1.0]: " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * The number of indices k for which the k-th row of the data is regressed onto rows k - 1, k - 2, ..., k -
     * numLags().
     */
    public int getNumReps() {
        return numReps;
    }

    /**
     * @return the number of lags.
     */
    public int getNumLags() {
        return numLags;
    }

    /**
     * Sets the number of lags; the number of reps is 1 - numLags.
     */
    public void setNumLags(int numLags) {
        if (numLags < 1 || numLags > this.getNumTimeSteps() - 1) {
            throw new IllegalArgumentException("numLags must be in [1, " +
                    "numTimePoints - 1]: " + numLags);
        }
        this.numLags = numLags;
        numReps = this.getNumTimeSteps() - numLags;
        this.reset();
    }

    /**
     * Sets the number of lags. Note that the number of lags plus the lag size must be <= the number of times.
     */
    public void setDataView(int numReps, int numLags) {
        if (numLags < 1) {
            throw new IllegalArgumentException("numLags must be > 0.");
        }
        if (numLags + numReps > this.getNumTimeSteps()) {
            throw new IllegalArgumentException(
                    "NumLags + numReps must be " + "<= numTimeSteps.");
        }
        this.numLags = numLags;
        this.numReps = numReps;
        this.reset();
    }

    /**
     * @return the number of time steps in the data.
     */
    public int getNumTimeSteps() {
        return numTimeSteps;
    }

    /**
     * True if the stationary algorithm is to be used; false if the non- stationary algorithm is to be used.
     */
    public boolean isStationary() {
        return stationary;
    }

    /**
     * True if the stationary algorithm is to be used; false if the non- stationary algorithm is to be used.
     */
    public void setStationary(boolean stationary) {
        this.stationary = stationary;
    }

    /**
     * Needed for the IndependenceTest interface.  Probably not meaningful here.
     */
    public double getPValue() {
        return Double.NaN;
    }

    //==========================PRIVATE METHODS============================//

    private void reset() {
        sigmaU = null;
        omega = null;
        indexedCorr = null;
    }

    private IndexedMatrix indexedCorr() {
        if (indexedCorr == null) {
            indexedCorr = new IndexedMatrix(this.sigmaU());
        }
        return indexedCorr;
    }

    private void setIndices(int[] indices) {

        // The indices are stored in the IndexedMatrix. (No need to duplicate.)
        this.indexedCorr().setIndices(indices);
    }

    private int[] getIndices() {

        // The indices are stored in the IndexedMatrix. (No need to duplicate.)
        return this.indexedCorr().getIndices();
    }

    private int[] createIndexArray(List<Node> z, Node x, Node y) {
        int[] indices = new int[z.size() + 2];

        indices[0] = this.getVariables().indexOf(x);
        indices[1] = this.getVariables().indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = this.getVariables().indexOf(z.get(i));
        }

        for (int index : indices) {
            if (index < 0) {
                throw new IllegalArgumentException("Some variable was no in " +
                        "the constructed list of vars.");
            }
        }

        return indices;
    }

    /**
     * @param tIndex an int in the range [numReps - numTimeSteps + 1, numReps].
     * @return the row of the data indexed so that the last row has index numReps and the first row has index numReps -
     * numTimeSteps + 1.
     */
    private double[][] yPrime(int tIndex) {

//        double[][] yPrime = new double[1][numVars];
        int transformedIndex = this.getNumTimeSteps() - this.getNumReps() + tIndex - 1;
        //        System.out.println("tIndex = " + tIndex + ", transformed index = " + transformedIndex);

        return data.getPart(transformedIndex, 0, 1, data.columns()).toArray();

//        System.arraycopy(data[transformedIndex], 0, yPrime[0], 0, numVars);
//        return yPrime;
    }

    /**
     * Constructs the x(t) vector.
     */
    private double[][] xPrime(int t) {
        double[][] x = new double[1][this.getNumLags() * numVars];

        for (int i = 0; i < this.getNumLags(); i++) {
            double[][] yPrime = this.yPrime(t - i - 1);
            System.arraycopy(yPrime[0], 0, x[0], i * numVars, numVars);
        }

        return x;
    }

    private double[][] piPrime() {
        double[][] ma = MatrixUtils.zeros(numVars, numVars * numLags);
        for (int t = 1; t <= this.getNumReps(); t++) {
            double[][] summand = MatrixUtils.product(this.y(t), this.xPrime(t));
            ma = MatrixUtils.sum(ma, summand);
        }
        double[][] mb = MatrixUtils.zeros(numVars * numLags, numVars * numLags);
        for (int t = 1; t <= this.getNumReps(); t++) {
            double[][] summand = MatrixUtils.product(this.x(t), this.xPrime(t));
            mb = MatrixUtils.sum(mb, summand);
        }

        double[][] mbinv = MatrixUtils.inverse(mb);
        //        double[][] a = MatrixUtils.outerProduct(mb, mbinv);
        //        testPrint("a", a);

        double[][] prod = MatrixUtils.product(ma, mbinv);
        assert MatrixUtils.hasDimensions(prod, numVars, numVars * numLags);

        //        testPrint("piprime", prod);
        return prod;
    }

    private double[][] y(int t) {
        return MatrixUtils.transpose(this.yPrime(t));
    }

    private double[][] x(int t) {
        return MatrixUtils.transpose(this.xPrime(t));
    }

    private double[][] u(double[][] piPrime, int t) {
        return MatrixUtils.subtract(this.y(t), MatrixUtils.product(piPrime, this.x(t)));
    }

    /**
     * @return Sigma_u.
     */
    private double[][] sigmaU() {
        if (sigmaU == null) {
            return this.isStationary() ? this.sigmaUStationary() : this.sigmaUNonStationary();
        }
        return sigmaU;
    }

    private double[][] sigmaUStationary() {
        // Precalculate to avoid inverting the same huge matrix ad nauseum.
        double[][] piPrime = this.piPrime();
        double[][] sum = MatrixUtils.zeros(numVars, numVars);

        for (int t = 1; t <= this.getNumReps(); t++) {
            double[][] u = this.u(piPrime, t);
            double[][] uPrime = MatrixUtils.transpose(u);
            double[][] product = MatrixUtils.product(u, uPrime);
            sum = MatrixUtils.sum(sum, product);
        }

        return MatrixUtils.scalarProduct(1.0 / this.getNumReps(), sum);
    }

    private double[][] dkPlus() {
        double[][] dk = MatrixUtils.vechToVecLeft(numVars);
        double[][] dkPrime = MatrixUtils.transpose(dk);
        double[][] ma = MatrixUtils.product(dkPrime, dk);
        double[][] mainv = MatrixUtils.inverse(ma);
        return MatrixUtils.product(mainv, dkPrime);
    }

    private double[][] omega() {
        if (omega == null) {
            double[][] dkPlus = this.dkPlus();
            double[][] dkPlusPrime = MatrixUtils.transpose(dkPlus);
            double[][] prod1 = MatrixUtils.directProduct(this.sigmaU(), this.sigmaU());
            double[][] prod2 = MatrixUtils.scalarProduct(2.0, dkPlus);
            double[][] prod3 = MatrixUtils.product(prod2, prod1);
            omega = MatrixUtils.product(prod3, dkPlusPrime);
        }
        return omega;
    }

    // The following methods are for calculating nonstationary SigmaU.

    private double[][] sigmaUNonStationary() {
        // Precalculate to avoid inverting the same huge matrix ad nauseum.
        double[][] piPrime = this.piPrime();
        double[][] sum = MatrixUtils.zeros(numVars, numVars);

        for (int t = 1; t <= this.getNumReps(); t++) {
            double[][] u = this.u(piPrime, t);
            double[][] uPrime = MatrixUtils.transpose(u);
            double[][] product = MatrixUtils.product(u, uPrime);
            sum = MatrixUtils.sum(sum, product);
        }

        return MatrixUtils.scalarProduct(1.0 / this.getNumReps(), sum);
    }

//    private double[][] deltaY(int t) {
//        return MatrixUtils.subtract(y(t), y(t - 1));
//    }

//    private double[][] deltaY() {
//        double[][] deltaY = new double[numVars][numReps];
//        for (int i = 0; i < numReps; i++) {
//            double[][] col = deltaY(i + 1);
//            MatrixUtils.pasteCol(col, 0, deltaY, i);
//        }
//        return deltaY;
//    }

//    private double[][] deltaX(int t) {
//        double[] deltaXVec = new double[numVars * (numLags - 1) + 1];
//        deltaXVec[0] = 1;
//
//        for (int i = 0; i < getNumLags() - 1; i++) {
//            double[][] deltaY = deltaY(t - i);
//            System.arraycopy(deltaY, 0, deltaXVec, i * numVars, numVars);
//        }
//
//        return MatrixUtils.asCol(deltaXVec);
//
//    }

//    private double[][] deltaX() {
//        double[][] deltaX = new double[numVars * (numLags - 1)][numReps];
//
//        for (int i = 0; i < numLags; i++) {
//            MatrixUtils.pasteCol(deltaX(i), 0, deltaX, i);
//        }
//
//        return deltaX;
//    }

//    private double[][] yP() {
//        double[][] deltaY = new double[numVars][numReps];
//        for (int i = 0; i < numReps; i++) {
//            double[][] col = y(i + 1 - numLags);
//            MatrixUtils.pasteCol(col, 0, deltaY, i);
//        }
//        return deltaY;
//    }

//    private double[][] m() {
//        double[][] iT = MatrixUtils.identity(numLags);
//        double[][] deltaX = deltaX();
//        double[][] deltaXPrime = MatrixUtils.transpose(deltaX);
//        double[][] prod1 = MatrixUtils.product(deltaX, deltaXPrime);
//        double[][] prod2 = MatrixUtils.product(deltaXPrime, prod1);
//        double[][] prod3 = MatrixUtils.product(prod2, deltaX);
//        return MatrixUtils.subtract(iT, prod3);
//    }

//    private double[][] engine(int i) {
//        switch (i) {
//            case 0:
//                return MatrixUtils.product(deltaY(), m());
//            case 1:
//                return MatrixUtils.product(yP(), m());
//            default:
//                throw new IllegalArgumentException("Index must be 0 or 1.");
//        }
//    }

    //    private double[][] s2(int i, int j) {
    //        double[][] m = MatrixUtils.outerProduct(engine(i), engine(j));
    //        return MatrixUtils.scalarProduct(1.0 / numLags, m);
    //    }

    /**
     * @return the chi square cutoff value for the given degrees of freedom and significance level.
     */
    public double chiSquareCutoff() {
        double d = 0.0;
        for (int mantissa = 0; mantissa >= -15; mantissa--) {
            double increment = Math.pow(10, mantissa);
            while (d < 1000.0) {
                d += increment;
                if (ProbUtils.chisqCdf(d, 1.0) > 1.0 - this.getAlpha()) {
                    d -= increment;
                    break;
                }
            }
        }
        return d;
    }

    /**
     * Calculates the gradient of tau for the given indices array (for up to 4 conditioning vars).
     */
    private double tau() {
        int numCondVars = this.getIndices().length - 2;

        switch (numCondVars) {
            case 0:
                return this.tau0();
            case 1:
                return this.tau1();
            case 2:
                return this.tau2();
            case 3:
                return this.tau3();
            case 4:
                return this.tau4();
            default:
                throw new IllegalStateException("Only taus for up to " +
                        "four conditioning variables were hardcoded: " +
                        numCondVars);
        }
    }

    /**
     * @return tau for indArr = [1 2] (one-indexed). From Mathematica.
     */
    private double tau0() {
        return this.s(1, 2);
    }

    /**
     * @return tau for indArr = [1 2 3] (one-indexed). From Mathematica.
     */
    private double tau1() {
        return -this.s(1, 3) * this.s(2, 3) + this.s(1, 2) * this.s(3, 3);
    }

    /**
     * @return tau for indArr = [1 2 3 4] (one-indexed). From Mathematica.
     */
    private double tau2() {
        return -this.s(1, 4) * this.s(2, 4) * this.s(3, 3) + this.s(1, 4) * this.s(2, 3) * this.s(3, 4) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) - this.s(1, 2) * this.s(3, 4) * this.s(3, 4) -
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) + this.s(1, 2) * this.s(3, 3) * this.s(4, 4);
    }

    /**
     * @return tau for indArr = [1 2 3 4 5] (one-indexed). From Mathematica.
     */
    private double tau3() {
        return this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(3, 4) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(3, 5) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(3, 5) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(3, 5) -
                this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(4, 4) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(4, 5) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(4, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5);
    }

    /**
     * @return tau for indArr = [1 2 3 4 5 6] (one-indexed). From Mathematica.
     */
    private double tau4() {
        return this.s(1, 6) * this.s(2, 6) * this.s(3, 5) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 4) -
                2 * this.s(1, 6) * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) +
                this.s(1, 2) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) +
                this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 6) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) -
                this.s(1, 2) * this.s(3, 6) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) -
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) -
                this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) -
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6);
    }

    /**
     * Calculates the gradient of tau for the given indices array (for up to 4 conditioning vars).
     */
    private double[][] gradTau() {
        int numCondVars = this.getIndices().length - 2;

        switch (numCondVars) {
            case 0:
                return this.convertGradTau(this.gradTau0());
            case 1:
                return this.convertGradTau(this.gradTau1());
            case 2:
                return this.convertGradTau(this.gradTau2());
            case 3:
                return this.convertGradTau(this.gradTau3());
            case 4:
                return this.convertGradTau(this.gradTau4());
            default:
                throw new IllegalStateException("Only gradients for up to " +
                        "four conditioning variables were hardcoded: " +
                        numCondVars);
        }
    }

    /**
     * Takes a gradTau for a basic case and transforms it into a gradTau for the case at hand.
     */
    private double[][] convertGradTau(double[] basicGradTau) {
        double[][] m = MatrixUtils.invVech(basicGradTau);
        double[][] m2 = new double[numVars][numVars];

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m.length; j++) {
                m2[this.getIndices()[i]][this.getIndices()[j]] = m[i][j];
            }
        }

        return MatrixUtils.vech(m2);
    }

    /**
     * @return grad(tau) for numTimeSteps = 2, indArr = [1 2] (one-indexed). From Mathematica. </p> Examples of the
     * gradients of the tau functions were calculated using the following Mathematica script (in this case comparing v1
     * and v4 given v2 and v3). These were then modified using Xemacs into Java formulas. (In case anyone needs to do it
     * again.)</p>
     * <pre>
     * numVars = 6;
     * indexSub = {1, 4, 2, 3};
     * corrAllVars = Table[s[Min[i, j], Max[i, j]], {i, numVars}, {j,
     * numVars}];
     * subLen = Length[indexSub];
     * sub = Table[corrAllVars[[indexSub[[i]], indexSub[[j]]]], {i, subLen},
     * {j,
     * subLen}];
     * tau = -Numerator[Inverse[sub][[1, 2]]];
     * vechLen = numVars(numVars + 1)/2;
     * vechCorrAllVars = Table[0, {vechLen}];
     * numVars = 0;
     * For[i = 1, i <= numVars, i++,
     *   For[j = i, j <= numVars, j++,
     *     numVars = numVars + 1;
     *     vechCorrAllVars[[numVars]] = corrAllVars[[i, j]]
     *     ]
     *   ]
     * grad = Table[D[tau, vechCorrAllVars[[i]]], {i, vechLen}]
     * </pre>
     */
    private double[] gradTau0() {
        return new double[]{0, 1, 0};
    }

    /**
     * @return grad(tau) for numTimeSteps = 3, indArr = [1 2 3] (one-indexed). From Mathematica.
     */
    private double[] gradTau1() {
        return new double[]{0, this.s(3, 3), -this.s(2, 3), 0, -this.s(1, 3), this.s(1, 2)};
    }

    /**
     * @return grad(tau) for numTimeSteps = 4, indArr = [1 2 3 4] (one-indexed). From Mathematica.
     */
    private double[] gradTau2() {
        return new double[]{0, -this.s(3, 4) * this.s(3, 4) + this.s(3, 3) * this.s(4, 4),
                this.s(2, 4) * this.s(3, 4) - this.s(2, 3) * this.s(4, 4),
                -this.s(2, 4) * this.s(3, 3) + this.s(2, 3) * this.s(3, 4), 0,
                this.s(1, 4) * this.s(3, 4) - this.s(1, 3) * this.s(4, 4),
                -this.s(1, 4) * this.s(3, 3) + this.s(1, 3) * this.s(3, 4),
                -this.s(1, 4) * this.s(2, 4) + this.s(1, 2) * this.s(4, 4),
                this.s(1, 4) * this.s(2, 3) + this.s(1, 3) * this.s(2, 4) - 2 * this.s(1, 2) * this.s(3, 4),
                -this.s(1, 3) * this.s(2, 3) + this.s(1, 2) * this.s(3, 3)};
    }

    /**
     * @return grad(tau) for numTimeSteps = 5, indArr = [1 2 3 4 5] (one-indexed). From Mathematica.
     */
    private double[] gradTau3() {
        return new double[]{0, -this.s(3, 5) * this.s(3, 5) * this.s(4, 4) +
                2 * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) - this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(3, 4) * this.s(3, 4) * this.s(5, 5) + this.s(3, 3) * this.s(4, 4) * this.s(5, 5), this.s(2,
                5) * this.s(3, 5) * this.s(4, 4) - this.s(2, 5) * this.s(3, 4) * this.s(4, 5) -
                this.s(2, 4) * this.s(3, 5) * this.s(4, 5) + this.s(2, 3) * this.s(4, 5) * this.s(4, 5) +
                this.s(2, 4) * this.s(3, 4) * this.s(5, 5) - this.s(2, 3) * this.s(4, 4) * this.s(5, 5), -this.s(2,
                5) * this.s(3, 4) * this.s(3, 5) + this.s(2, 4) * this.s(3, 5) * this.s(3, 5) +
                this.s(2, 5) * this.s(3, 3) * this.s(4, 5) - this.s(2, 3) * this.s(3, 5) * this.s(4, 5) -
                this.s(2, 4) * this.s(3, 3) * this.s(5, 5) + this.s(2, 3) * this.s(3, 4) * this.s(5, 5), this.s(2,
                5) * this.s(3, 4) * this.s(3, 4) - this.s(2, 4) * this.s(3, 4) * this.s(3, 5) -
                this.s(2, 5) * this.s(3, 3) * this.s(4, 4) + this.s(2, 3) * this.s(3, 5) * this.s(4, 4) +
                this.s(2, 4) * this.s(3, 3) * this.s(4, 5) - this.s(2, 3) * this.s(3, 4) * this.s(4, 5), 0, this.s(
                1, 5) * this.s(3, 5) * this.s(4, 4) - this.s(1, 5) * this.s(3, 4) * this.s(4, 5) -
                this.s(1, 4) * this.s(3, 5) * this.s(4, 5) + this.s(1, 3) * this.s(4, 5) * this.s(4, 5) +
                this.s(1, 4) * this.s(3, 4) * this.s(5, 5) - this.s(1, 3) * this.s(4, 4) * this.s(5, 5), -this.s(1,
                5) * this.s(3, 4) * this.s(3, 5) + this.s(1, 4) * this.s(3, 5) * this.s(3, 5) +
                this.s(1, 5) * this.s(3, 3) * this.s(4, 5) - this.s(1, 3) * this.s(3, 5) * this.s(4, 5) -
                this.s(1, 4) * this.s(3, 3) * this.s(5, 5) + this.s(1, 3) * this.s(3, 4) * this.s(5, 5), this.s(1,
                5) * this.s(3, 4) * this.s(3, 4) - this.s(1, 4) * this.s(3, 4) * this.s(3, 5) -
                this.s(1, 5) * this.s(3, 3) * this.s(4, 4) + this.s(1, 3) * this.s(3, 5) * this.s(4, 4) +
                this.s(1, 4) * this.s(3, 3) * this.s(4, 5) - this.s(1, 3) * this.s(3, 4) * this.s(4, 5), -this.s(1,
                5) * this.s(2, 5) * this.s(4, 4) + this.s(1, 5) * this.s(2, 4) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 5) * this.s(4, 5) - this.s(1, 2) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 4) * this.s(5, 5) + this.s(1, 2) * this.s(4, 4) * this.s(5, 5), 2 *
                this.s(1, 5) * this.s(2, 5) * this.s(3, 4) - this.s(1, 5) * this.s(2, 4) * this.s(3, 5) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 5) - this.s(1, 5) * this.s(2, 3) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 5) * this.s(4, 5) + 2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 3) * this.s(5, 5) + this.s(1, 3) * this.s(2, 4) * this.s(5, 5) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(5, 5), -this.s(1, 5) * this.s(2, 4) * this.s(3, 4) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) + 2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 5) +
                this.s(1, 5) * this.s(2, 3) * this.s(4, 4) + this.s(1, 3) * this.s(2, 5) * this.s(4, 4) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 4) - this.s(1, 4) * this.s(2, 3) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(4, 5) + 2 * this.s(1, 2) * this.s(3, 4) * this.s(4, 5),
                -this.s(1, 5) * this.s(2, 5) * this.s(3, 3) + this.s(1, 5) * this.s(2, 3) * this.s(3, 5) +
                        this.s(1, 3) * this.s(2, 5) * this.s(3, 5) -
                        this.s(1, 2) * this.s(3, 5) * this.s(3, 5) -
                        this.s(1, 3) * this.s(2, 3) * this.s(5, 5) +
                        this.s(1, 2) * this.s(3, 3) * this.s(5, 5), this.s(1, 5) * this.s(2, 4) *
                this.s(3, 3) + this.s(1, 4) * this.s(2, 5) * this.s(3, 3) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) - this.s(1, 3) * this.s(2, 5) * this.s(3, 4) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) - this.s(1, 3) * this.s(2, 4) * this.s(3, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) +
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 5) -
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 5), -this.s(1, 4) * this.s(2, 4) * this.s(3, 3) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) + this.s(1, 3) * this.s(2, 4) * this.s(3, 4) -
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) - this.s(1, 3) * this.s(2, 3) * this.s(4, 4) +
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4)};
    }

    /**
     * @return grad(tau) for numTimeSteps = 6, indArr = [1 2 3 4 5 6] (one-indexed). From Mathematica.
     */
    private double[] gradTau4() {
        return new double[]{0, this.s(3, 6) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) -
                2 * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(3, 5) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) -
                this.s(3, 6) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) +
                2 * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(3, 3) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) +
                2 * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) -
                2 * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) -
                2 * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) +
                2 * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(3, 4) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(3, 3) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(3, 5) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) +
                2 * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(3, 3) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(3, 4) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) +
                this.s(3, 3) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6), -this.s(2, 6) * this.s(3, 6) *
                this.s(4, 5) * this.s(4, 5) + this.s(2, 6) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(2, 5) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) -
                this.s(2, 5) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) +
                this.s(2, 6) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) -
                this.s(2, 6) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(2, 4) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(2, 3) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(2, 6) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(2, 5) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) +
                this.s(2, 6) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6) +
                this.s(2, 4) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) +
                this.s(2, 5) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(2, 4) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(2, 3) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) -
                this.s(2, 4) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(2, 3) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(2, 5) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) -
                this.s(2, 5) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6) -
                this.s(2, 4) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(2, 3) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(2, 4) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) -
                this.s(2, 3) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6), this.s(2, 6) * this.s(3, 5) *
                this.s(3, 6) * this.s(4, 5) - this.s(2, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) -
                this.s(2, 6) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) +
                this.s(2, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(2, 6) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) +
                this.s(2, 4) * this.s(3, 6) * this.s(3, 6) * this.s(5, 5) +
                this.s(2, 6) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(2, 3) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) -
                2 * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(5, 6) -
                this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) -
                this.s(2, 5) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(2, 3) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(2, 4) * this.s(3, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(2, 3) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(2, 5) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) +
                this.s(2, 4) * this.s(3, 5) * this.s(3, 5) * this.s(6, 6) +
                this.s(2, 5) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(2, 3) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(2, 4) * this.s(3, 3) * this.s(5, 5) * this.s(6, 6) +
                this.s(2, 3) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6), -this.s(2, 6) * this.s(3, 5) *
                this.s(3, 6) * this.s(4, 4) + this.s(2, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 4) +
                this.s(2, 6) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) -
                this.s(2, 4) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) +
                this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) +
                this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(2, 5) * this.s(3, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(2, 3) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) -
                this.s(2, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(2, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) +
                this.s(2, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(2, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) -
                this.s(2, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(2, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(2, 5) * this.s(3, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(2, 4) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) -
                this.s(2, 5) * this.s(3, 3) * this.s(4, 4) * this.s(6, 6) +
                this.s(2, 3) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) +
                this.s(2, 4) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(2, 3) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6), this.s(2, 6) * this.s(3, 5) *
                this.s(3, 5) * this.s(4, 4) - this.s(2, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) -
                2 * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(2, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) +
                this.s(2, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) +
                this.s(2, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(2, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) +
                this.s(2, 5) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                this.s(2, 4) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) -
                this.s(2, 5) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(2, 3) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(2, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(2, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) -
                this.s(2, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(2, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) +
                this.s(2, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(2, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(2, 5) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(2, 4) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(2, 5) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(2, 3) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(2, 4) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(2, 3) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6), 0, -this.s(1, 6) * this.s(3, 6) *
                this.s(4, 5) * this.s(4, 5) + this.s(1, 6) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) -
                this.s(1, 5) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) +
                this.s(1, 6) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) -
                this.s(1, 6) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 4) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 3) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 5) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) +
                this.s(1, 6) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 4) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(1, 3) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) -
                this.s(1, 4) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) +
                this.s(1, 5) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) -
                this.s(1, 5) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6), this.s(1, 6) * this.s(3, 5) *
                this.s(3, 6) * this.s(4, 5) - this.s(1, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 6) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 4) * this.s(3, 6) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 6) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 6) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) -
                2 * this.s(1, 4) * this.s(3, 5) * this.s(3, 6) * this.s(5, 6) -
                this.s(1, 6) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) -
                this.s(1, 5) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(3, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 3) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 5) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(3, 5) * this.s(3, 5) * this.s(6, 6) +
                this.s(1, 5) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(3, 3) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6), -this.s(1, 6) * this.s(3, 5) *
                this.s(3, 6) * this.s(4, 4) + this.s(1, 5) * this.s(3, 6) * this.s(3, 6) * this.s(4, 4) +
                this.s(1, 6) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 4) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(3, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 3) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 5) * this.s(3, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 4) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 5) * this.s(3, 3) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 3) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 4) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6), this.s(1, 6) * this.s(3, 5) *
                this.s(3, 5) * this.s(4, 4) - this.s(1, 5) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) -
                2 * this.s(1, 6) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 5) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 4) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 3) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) +
                this.s(1, 5) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                this.s(1, 4) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) -
                this.s(1, 5) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 6) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 4) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 3) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 4) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 5) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 4) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6), this.s(1, 6) * this.s(2, 6) *
                this.s(4, 5) * this.s(4, 5) - this.s(1, 6) * this.s(2, 5) * this.s(4, 5) * this.s(4, 6) -
                this.s(1, 5) * this.s(2, 6) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 5) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 6) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 4) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 2) * this.s(4, 6) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(4, 4) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 6) * this.s(2, 4) * this.s(4, 5) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 6) * this.s(4, 5) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(4, 6) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(4, 6) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(4, 5) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 2) * this.s(4, 4) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 5) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 2) * this.s(4, 5) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 4) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 2) * this.s(4, 4) * this.s(5, 5) * this.s(6, 6), -2 * this.s(1, 6) * this.s(2, 6) *
                this.s(3, 5) * this.s(4, 5) + this.s(1, 6) * this.s(2, 5) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 6) * this.s(4, 6) +
                2 * this.s(1, 6) * this.s(2, 6) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 6) * this.s(4, 6) * this.s(5, 5) +
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 6) * this.s(5, 5) -
                2 * this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(5, 6) -
                2 * this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(4, 5) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 6) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(5, 6) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(5, 6) * this.s(5, 6) +
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(4, 5) * this.s(6, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 5) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(5, 5) * this.s(6, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(5, 5) * this.s(6, 6), 2 * this.s(1, 6) *
                this.s(2, 6) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 6) * this.s(4, 4) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 6) * this.s(4, 4) -
                2 * this.s(1, 6) * this.s(2, 6) * this.s(3, 4) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(4, 6) -
                2 * this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(4, 5) * this.s(4, 6) -
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 5) * this.s(4, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(4, 6) * this.s(4, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 6) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(5, 6) -
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 6) * this.s(5, 6) -
                this.s(1, 6) * this.s(2, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 6) * this.s(4, 4) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(4, 6) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(4, 6) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(6, 6) +
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(6, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(4, 4) * this.s(6, 6) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 4) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(4, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(4, 5) * this.s(6, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(4, 5) * this.s(6, 6), -this.s(1, 6) * this.s(2, 5) *
                this.s(3, 5) * this.s(4, 4) - this.s(1, 5) * this.s(2, 6) * this.s(3, 5) * this.s(4, 4) +
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 6) * this.s(4, 4) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(4, 5) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(4, 5) -
                2 * this.s(1, 5) * this.s(2, 4) * this.s(3, 6) * this.s(4, 5) -
                2 * this.s(1, 4) * this.s(2, 5) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 6) * this.s(2, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 6) * this.s(4, 5) * this.s(4, 5) +
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 5) * this.s(4, 5) -
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(4, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(4, 5) * this.s(4, 6) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 5) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(5, 5) +
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 6) * this.s(4, 4) * this.s(5, 5) -
                2 * this.s(1, 2) * this.s(3, 6) * this.s(4, 4) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(4, 6) * this.s(5, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(4, 6) * this.s(5, 5) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(5, 6) -
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(4, 4) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(4, 4) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(4, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(4, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(4, 5) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(4, 5) * this.s(5, 6), this.s(1, 6) * this.s(2, 6) *
                this.s(3, 5) * this.s(3, 5) - this.s(1, 6) * this.s(2, 5) * this.s(3, 5) * this.s(3, 6) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 5) * this.s(3, 6) +
                this.s(1, 5) * this.s(2, 5) * this.s(3, 6) * this.s(3, 6) -
                this.s(1, 6) * this.s(2, 6) * this.s(3, 3) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 2) * this.s(3, 6) * this.s(3, 6) * this.s(5, 5) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(5, 6) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(5, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 2) * this.s(3, 3) * this.s(5, 6) * this.s(5, 6) -
                this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(6, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 3) * this.s(5, 5) * this.s(6, 6) +
                this.s(1, 2) * this.s(3, 3) * this.s(5, 5) * this.s(6, 6), -2 * this.s(1, 6) * this.s(2, 6) *
                this.s(3, 4) * this.s(3, 5) + this.s(1, 6) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 6) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(3, 6) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 6) * this.s(3, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 6) * this.s(3, 6) +
                2 * this.s(1, 6) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) -
                2 * this.s(1, 6) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) -
                2 * this.s(1, 3) * this.s(2, 6) * this.s(3, 6) * this.s(4, 5) +
                2 * this.s(1, 2) * this.s(3, 6) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(4, 6) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(4, 6) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(5, 6) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(5, 6) -
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 6) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 6) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(6, 6) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(6, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(6, 6) +
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(6, 6) -
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(6, 6), this.s(1, 6) * this.s(2, 5) *
                this.s(3, 4) * this.s(3, 5) + this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 5) * this.s(3, 5) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 5) * this.s(3, 5) -
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 5) * this.s(3, 6) -
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(4, 5) -
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(4, 5) -
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(4, 5) +
                2 * this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(4, 6) -
                2 * this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(4, 6) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(5, 5) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(5, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(5, 5) +
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 6) * this.s(5, 5) -
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 6) * this.s(5, 5) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(5, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(5, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(5, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(5, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(5, 6) -
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(5, 6), this.s(1, 6) * this.s(2, 6) *
                this.s(3, 4) * this.s(3, 4) - this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(3, 6) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(3, 6) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 6) * this.s(3, 6) -
                this.s(1, 6) * this.s(2, 6) * this.s(3, 3) * this.s(4, 4) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 6) * this.s(4, 4) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 6) * this.s(4, 4) -
                this.s(1, 2) * this.s(3, 6) * this.s(3, 6) * this.s(4, 4) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(4, 6) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(4, 6) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(4, 6) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(4, 6) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(4, 6) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(4, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 6) * this.s(4, 6) -
                this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(6, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(6, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(6, 6) -
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(6, 6) +
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(6, 6), -this.s(1, 6) * this.s(2, 5) *
                this.s(3, 4) * this.s(3, 4) - this.s(1, 5) * this.s(2, 6) * this.s(3, 4) * this.s(3, 4) +
                this.s(1, 6) * this.s(2, 4) * this.s(3, 4) * this.s(3, 5) +
                this.s(1, 4) * this.s(2, 6) * this.s(3, 4) * this.s(3, 5) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(3, 6) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(3, 6) -
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(3, 6) +
                this.s(1, 6) * this.s(2, 5) * this.s(3, 3) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 6) * this.s(3, 3) * this.s(4, 4) -
                this.s(1, 6) * this.s(2, 3) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 3) * this.s(2, 6) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 6) * this.s(4, 4) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 6) * this.s(4, 4) +
                2 * this.s(1, 2) * this.s(3, 5) * this.s(3, 6) * this.s(4, 4) -
                this.s(1, 6) * this.s(2, 4) * this.s(3, 3) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 6) * this.s(3, 3) * this.s(4, 5) +
                this.s(1, 6) * this.s(2, 3) * this.s(3, 4) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 6) * this.s(3, 4) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 6) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 6) * this.s(4, 5) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 6) * this.s(4, 5) -
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(4, 6) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(4, 6) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(4, 6) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(4, 6) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(4, 6) -
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(4, 6) +
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(4, 6) +
                2 * this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(5, 6) -
                2 * this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(5, 6) -
                2 * this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(5, 6) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(5, 6) +
                2 * this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(5, 6) -
                2 * this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(5, 6), this.s(1, 5) * this.s(2, 5) *
                this.s(3, 4) * this.s(3, 4) - this.s(1, 5) * this.s(2, 4) * this.s(3, 4) * this.s(3, 5) -
                this.s(1, 4) * this.s(2, 5) * this.s(3, 4) * this.s(3, 5) +
                this.s(1, 4) * this.s(2, 4) * this.s(3, 5) * this.s(3, 5) -
                this.s(1, 5) * this.s(2, 5) * this.s(3, 3) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 3) * this.s(3, 5) * this.s(4, 4) +
                this.s(1, 3) * this.s(2, 5) * this.s(3, 5) * this.s(4, 4) -
                this.s(1, 2) * this.s(3, 5) * this.s(3, 5) * this.s(4, 4) +
                this.s(1, 5) * this.s(2, 4) * this.s(3, 3) * this.s(4, 5) +
                this.s(1, 4) * this.s(2, 5) * this.s(3, 3) * this.s(4, 5) -
                this.s(1, 5) * this.s(2, 3) * this.s(3, 4) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 5) * this.s(3, 4) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 3) * this.s(3, 5) * this.s(4, 5) -
                this.s(1, 3) * this.s(2, 4) * this.s(3, 5) * this.s(4, 5) +
                2 * this.s(1, 2) * this.s(3, 4) * this.s(3, 5) * this.s(4, 5) +
                this.s(1, 3) * this.s(2, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 2) * this.s(3, 3) * this.s(4, 5) * this.s(4, 5) -
                this.s(1, 4) * this.s(2, 4) * this.s(3, 3) * this.s(5, 5) +
                this.s(1, 4) * this.s(2, 3) * this.s(3, 4) * this.s(5, 5) +
                this.s(1, 3) * this.s(2, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 2) * this.s(3, 4) * this.s(3, 4) * this.s(5, 5) -
                this.s(1, 3) * this.s(2, 3) * this.s(4, 4) * this.s(5, 5) +
                this.s(1, 2) * this.s(3, 3) * this.s(4, 4) * this.s(5, 5)};
    }

    /**
     * @return the sample correlation of var(i) with var(j) (one-indexed).
     */
    private double s(int i, int j) {
        return this.indexedCorr().getValue(i - 1, j - 1);
    }

    public double getChiSquare() {
        return chiSquare;
    }

    public String toString() {
        return "Time Series";
    }

    public DataSet getData() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<Matrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return this.getPValue();
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





