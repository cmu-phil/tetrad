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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.abs;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestPartialCorrelation implements IndependenceTest {

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
    private double pValue;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;

    private int sampleSize;

    CovarianceMatrix cov;


    //==========================CONSTRUCTORS=============================//

    public IndTestPartialCorrelation(DataSet data, double alpha) {
        this.dataSet = data;
        this.alpha = alpha;
        this.sampleSize = data.getNumRows();
        this.variables = new ArrayList<Node>(data.getVariables());
        this.cov = new CovarianceMatrix(data);
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    public boolean isIndependent(Node x, Node y, List<Node> z) {
        return indepCollection(x, y, alpha);
    }

    private boolean indepCollection(Node x, Node y, double alpha) {
        TetradMatrix submatrix = cov.getMatrix();

        TetradMatrix inverse;

        try {
            inverse = submatrix.inverse();
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }

        int i = variables.indexOf(x);
        int j = variables.indexOf(y);

        double a = -1.0 * inverse.get(i, j);
        double v0 = inverse.get(i, i);
        double v1 = inverse.get(j, j);
        double b = Math.sqrt(v0 * v1);

        double r = a / b;

        double fisherZ = Math.sqrt(cov.getSampleSize() - 3 - (variables.size() - 2)) * 0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));
        double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
        return p > alpha;

    }

    private List<Node> listVars(int[] indices, List<Node> vars) {
        List<Node> nodes = new ArrayList<Node>();

        for (int i : indices) {
            nodes.add(vars.get(i));
        }

        return nodes;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static TetradMatrix subMatrix(ICovarianceMatrix m, List<Node> x, List<Node> y, List<Node> z) {
        List<Node> variables = m.getVariables();
        TetradMatrix _covMatrix = m.getMatrix();

        // Create index array for the given variables.
        int[] indices = new int[x.size() + y.size() + z.size()];

        for (int i = 0; i < x.size(); i++) {
            indices[i] = variables.indexOf(x.get(i));
        }

        for (int i = 0; i < y.size(); i++) {
            indices[x.size() + i] = variables.indexOf(y.get(i));
        }

        for (int i = 0; i < z.size(); i++) {
            indices[x.size() + y.size() + i] = variables.indexOf(z.get(i));
        }

//        System.out.println(Arrays.toString(indices));

        // Extract submatrix of correlation matrix using this index array.
        TetradMatrix submatrix = _covMatrix.getSelection(indices, indices);

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
        return pValue;
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
        throw new UnsupportedOperationException();
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
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
    }

    public void shuffleVariables() {
        ArrayList<Node> nodes = new ArrayList<Node>(this.variables);
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

    private Map<String, Node> mapNames(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<String, Node>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    private Map<Node, Integer> indexMap(List<Node> variables) {
        Map<Node, Integer> indexMap = new ConcurrentHashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
    }

    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DataSet> getDataSets() {

        List<DataSet> dataSets = new ArrayList<DataSet>();

        dataSets.add(dataSet);

        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }


}




