///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates independence from pooled residuals using the Fisher Z method.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IndTestFisherZ
 */
@Deprecated(since = "7.9", forRemoval = false)
public final class IndTestFisherZConcatenateResiduals implements IndependenceTest {

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;
    /**
     * The regressions.
     */
    private final ArrayList<Regression> regressions;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The data sets.
     */
    private List<DataSet> dataSets;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructor.
     *
     * @param dataSets The continuous datasets to analyze.
     * @param alpha    The alpha significance cutoff value.
     */
    public IndTestFisherZConcatenateResiduals(List<DataSet> dataSets, double alpha) {
        System.out.println("# data sets = " + dataSets.size());
        this.dataSets = dataSets;

        this.regressions = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            DataSet _dataSet = new BoxDataSet(new DoubleDataBox(dataSet.getDoubleData().toArray()),
                    dataSets.get(0).getVariables());

            this.regressions.add(new RegressionDataset(_dataSet));
        }

        setAlpha(alpha);

        this.variables = dataSets.get(0).getVariables();

        List<DataSet> dataSets2 = new ArrayList<>();

        for (DataSet set : dataSets) {
            DataSet dataSet = new BoxDataSet(new DoubleDataBox(set.getDoubleData().toArray()), this.variables);
            dataSets2.add(dataSet);
        }

        this.dataSets = dataSets2;
    }

    /**
     * Returns an Independence test for a sublist of the variables.
     *
     * @param vars The sublist of variables.
     * @return an instance of IndependenceTest.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether x _||_ y | z.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (facts.containsKey(new IndependenceFact(x, y, _z))) {
            return facts.get(new IndependenceFact(x, y, _z));
        }

        x = getVariable(this.variables, x.getName());
        List<Node> z = GraphUtils.replaceNodes(new ArrayList<>(_z), new ArrayList<>(this.variables));

        // Calculate the residual of x and y conditional on z for each data set and concatenate them.
        double[] residualsX = residuals(x, z);
        double[] residualsY = residuals(y, z);

        List<Double> residualsXFiltered = new ArrayList<>();
        List<Double> residualsYFiltered = new ArrayList<>();

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

        double fisherZ = FastMath.sqrt(sampleSize - z.size() - 3.0) *
                         0.5 * (FastMath.log(1.0 + r) - FastMath.log(1.0 - r));

        if (Double.isNaN(fisherZ)) {
            return new IndependenceResult(new IndependenceFact(x, y, _z),
                    true, Double.NaN, Double.NaN);
        }

        double pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, FastMath.abs(fisherZ)));

        if (Double.isNaN(pValue)) {
            throw new RuntimeException("Undefined p-value encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
        }

        boolean independent = pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, pValue - getAlpha());
        facts.put(new IndependenceFact(x, y, _z), result);
        return result;
    }

    /**
     * This method returns the alpha significance cutoff value used in the independence test.
     *
     * @return the alpha significance cutoff value
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the alpha significance cutoff value.
     *
     * @param alpha The alpha significance cutoff value.
     * @throws IllegalArgumentException if the alpha value is outside the range [0, 1]
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables used in this method.
     *
     * @return The list of variables.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines whether the z nodes determine the x node.
     *
     * @param z The list of nodes to condition on.
     * @param x The node to test determination for.
     * @return True if node x is dependent on nodes z, False otherwise.
     * @throws UnsupportedOperationException Always throws this exception.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the concatenated data.
     *
     * @return This data
     */
    public DataSet getData() {
        return DataTransforms.concatenate(this.dataSets);
    }

    /**
     * Returns the covariance matrix for the data sets.
     *
     * @return The covariance matrix of the standardized data sets.
     */
    @Override
    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<>();

        for (DataSet d : this.dataSets) {
            _dataSets.add(DataTransforms.standardizeData(d));
        }

        return new CovarianceMatrix(DataTransforms.concatenate(_dataSets));
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representing the object.
     */
    public String toString() {
        return "Fisher Z, Concatenating Residuals";
    }

    /**
     * Return True if verbose output should be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose output flag.
     *
     * @param verbose Whether verbose output should be printed or not.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the residuals for a given node and list of parents.
     *
     * @param node    The node for which residuals are calculated.
     * @param parents The list of parent nodes for the calculation.
     * @return The array of residuals.
     */
    private double[] residuals(Node node, List<Node> parents) {
        List<Double> _residuals = new ArrayList<>();

        Node target = this.dataSets.get(0).getVariable(node.getName());

        List<Node> regressors = new ArrayList<>();

        for (Node _regressor : parents) {
            Node variable = this.dataSets.get(0).getVariable(_regressor.getName());
            regressors.add(variable);
        }


        for (int m = 0; m < this.dataSets.size(); m++) {
            RegressionResult result = this.regressions.get(m).regress(target, regressors);
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


    /**
     * Returns the variable with the given name from the list of variables.
     *
     * @param variables The list of variables to search in.
     * @param name      The name of the variable to find.
     * @return The variable with the given name, or null if it is not found.
     */
    private Node getVariable(List<Node> variables, String name) {
        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

}



