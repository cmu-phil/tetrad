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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;

import java.awt.geom.Point2D;
import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.log;

/**
 * This is the scatterplot model class holding the necessary information to
 * create a scatterplot. It uses Point2D to hold the pair of values need to
 * create the scatterplot.
 *
 * @author Adrian Tang
 * @author Joseph Ramsey
 */
public class ScatterPlot {
    private String x;
    private String y;
    private final boolean includeLine;
    private final DataSet dataSet;
    private Vector<Point2D.Double> pairs;
    private Map<Node, double[]> continuousIntervals;

    /**
     * Constructor.
     *
     * @param includeLine whether or not to include the regression line in the
     *                    plot.
     * @param x           y-axis variable name.
     * @param y           x-axis variable name.
     */
    public ScatterPlot(
            DataSet dataSet,
            boolean includeLine,
            String x,
            String y) {
        this.dataSet = dataSet;
        this.x = x;
        this.y = y;
        this.includeLine = includeLine;
        this.continuousIntervals = new HashMap<Node, double[]>();
    }

    private RegressionResult getRegressionResult() {
        List<Node> regressors = new ArrayList<Node>();
        regressors.add(dataSet.getVariable(x));
        Node target = dataSet.getVariable(y);
        Regression regression = new RegressionDataset(dataSet);
        RegressionResult result = regression.regress(target, regressors);
        System.out.println(result);
        return result;
    }

    public double getCorrelationCoeff() {
        DataSet dataSet = getDataSet();
        TetradMatrix data = dataSet.getDoubleData();

        int _x = dataSet.getColumn(dataSet.getVariable(x));
        int _y = dataSet.getColumn(dataSet.getVariable(y));

        double[] xdata = data.getColumn(_x).toArray();
        double[] ydata = data.getColumn(_y).toArray();

        double correlation = StatUtils.correlation(xdata, ydata);

        if (correlation > 1) correlation = 1;
        else if (correlation < -1) correlation = -1;

        return correlation;
    }

    /**
     * @return the p-value of the correlation coefficient statistics.
     */
    public double getCorrelationPValue() {
        double r = getCorrelationCoeff();
        double fisherZ = fisherz(r);
        double pValue;

        if (Double.isInfinite(fisherZ)) {
            pValue = 0;
        }
        else {
            pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
        }

        return pValue;
    }

    public double fisherz(double r) {
        return 0.5 * Math.sqrt(getSampleSize() - 3.0) * (log(1.0 + r) - log(1.0 - r));
    }

    /**
     * @return the minimum x-axis value from the set of sample values.
     */
    public double getXmin() {
        double min = Double.POSITIVE_INFINITY;
        Vector<Point2D.Double> cleanedSampleValues = getSievedValues();
        for (Point2D.Double cleanedSampleValue : cleanedSampleValues) {
            min = Math.min(min, cleanedSampleValue.getX());
        }
        return min;
    }

    /**
     * @return the minimum y-axis value from the set of sample values.
     */
    public double getYmin() {
        double min = Double.POSITIVE_INFINITY;
        Vector<Point2D.Double> cleanedSampleValues = getSievedValues();
        for (Point2D.Double cleanedSampleValue : cleanedSampleValues) {
            min = Math.min(min, cleanedSampleValue.getY());
        }
        return min;
    }

    /**
     * @return the maximum x-axis value from the set of sample values.
     */
    public double getXmax() {
        double max = Double.NEGATIVE_INFINITY;
        Vector<Point2D.Double> cleanedSampleValues = getSievedValues();
        for (Point2D.Double cleanedSampleValue : cleanedSampleValues) {
            max = Math.max(max, cleanedSampleValue.getX());
        }
        return max;
    }

    /**
     * @return the maximum y-axis value from the set of sample values.
     */
    public double getYmax() {
        double max = Double.NEGATIVE_INFINITY;
        Vector<Point2D.Double> cleanedSampleValues = getSievedValues();
        for (Point2D.Double cleanedSampleValue : cleanedSampleValues) {
            max = Math.max(max, cleanedSampleValue.getY());
        }
        return max;
    }

    /**
     * Seives through the sample values and grabs only the values for the
     * response and predictor variables.
     *
     * @return a vector containing the filtered values.
     */
    public Vector<Point2D.Double> getSievedValues() {
        pairs = pairs(x, y);
        return pairs;
    }

    /**
     * @return size of the sample.
     */
    public int getSampleSize() {
        return getSievedValues().size();
    }

    /**
     * @return the name of the predictor variable.
     */
    public String getXvar() {
        return x;
    }

