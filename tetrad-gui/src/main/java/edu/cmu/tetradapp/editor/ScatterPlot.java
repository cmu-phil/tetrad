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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;

import java.awt.geom.Point2D;
import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * This is the scatterplot model class holding the necessary information to create a scatterplot. It uses Point2D to
 * hold the pair of values need to create the scatterplot.
 *
 * @author Adrian Tang
 * @author josephramsey
 */
public class ScatterPlot {
    private final String x;
    private final String y;
    private final boolean includeLine;
    private final DataSet dataSet;
    private final Map<Node, double[]> continuousIntervals;
    private final Map<Node, Integer> discreteValues;
    private final Node _x;
    private final Node _y;
    private JitterStyle jitterStyle = JitterStyle.None;

    /**
     * Constructor.
     *
     * @param includeLine whether to include the regression line in the plot.
     * @param x           y-axis variable name.
     * @param y           x-axis variable name.
     */
    public ScatterPlot(DataSet dataSet, boolean includeLine, String x, String y) {
        this.dataSet = dataSet;
        this.x = x;
        this.y = y;
        _x = this.dataSet.getVariable(this.x);
        _y = this.dataSet.getVariable(this.y);
        this.includeLine = includeLine;
        this.continuousIntervals = new HashMap<>();
        this.discreteValues = new HashMap<>();
    }

    public void setJitterStyle(JitterStyle jitterStyle) {
        this.jitterStyle = jitterStyle;
    }

    private RegressionResult getRegressionResult() {
        List<Node> regressors = new ArrayList<>();
        regressors.add(_x);
        RegressionDataset regression = new RegressionDataset(this.dataSet);
        final List<Integer> conditionedRows = getConditionedRows();
        final int[] _conditionedRows = new int[conditionedRows.size()];
        for (int i = 0; i < conditionedRows.size(); i++) _conditionedRows[i] = conditionedRows.get(i);
        regression.setRows(_conditionedRows);
        return regression.regress(_y, regressors);
    }

    public double getCorrelationCoeff() {
        DataSet dataSet = getDataSet();
        Matrix data = dataSet.getDoubleData();

        int _x = dataSet.getColumn(dataSet.getVariable(this.x));
        int _y = dataSet.getColumn(dataSet.getVariable(this.y));

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
        } else {
            pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
        }

