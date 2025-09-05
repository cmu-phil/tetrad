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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author josephramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 * @version $Id: $Id
 */
@Deprecated(since = "7.9", forRemoval = false)
public final class IndTestPositiveCorr implements IndependenceTest {

    /**
     * The covariance matrix.
     */
    private final ICovarianceMatrix covMatrix;
    private final double[][] data;
    /**
     * Stores a reference to the dataset being analyzed.
     */
    private final DataSet dataSet;
    private final Map<String, Node> nameMap;
    private final double fisherZ = Double.NaN;
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    private boolean verbose = true;
    private double cutoff = Double.NaN;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestPositiveCorr(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        this.covMatrix = new CovarianceMatrix(dataSet);
        List<Node> nodes = this.covMatrix.getVariables();

        this.variables = Collections.unmodifiableList(nodes);
        this.nameMap = nameMap(this.variables);
        setAlpha(alpha);

        this.dataSet = dataSet;

        this.data = dataSet.getDoubleData().transpose().toArray();

    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Performs an independence test on a subset of variables.
     *
     * @param vars The sublist of variables.
     * @return An IndependenceTest object representing the result of the test.
     * @throws UnsupportedOperationException This method is not implemented and will always throw an
     *                                       UnsupportedOperationException.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks the independence between two nodes, given a set of conditioning nodes.
     *
     * @param x0  First node to check independence for.
     * @param y0  Second node to check independence for.
     * @param _z0 Set of conditioning nodes.
     * @return An IndependenceResult object representing the result of the independence test.
     */
    public IndependenceResult checkIndependence(Node x0, Node y0, Set<Node> _z0) {

        System.out.println(LogUtilsSearch.independenceFact(x0, y0, _z0));

        double[] x = this.data[this.dataSet.getColumnIndex(x0)];
        double[] y = this.data[this.dataSet.getColumnIndex(y0)];

        List<Node> z0 = new ArrayList<>(_z0);
        Collections.sort(z0);

        double[][] _Z = new double[z0.size()][];

        for (int f = 0; f < z0.size(); f++) {
            Node _z = z0.get(f);
            int column = this.dataSet.getColumnIndex(_z);
            _Z[f] = this.data[column];
        }

        double lambea = 0.0;

        double pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY, lambea);
        double pc1 = partialCorrelation(x, y, _Z, x, 0, lambea);
        double pc2 = partialCorrelation(x, y, _Z, y, 0, lambea);

        int nc = StatUtils.getRows(x, Double.NEGATIVE_INFINITY, +1).size();
        int nc1 = StatUtils.getRows(x, 0, +1).size();
        int nc2 = StatUtils.getRows(y, 0, +1).size();

        double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
        double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
        double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

        double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
        double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

        double p1 = (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv1)));
        double p2 = (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv2)));

        boolean rejected1 = p1 < this.alpha;
        boolean rejected2 = p2 < this.alpha;

        boolean possibleEdge = false;

        if (zv1 < 0 && zv2 > 0 && rejected1) {
            possibleEdge = true;
        } else if (zv1 > 0 && zv2 < 0 && rejected2) {
            possibleEdge = true;
        } else if (rejected1 && rejected2) {
            possibleEdge = true;
        } else if (rejected1 || rejected2) {
            possibleEdge = true;
        }

        System.out.println(possibleEdge);

        double pValue = getPValue();

        if (Double.isNaN(pValue)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                                       LogUtilsSearch.independenceFact(x0, y0, _z0));
        }

        return new IndependenceResult(new IndependenceFact(x0, y0, _z0), !possibleEdge, pValue, alpha - pValue);
    }

    /**
     * Calculates the p-value for the independence test.
     *
     * @return The p-value of the independence test.
     */
    public double getPValue() {
        return 2.0 * (1.0 - this.normal.cumulativeProbability(abs(this.fisherZ)));
    }

    /**
     * Retrieves the alpha level of the Independence Test.
     *
     * @return The alpha level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Set the alpha level for the significance of the test.
     *
     * @param alpha The alpha level for the significance of the test. Must be a value between 0.0 and 1.0, inclusive.
     * @throws IllegalArgumentException if the alpha value is out of range.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }

        this.alpha = alpha;
        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    /**
     * Retrieves the list of variables used in the independence test.
     *
     * @return The list of variables used in the independence test.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Sets the variables used in the independence test.
     *
     * @param variables A list of Nodes representing the variables to be used in the test.
     * @throws IllegalArgumentException if the number of variables is different from the current number of variables in
     *                                  the test.
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.covMatrix.setVariables(variables);
    }

    /**
     * Retrieves the node associated with the given variable name.
     *
     * @param name a {@link String} object representing the variable name.
     * @return the node associated with the variable name.
     */
    public Node getVariable(String name) {
        return this.nameMap.get(name);
    }

    /**
     * Determines if there exists a causal relationship between the nodes in z and node x.
     *
     * @param z The list of nodes representing the potential causes.
     * @param x The node representing the potential effect.
     * @return true if there exists a causal relationship between the nodes in z and node x, false otherwise.
     * @throws UnsupportedOperationException if the method is not implemented.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = this.covMatrix.getVariables().indexOf(z.get(j));
        }

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            Matrix Czz = this.covMatrix.getSelection(parents, parents);
//            TetradMatrix inverse;

            try {
                Czz.inverse();
            } catch (SingularMatrixException e) {
                return true;
            }

        }

        return false;
    }

    /**
     * Retrieve the data set used in the independence test.
     *
     * @return The data set.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    //==========================PRIVATE METHODS============================//

    /**
     * Returns a string representation of the Fisher Z object.
     *
     * @return A string representation of the Fisher Z object.
     */
    public String toString() {
        return "Fisher Z, alpha = " + new DecimalFormat("0.0E0").format(getAlpha());
    }

    private Map<String, Node> nameMap(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    /**
     * <p>getCov.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCov() {
        return this.covMatrix;
    }

    /**
     * Retrieves the data sets used in the independence test.
     *
     * @return A list of data sets.
     */
    @Override
    public List<DataSet> getDataSets() {

        List<DataSet> dataSets = new ArrayList<>();

        dataSets.add(this.dataSet);

        return dataSets;
    }

    /**
     * Retrieves the sample size of the covariance matrix.
     *
     * @return The sample size.
     */
    @Override
    public int getSampleSize() {
        return this.covMatrix.getSampleSize();
    }

    /**
     * Returns whether the verbose mode is enabled.
     *
     * @return true if verbose mode is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose mode to either enabled or disabled.
     *
     * @param verbose True if verbose mode is enabled, false otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private double partialCorrelation(double[] x, double[] y, double[][] z, double[] condition, double threshold, double lambda) throws SingularMatrixException {
        double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, 1);
        Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m, lambda);
    }
}