    /**
     * @return the name of the response variable.
     */
    public String getYvar() {
        return y;
    }

    /**
     * @return whether or not to include the regression line.
     */
    public boolean isIncludeLine() {
        return includeLine;
    }

    /**
     * Calculates the regression coefficient for the variables
     * return a regression coeff
     */
    public double getRegressionCoeff() {
        return getRegressionResult().getCoef()[1];
    }

    /**
     * @return the zero intercept of the regression equation.
     */
    public double getRegressionIntercept() {
        return getRegressionResult().getCoef()[0];
    }

    public DataSet getDataSet() {
        return dataSet;
    }


    //========================================PUBLIC METHODS=================================//

    /**
     * Adds a continuous conditioning variables, conditioning on a range of values.
     *
     * @param variable The name of the variable in the data set.
     * @param low      The low end of the conditioning range.
     * @param high     The high end of the conditioning range.
     */
    public void addConditioningVariable(String variable, double low, double high) {
        if (!(low < high)) throw new IllegalArgumentException("Low must be less than high: " + low + " >= " + high);

        Node node = dataSet.getVariable(variable);
        if (!(node instanceof ContinuousVariable)) throw new IllegalArgumentException("Variable must be continuous.");
        if (continuousIntervals.containsKey(node))
            throw new IllegalArgumentException("Please remove conditioning variable first.");

        continuousIntervals.put(node, new double[]{low, high});
    }

    /**
     * Removes a conditioning variable.
     *
     * @param variable The name of the conditioning variable to remove.
     */
    public void removeConditioningVariable(String variable) {
        Node node = dataSet.getVariable(variable);
        if (!(continuousIntervals.containsKey(node))) {
            throw new IllegalArgumentException("Not a conditioning node: " + variable);
        }
        continuousIntervals.remove(node);
    }

    public void removeConditioningVariables() {
        this.continuousIntervals = new HashMap<Node, double[]>();
    }

    /**
     * For a continuous target, returns the number of values histogrammed. This may be
     * less than the sample size of the data set because of conditioning.
     */
    public int getN(String target) {
        List<Double> conditionedDataContinuous = getConditionedDataContinuous(target);
        return conditionedDataContinuous.size();
    }

    /**
     * A convenience method to return the data for a particular named continuous
     * variable.
     *
     * @param variable The name of the variable.
     */
    public double[] getContinuousData(String variable) {
        int index = dataSet.getColumn(dataSet.getVariable(variable));
        List<Double> _data = new ArrayList<Double>();

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            _data.add(dataSet.getDouble(i, index));
        }

        return asDoubleArray(_data);
    }

    //======================================PRIVATE METHODS=======================================//

    private double[] asDoubleArray(List<Double> data) {
        double[] _data = new double[data.size()];
        for (int i = 0; i < data.size(); i++) _data[i] = data.get(i);
        return _data;
    }

    private List<Double> getUnconditionedDataContinuous(String target) {
        int index = dataSet.getColumn(dataSet.getVariable(target));

        List<Double> _data = new ArrayList<Double>();

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            _data.add(dataSet.getDouble(i, index));
        }

        return _data;
    }

    private List<Double> getConditionedDataContinuous(String target) {
        if (continuousIntervals == null) return getUnconditionedDataContinuous(target);

        List<Integer> rows = getConditionedRows();

        int index = dataSet.getColumn(dataSet.getVariable(target));

        List<Double> _data = new ArrayList<Double>();

        for (Integer row : rows) {
            _data.add(dataSet.getDouble(row, index));
        }

        return _data;
    }

    // Returns the rows in the data that satisfy the conditioning constraints.
    private List<Integer> getConditionedRows() {
        List<Integer> rows = new ArrayList<Integer>();

        I:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (Node node : continuousIntervals.keySet()) {
                double[] range = continuousIntervals.get(node);
                int index = dataSet.getColumn(node);
                double value = dataSet.getDouble(i, index);
                if (!(value > range[0] && value < range[1])) {
                    continue I;
                }
            }

            rows.add(i);
        }

        return rows;
    }

    private Vector<Point2D.Double> pairs(String x, String y) {
        Point2D.Double pt;
        Vector<Point2D.Double> cleanedVals = new Vector<Point2D.Double>();

        List<Double> _x = getConditionedDataContinuous(x);
        List<Double> _y = getConditionedDataContinuous(y);

        for (int row = 0; row < _x.size(); row++) {
            pt = new Point2D.Double();
            pt.setLocation(_x.get(row), _y.get(row));
            cleanedVals.add(pt);
        }

        return cleanedVals;
    }

}



