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
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Calculates independence from pooled residuals.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndTestFisherZPercentIndependent implements IndependenceTest {

    /**
     * The variables.
     */
    private final List<Node> variables;

    /**
     * The data sets.
     */
    private final List<DataSet> dataSets;

    /**
     * The rows.
     */
    private final int[] rows;

    /**
     * The data.
     */
    private final List<Matrix> data;

    /**
     * The ncov.
     */
    private final List<Matrix> ncov;

    /**
     * The alpha.
     */
    private final Map<Node, Integer> variablesMap;

    /**
     * The alpha.
     */
    private double alpha;

    /**
     * The percent.
     */
    private double percent = .75;

    /**
     * The fdr.
     */
    private boolean fdr = true;

    /**
     * whether to print verbose output
     */
    private boolean verbose;
    private int effectiveSampleSize;

    //==========================CONSTRUCTORS=============================//

    /**
     * Initializes an object of the class IndTestFisherZPercentIndependent.
     *
     * @param dataSets The list of data sets to be used for the independence test.
     * @param alpha    The significance level for the independence test. Must be between 0.0 and 1.0 (inclusive).
     * @throws IllegalArgumentException If alpha is not within the valid range.
     */
    public IndTestFisherZPercentIndependent(List<DataSet> dataSets, double alpha) {
        this.dataSets = dataSets;
        this.variables = dataSets.get(0).getVariables();

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        this.data = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataSet = DataTransforms.center(dataSet);
            Matrix _data = dataSet.getDoubleData();
            this.data.add(_data);
        }

        this.ncov = new ArrayList<>();
        for (Matrix d : this.data) this.ncov.add(d.transpose().times(d).scale(1.0 / d.getNumRows()));

        setAlpha(alpha);
        this.rows = new int[dataSets.get(0).getNumRows()];
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;

        this.variablesMap = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            this.variablesMap.put(this.variables.get(i), i);
        }
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Performs an independence test on a subset of variables.
     *
     * @param vars The sublist of variables to test for independence.
     * @return The result of the independence test.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks the independence between two nodes x and y given a set of conditioning nodes z.
     *
     * @param x  The first node.
     * @param y  The second node.
     * @param _z The set of conditioning nodes.
     * @return The result of the independence test.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        try {
            List<Node> z = new ArrayList<>(_z);
            Collections.sort(z);

            int[] all = new int[z.size() + 2];
            all[0] = this.variablesMap.get(x);
            all[1] = this.variablesMap.get(y);
            for (int i = 0; i < z.size(); i++) {
                all[i + 2] = this.variablesMap.get(z.get(i));
            }

            int sampleSize = this.data.get(0).getNumRows();
            List<Double> pValues = new ArrayList<>();

            for (Matrix matrix : this.ncov) {
                Matrix _ncov = matrix.view(all, all).mat();
                Matrix inv = _ncov.inverse();
                double r = -inv.get(0, 1) / sqrt(inv.get(0, 0) * inv.get(1, 1));

                double fisherZ = sqrt(sampleSize - z.size() - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
                double pValue;

                if (Double.isInfinite(fisherZ)) {
                    pValue = 0;
                } else {
                    pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
                }

                pValues.add(pValue);
            }

            double _cutoff = this.alpha;

            if (this.fdr) {
                _cutoff = StatUtils.fdrCutoff(this.alpha, pValues, false);
            }

            Collections.sort(pValues);
            int index = (int) round((1.0 - this.percent) * pValues.size());
            double pValue = pValues.get(index);

            if (Double.isNaN(pValue)) {
                throw new RuntimeException("NaN p-value encountered when testing " +
                                           LogUtilsSearch.independenceFact(x, y, _z));
            }

            boolean independent = pValue > _cutoff;

            if (this.verbose) {
                if (independent) {
                    TetradLogger.getInstance().log(
                            LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
                }
            }

            return new IndependenceResult(new IndependenceFact(x, y, _z), independent, pValue, getAlpha() - pValue);
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, _z));
        }
    }

    /**
     * Gets the getModel significance level.
     *
     * @return a double
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The significance level to set. Must be between 0.0 and 1.0 (inclusive).
     * @throws IllegalArgumentException if alpha is not within the valid range.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines the independence between a list of conditioning variables (z) and a target variable (x).
     *
     * @param z The list of conditioning variables.
     * @param x The target variable.
     * @return True if the target variable is independent of the conditioning variables, otherwise False.
     * @throws UnsupportedOperationException if the operation is not supported.
     */
    public boolean determines(List z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the data set from the method.
     *
     * @return The data set obtained.
     */
    public DataSet getData() {
        return DataTransforms.concatenate(this.dataSets);
    }

    /**
     * Retrieves the covariance matrix.
     *
     * @return The covariance matrix.
     */
    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<>();

        for (DataSet d : this.dataSets) {
            _dataSets.add(DataTransforms.standardizeData(d));
        }

        return new CovarianceMatrix(DataTransforms.concatenate(_dataSets));
    }

    /**
     * Retrieves the list of data sets.
     *
     * @return The list of data sets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return this.dataSets;
    }

    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.effectiveSampleSize = effectiveSampleSize;
    }

    /**
     * Retrieves the sample size of the data set.
     *
     * @return The sample size of the data set.
     */
    @Override
    public int getSampleSize() {
        return this.dataSets.get(0).getNumRows();
    }

    /**
     * Returns a string representation of this object.
     *
     * @return The string representation of this object.
     */
    public String toString() {
        return "Fisher Z, Percent Independent";
    }

    /**
     * Retrieves the array of row indices.
     *
     * @return The array of row indices represented by an int array.
     */
    public int[] getRows() {
        return this.rows;
    }

    /**
     * Returns the percentage value.
     *
     * @return The percentage value.
     */
    public double getPercent() {
        return this.percent;
    }

    /**
     * Sets the percentage value.
     *
     * @param percent The percentage value to set. Must be between 0.0 and 1.0 (inclusive).
     * @throws IllegalArgumentException if percent is not within the valid range.
     */
    public void setPercent(double percent) {
        if (percent < 0.0 || percent > 1.0) throw new IllegalArgumentException();
        this.percent = percent;
    }

    /**
     * Sets the value of the fdr field.
     *
     * @param fdr The new value of the fdr field.
     */
    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }

    /**
     * Returns the value of the verbose flag.
     *
     * @return True if verbose mode is enabled, False otherwise.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose flag.
     *
     * @param verbose True if verbose mode is enabled, False otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


