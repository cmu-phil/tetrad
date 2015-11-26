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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;

import java.text.NumberFormat;
import java.util.*;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestFisherZOnTheFly implements IndependenceTest {

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private double fisherZ;

    /**
     * The FisherZD independence test, used when Fisher Z throws an exception (i.e., when there's a collinearity).
     */
    private IndTestFisherZGeneralizedInverse deterministicTest;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;
    private TetradMatrix _dataSet;

    /**
     * A stored p value, if the deterministic test was used.
     */
    private double pValue = Double.NaN;
    private Map<Node, Integer> indices;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestFisherZOnTheFly(DataSet dataSet, double alpha) {
//        if (!(dataSet.isContinuous())) {
//            throw new IllegalArgumentException("Data set must be continuous.");
//        }

//        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.variables = dataSet.getVariables();
        setAlpha(alpha);

//        this.deterministicTest = new IndTestFisherZGeneralizedInverse(dataSet, alpha);
        this.dataSet = dataSet;
        this._dataSet = dataSet.getDoubleData();

        this.indices = new HashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            indices.put(variables.get(i), i);
        }
    }

    /**
     * Constructs a new Fisher Z independence test with the listed arguments.
     *
     * @param data      A 2D continuous data set with no missing values.
     * @param variables A list of variables, a subset of the variables of <code>data</code>.
     * @param alpha     The significance cutoff level. p values less than alpha will be reported as dependent.
     */
    public IndTestFisherZOnTheFly(TetradMatrix data, List<Node> variables, double alpha) {
        DataSet dataSet = ColtDataSet.makeContinuousData(variables, data);
        this.variables = Collections.unmodifiableList(variables);
        setAlpha(alpha);

//        this.deterministicTest = new IndTestFisherZGeneralizedInverse(dataSet, alpha);
    }

    public IndTestFisherZOnTheFly(DataSet data, List<Node> variables, double alpha) {
        this.dataSet = data;
        this._dataSet = data.getDoubleData();
        this.variables = variables;
        this.alpha = alpha;

        this.indices = new HashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            indices.put(variables.get(i), i);
        }
    }

    /**
     * Constructs a new independence test that will determine conditional independence facts using the given correlation
     * matrix and the given significance level.
     */
    public IndTestFisherZOnTheFly(ICovarianceMatrix corrMatrix, double alpha) {
        this.variables = Collections.unmodifiableList(corrMatrix.getVariables());
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
            if (!variables.contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        int[] indices = new int[vars.size()];

        for (int i = 0; i < indices.length; i++) {
//            indices[i] = variables.indexOf(vars.get(i));
            indices[i] = this.indices.get(vars.get(i));
        }

        double alphaNew = getAlpha();
        return new IndTestFisherZOnTheFly(dataSet, alphaNew);
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        TetradMatrix submatrix = getSubmatrix(x, y, z);


        double r = 0;

        try {
            r = StatUtils.partialCorrelation(submatrix);

            if (Double.isNaN((r)) || r < -1. || r > 1.) throw new RuntimeException();
        } catch (Exception e) {
            DepthChoiceGenerator gen = new DepthChoiceGenerator(z.size(), z.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                try {
                    List<Node> z2 = new ArrayList<Node>(z);
                    z2.removeAll(GraphUtils.asList(choice, z));
                    submatrix = getSubmatrix(x, y, z2);
                    r = StatUtils.partialCorrelation(submatrix);
                } catch (Exception e2) {
                    continue;
                }

//                if (Double.isNaN(r)) continue;
//
//                if (r > 1.) r = 1.;
//                 if (r < -1.) r = -1.;

                if (Double.isNaN(r) || r < -1. || r > 1.) continue;

                break;
            }
        }

        // Either dividing by a zero standard deviation (in which case it's dependent) or doing a regression
        // (effectively) with a multicolliarity
        if (Double.isNaN(r)) {
            int[] _z = new int[z.size()];
//            for (int i = 0; i < _z.length; i++) _z[i] = i + 2;
//
////            double varx = StatUtils.partialVariance(submatrix, 0, _z); // submatrix.get(0, 0);
////            double vary = StatUtils.partialVariance(submatrix, 1, _z); //submatrix.get(1, 1);
//
//            double varx = submatrix.get(0, 0);
//            double vary = submatrix.get(1, 1);
//
//            if (varx * vary == 0) {
            return true;
//            }
        }

        if (r > 1.) r = 1.;
        if (r < -1.) r = -1.;

        this.fisherZ = Math.sqrt(sampleSize() - z.size() - 3.0) *
                0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));

        if (Double.isNaN(this.fisherZ)) {
            throw new IllegalArgumentException("The Fisher's Z " +
                    "score for independence fact " + x + " _||_ " + y + " | " +
                    z + " is undefined. r = " + r);
        }

        boolean independent = getPValue() > alpha;

        if (independent) {
            TetradLogger.getInstance().log("independencies",
                    SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
        } else {
            TetradLogger.getInstance().log("dependencies",
                    SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
        }

        return independent;
    }

    private TetradMatrix getSubmatrix(Node x, Node y, List<Node> z) {
        int dim = z.size() + 2;
        int[] indices = new int[dim];
        indices[0] = variables.indexOf(x);
        indices[1] = variables.indexOf(y);
        for (int k = 0; k < z.size(); k++) {
            indices[k + 2] = variables.indexOf(z.get(k));
        }

        TetradMatrix submatrix = new TetradMatrix(dim, dim);

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                int i1 = indices[i];
                int i2 = indices[j];
                double[] coli = _dataSet.getColumn(i1).toArray();
                double[] colj = _dataSet.getColumn(i2).toArray();
                submatrix.set(i, j, StatUtils.correlation(coli, colj));
            }
        }
        return submatrix;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        if (!Double.isNaN(this.pValue)) {
            return Double.NaN;
        } else {
            return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fisherZ)));
        }
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
//        this.thresh = Double.NaN;
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
//        int[] parents = new int[z.size()];
//
//        for (int j = 0; j < parents.length; j++) {
//            parents[j] = covMatrix.getVariables().indexOf(z.get(j));
//        }
//
//        int i = covMatrix.getVariables().indexOf(x);
//
//        TetradMatrix matrix2D = covMatrix.getMatrix();
//        double variance = matrix2D.get(i, i);
//
//        if (parents.length > 0) {
//
//            // Regress z onto i, yielding regression coefficients b.
//            TetradMatrix Czz =
//                   matrix2D.viewSelection(parents, parents);
//            TetradMatrix inverse;
//            try {
//                inverse = TetradAlgebra.inverse(Czz);
////                inverse = MatrixUtils.ginverse(Czz);
//            }
//            catch (Exception e) {
//                return true;
//            }
//
//            TetradVector Cyz = matrix2D.viewColumn(i);
//            Cyz = Cyz.viewSelection(parents);
//            TetradVector b = TetradAlgebra.times(inverse, Cyz);
//
//            variance -= TetradAlgebra.times(Cyz, b);
//        }
//
//        return variance < 0.01;
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
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
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    public void shuffleVariables() {
        List<Node> nodes = new ArrayList(this.variables);
        Collections.shuffle(nodes);
        this.variables = Collections.unmodifiableList(nodes);
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher's Z, alpha = " + nf.format(getAlpha());
    }

    //==========================PRIVATE METHODS============================//

    /**
     * Computes that value x such that P(abs(N(0,1) > x) < alpha.  Note that this is a two sided test of the null
     * hypothesis that the Fisher's Z value, which is distributed as N(0,1) is not equal to 0.0.
     */
    private double cutoffGaussian(double alpha) {
        double upperTail = 1.0 - alpha / 2.0;
        double epsilon = 1e-14;

        // Find an upper bound.
        double lowerBound = -1.0;
        double upperBound = 0.0;

        while (ProbUtils.normalCdf(upperBound) < upperTail) {
            lowerBound += 1.0;
            upperBound += 1.0;
        }

        while (upperBound >= lowerBound + epsilon) {
            double midPoint = lowerBound + (upperBound - lowerBound) / 2.0;

            if (ProbUtils.normalCdf(midPoint) <= upperTail) {
                lowerBound = midPoint;
            } else {
                upperBound = midPoint;
            }
        }

        return lowerBound;
    }

    private int sampleSize() {
        return _dataSet.rows();
    }
}


