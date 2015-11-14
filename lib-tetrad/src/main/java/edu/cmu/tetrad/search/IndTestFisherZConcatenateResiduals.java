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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DoubleDataBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calculates independence from pooled residuals.
 *
 * @author Joseph Ramsey
 */
public final class IndTestFisherZConcatenateResiduals implements IndependenceTest {


    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    private ArrayList<Regression> regressions;

    private List<DataSet> dataSets;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * The value of the Fisher's Z statistic associated with the last calculated partial correlation.
     */
//    private double fisherZ;

    private double pValue = Double.NaN;

//    private DataSet concatenatedData;

    //==========================CONSTRUCTORS=============================//

    public IndTestFisherZConcatenateResiduals(List<DataSet> dataSets, double alpha) {
        System.out.println("# data sets = " + dataSets.size());
        this.dataSets = dataSets;

        regressions = new ArrayList<Regression>();

        for (DataSet dataSet : dataSets) {
            DataSet _dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()),
                    dataSets.get(0).getVariables());

            regressions.add(new RegressionDataset(_dataSet));
        }

        setAlpha(alpha);

//        this.concatenatedData = DataUtils.concatenateData(dataSets);

        this.variables = dataSets.get(0).getVariables();

        List<DataSet> dataSets2 = new ArrayList<DataSet>();

        for (int i = 0; i < dataSets.size(); i++) {
            DataSet dataSet = ColtDataSet.makeContinuousData(variables, dataSets.get(i).getDoubleData());
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    //==========================PUBLIC METHODS=============================//

    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
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

        x = getVariable(variables, x.getName());
        z = GraphUtils.replaceNodes(z, variables);

        // Calculate the residual of x and y conditional on z for each data set and concatenate them.
        double[] residualsX = residuals(x, z);
        double[] residualsY = residuals(y, z);

        List<Double> residualsXFiltered = new ArrayList<Double>();
        List<Double> residualsYFiltered = new ArrayList<Double>();

        // This is the way of dealing with missing values; residuals are only correlated
        // for data sets in which both residuals exist.
        for (int i = 0; i < residualsX.length; i++) {
            if (!Double.isNaN(residualsX[i]) && !Double.isNaN(residualsY[i])) {
                residualsXFiltered.add(residualsX[i]);
                residualsYFiltered.add(residualsY[i]);
            }
        }

        residualsX = new double[residualsXFiltered.size()];
        residualsY = new double[residualsYFiltered.size()];

        for (int i = 0; i < residualsXFiltered.size(); i++) {
            residualsX[i] = residualsXFiltered.get(i);
            residualsY[i] = residualsYFiltered.get(i);
        }


        if (residualsX.length != residualsY.length) throw new IllegalArgumentException("Missing values handled.");
        int sampleSize = residualsX.length;

        // return a judgement of whether these concatenated residuals are independent.
        double r = StatUtils.correlation(residualsX, residualsY);

        if (r > 1.) r = 1.;
        if (r < -1.) r = -1.;

        double fisherZ = Math.sqrt(sampleSize - z.size() - 3.0) *
                0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));

        if (Double.isNaN(fisherZ)) {
            return false;
//            throw new IllegalArgumentException("The Fisher's Z " +
//                    "score for independence fact " + x + " _||_ " + y + " | " +
//                    z + " is undefined. r = " + r);
        }

        double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fisherZ)));
        this.pValue = pvalue;
        boolean independent = pvalue > alpha;

        if (independent) {
            TetradLogger.getInstance().log("independencies",
                    SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
            System.out.println(SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
        } else {
            TetradLogger.getInstance().log("dependencies",
                    SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
        }

        return independent;

    }


    private double[] residuals(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<Double>();

        Node target = dataSets.get(0).getVariable(node.getName());

        List<Node> regressors = new ArrayList<Node>();

        for (Node _regressor : parents) {
            Node variable = dataSets.get(0).getVariable(_regressor.getName());
            regressors.add(variable);
        }


        for (int m = 0; m < dataSets.size(); m++) {
            RegressionResult result = regressions.get(m).regress(target, regressors);
            double[] residualsSingleDataset = result.getResiduals().toArray();

            double mean = StatUtils.mean(residualsSingleDataset);
            for (int i2 = 0; i2 < residualsSingleDataset.length; i2++) {
                residualsSingleDataset[i2] = residualsSingleDataset[i2] - mean;
            }

            for (double d : residualsSingleDataset) {
                _residuals.add(d);
            }
        }

        double[] _f = new double[_residuals.size()];


        for (int k = 0; k < _residuals.size(); k++) {
            _f[k] = _residuals.get(k);
        }

        return _f;
    }

    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
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
        return this.pValue;
//        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(fisherZ)));
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
     * @throws UnsupportedOperationException
     */
    public boolean determines(List z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException
     */
    public DataSet getData() {
        return DataUtils.concatenateData(dataSets);
    }

    @Override
    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<DataSet>();

        for (DataSet d : dataSets) {
            _dataSets.add(DataUtils.standardizeData(d));
        }

        return new CovarianceMatrix(DataUtils.concatenateData(_dataSets));
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


    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher Z, Concatenating Residuals";
    }
}