        return pValue;
    }

    private double fisherz(double r) {
        return 0.5 * FastMath.sqrt(getSampleSize() - 3.0) * (FastMath.log(1.0 + r) - FastMath.log(1.0 - r));
    }

    /**
     * @return the minimum x-axis value from the set of sample values.
     */
    public double getXmin() {
        double min = Double.POSITIVE_INFINITY;
        Vector<Point2D.Double> cleanedSampleValues = getSievedValues();
        for (Point2D.Double cleanedSampleValue : cleanedSampleValues) {
            min = FastMath.min(min, cleanedSampleValue.getX());
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
            min = FastMath.min(min, cleanedSampleValue.getY());
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
            max = FastMath.max(max, cleanedSampleValue.getX());
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
            max = FastMath.max(max, cleanedSampleValue.getY());
        }
        return max;
    }

    /**
     * Seives through the sample values and grabs only the values for the response and predictor variables.
     *
     * @return a vector containing the filtered values.
     */
    public Vector<Point2D.Double> getSievedValues() {
        return pairs(this.x, this.y);
    }

    /**
     * @return size of the sample.
     */
    private int getSampleSize() {
        return getSievedValues().size();
    }

    /**
     * @return the name of the predictor variable.
     */
    public String getXvar() {
        return this.x;
    }

    /**
     * @return the name of the response variable.
     */
    public String getYvar() {
        return this.y;
    }

    /**
     * @return whether to include the regression line.
     */
    public boolean isIncludeLine() {
        return this.includeLine;
    }

    /**
     * Calculates the regression coefficient for the variables return a regression coefficient.
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
        return this.dataSet;
    }

    /**
     * Adds a continuous conditioning variables, conditioning on a range of values.
     *
     * @param variable The name of the variable in the data set.
     * @param low      The low end of the conditioning range.
     * @param high     The high end of the conditioning range.
     */
    public void addConditioningVariable(String variable, double low, double high) {
        if (!(low < high)) throw new IllegalArgumentException("Low must be less than high: " + low + " >= " + high);

        Node node = this.dataSet.getVariable(variable);
        if (!(node instanceof ContinuousVariable)) throw new IllegalArgumentException("Variable must be continuous.");
        if (this.continuousIntervals.containsKey(node))
            throw new IllegalArgumentException("Please remove conditioning variable first.");

        this.continuousIntervals.put(node, new double[]{low, high});
    }

    /**
     * Adds a discrete conditioning variable, conditioning on a particular value.
     *
     * @param variable The name of the variable in the data set.
     * @param value    The value to condition on.
     */
    public void addConditioningVariable(String variable, int value) {
        Node node = this.dataSet.getVariable(variable);
//        if (node == this.target) throw new IllegalArgumentException("Conditioning node may not be the target.");
        if (!(node instanceof DiscreteVariable)) throw new IllegalArgumentException("Variable must be discrete.");
        this.discreteValues.put(node, value);
    }

    private List<Double> getUnconditionedDataContinuous(String target) {
        int index = this.dataSet.getColumn(this.dataSet.getVariable(target));

        List<Double> _data = new ArrayList<>();

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            _data.add(this.dataSet.getDouble(i, index));
        }

        return _data;
    }

    //======================================PRIVATE METHODS=======================================//

    private List<Double> getConditionedDataContinuous(String target) {
        if (this.continuousIntervals == null) return getUnconditionedDataContinuous(target);

        List<Integer> rows = getConditionedRows();

        int index = this.dataSet.getColumn(this.dataSet.getVariable(target));

        List<Double> _data = new ArrayList<>();

        for (Integer row : rows) {
            _data.add(this.dataSet.getDouble(row, index));
        }

        return _data;
    }

    // Returns the rows in the data that satisfy the conditioning constraints.
    private List<Integer> getConditionedRows() {
        List<Integer> rows = new ArrayList<>();

        I:
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            for (Node node : this.continuousIntervals.keySet()) {
                double[] range = this.continuousIntervals.get(node);
                int index = this.dataSet.getColumn(node);
                double value = this.dataSet.getDouble(i, index);
                if (!(value >= range[0] && value <= range[1])) {
                    continue I;
                }
            }

            for (Node node : this.discreteValues.keySet()) {
                int value = this.discreteValues.get(node);
                int index = this.dataSet.getColumn(node);
                int _value = this.dataSet.getInt(i, index);
                if (!(value == _value)) {
                    continue I;
                }
            }

            rows.add(i);
        }

        return rows;
    }

    private Vector<Point2D.Double> pairs(String x, String y) {
        Point2D.Double pt;
        Vector<Point2D.Double> cleanedVals = new Vector<>();

        List<Double> _x = getConditionedDataContinuous(x);
        List<Double> _y = getConditionedDataContinuous(y);

        double spreadx = getRange(_x);
        double spready = getRange(_y);

        for (int row = 0; row < _x.size(); row++) {
            pt = new Point2D.Double();
            double x1 = _x.get(row);
            double y1 = _y.get(row);

            double v = 0.03;

            if (jitterStyle == JitterStyle.Gaussian) {
                x1 += RandomUtil.getInstance().nextNormal(0, spreadx * v);
            } else if (jitterStyle == JitterStyle.Uniform) {
                x1 += RandomUtil.getInstance().nextUniform(-2 * spreadx * v, 2 * spreadx * v);
            }

            if (jitterStyle == JitterStyle.Gaussian) {
                y1 += RandomUtil.getInstance().nextNormal(0, spready * v);
            } else if (jitterStyle == JitterStyle.Uniform) {
                y1 += RandomUtil.getInstance().nextUniform(-2 * spready * v, 2 * spready * v);
            }

            pt.setLocation(x1, y1);
            cleanedVals.add(pt);
        }

        return cleanedVals;
    }

    private double getRange(List<Double> x) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (Double d : x) {
            if (d < min) min = d;
            if (d > max) max = d;
        }

        return max - min;
    }

    public enum JitterStyle {None, Gaussian, Uniform}
}



