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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model for a conditional histogram for mixed continuous and discrete variables.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Histogram {
    private final DataSet dataSet;
    private final boolean removeZeroPointsPerPlot;
    private Node target;
    private int numBins = 10;
    private Map<Node, double[]> continuousIntervals;
    private Map<Node, Integer> discreteValues;

    /**
     * This histogram is for variables in a particular data set. These may be continuous or discrete.
     *
     * @param dataSet                 a {@link edu.cmu.tetrad.data.DataSet} object
     * @param target                  a {@link java.lang.String} object
     * @param removeZeroPointsPerPlot a boolean
     */
    public Histogram(DataSet dataSet, String target, boolean removeZeroPointsPerPlot) {
        if (dataSet.getVariables().size() < 1) {
            throw new IllegalArgumentException("Can't do histograms for an empty data sets.");
        }

        this.dataSet = dataSet;
        this.removeZeroPointsPerPlot = removeZeroPointsPerPlot;
        setTarget(target);
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

    /**
     * Removes a conditioning variable.
     *
     * @param variable The name of the conditioning variable to remove.
     */
    public void removeConditioningVariable(String variable) {
        Node node = this.dataSet.getVariable(variable);
//        if (node == this.target) throw new IllegalArgumentException("The target cannot be a conditioning node.");
        if (!(this.continuousIntervals.containsKey(node) || this.discreteValues.containsKey(node))) {
            throw new IllegalArgumentException("Not a conditioning node: " + variable);
        }
        this.continuousIntervals.remove(node);
        this.discreteValues.remove(node);
    }

    /**
     * For a continuous target, sets the number of bins for the histogram.
     *
     * @param numBins The number of bins.
     */
    public void setNumBins(int numBins) {
        if (this.target instanceof DiscreteVariable) {
            throw new IllegalArgumentException("Can't set number of bins for a discrete target.");
        }

        this.numBins = numBins;
    }

    /**
     * <p>getFrequencies.</p>
     *
     * @return the counts for the histogram, one count for each target, in an integer array.
     */
    public int[] getFrequencies() {
        if (this.target instanceof ContinuousVariable) {
            List<Double> _data = getConditionedDataContinuous();
            _data = removeZeroPointsPerPlot(_data);
            double[] breakpoints = getBreakpoints(_data, this.numBins);

            int[] counts = new int[this.numBins];

            for (Double d : _data) {
                boolean sorted = false;

                int h;

                for (h = 0; h < breakpoints.length; h++) {
                    if (breakpoints[h] > d) {
                        counts[h]++;
                        sorted = true;
                        break;
                    }
                }

                if (!sorted) {
                    counts[breakpoints.length]++;
                }
            }

            return counts;
        } else if (this.target instanceof DiscreteVariable _var) {
            List<Integer> _data = getConditionedDataDiscrete();

            int[] counts = new int[_var.getNumCategories()];

            for (Integer d : _data) {
                counts[d]++;
            }

            return counts;
        } else {
            throw new IllegalArgumentException("Unrecognized variable type.");
        }
    }

    private List<Double> removeZeroPointsPerPlot(List<Double> data) {
        List<Double> _data = new ArrayList<>();

        for (double d : data) {
            if (!removeZeroPointsPerPlot || d != 0) {
                _data.add(d);
            }
        }

        return _data;
    }

    /**
     * For a continuous target, returns the maximum value of the values histogrammed, for the unconditioned data.
     *
     * @return a double
     */
    public double getMax() {
        List<Double> conditionedDataContinuous = getUnconditionedDataContinuous();
        double[] d = asDoubleArray(conditionedDataContinuous);
        return StatUtils.max(d);
    }

    /**
     * For a continuous target, returns the minimum value of the values histogrammed, for the unconditioned data.
     *
     * @return a double
     */
    public double getMin() {
        List<Double> conditionedDataContinuous = getUnconditionedDataContinuous();
        double[] d = asDoubleArray(conditionedDataContinuous);
        return StatUtils.min(d);
    }

    /**
     * For a continuous target, returns the number of values histogrammed. This may be less than the sample size of the
     * data set because of conditioning.
     *
     * @return a int
     */
    public int getN() {
        List<Double> conditionedDataContinuous = getConditionedDataContinuous();
        return conditionedDataContinuous.size();
    }

    /**
     * A convenience method to return the data for a particular named continuous variable.
     *
     * @param variable The name of the variable.
     * @return an array of {@link double} objects
     */
    public double[] getContinuousData(String variable) {
        int index = this.dataSet.getColumn(this.dataSet.getVariable(variable));
        List<Double> _data = new ArrayList<>();

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            _data.add(this.dataSet.getDouble(i, index));
        }

        return asDoubleArray(_data);
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return the data set for this histogram.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>Getter for the field <code>target</code>.</p>
     *
     * @return the target node being histogrammed. Could be continuous or discrete.
     */
    public String getTarget() {
        return this.target.getName();
    }

    /**
     * Sets the target. Setting the target removes all conditioning variables and sets the number of bins to the default
     * (using Sturges' formula).
     *
     * @param target The name of the target in the data set.
     */
    private void setTarget(String target) {
        Node _target;

        if (target == null) {
            _target = this.dataSet.getVariable(0);
        } else {
            _target = this.dataSet.getVariable(target);
        }

        this.target = _target;
        this.continuousIntervals = new HashMap<>();
        this.discreteValues = new HashMap<>();
//        this.numBins = (int) ceil(log(this.dataSet.getNumRows()) / log(2) + 1);
    }

    /**
     * <p>getTargetNode.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getTargetNode() {
        return this.target;
    }

    private double[] getBreakpoints(List<Double> data, int numBins) {
        double[] _data = asDoubleArray(data);

        if (data.isEmpty()) throw new IllegalArgumentException("No data.");

        double max = StatUtils.max(_data);
        double min = StatUtils.min(_data);

        double interval = (max - min) / numBins;

        double[] breakpoints = new double[numBins - 1];

        for (int g = 0; g < numBins - 1; g++) {
            breakpoints[g] = min + (g + 1) * interval;
        }

        return breakpoints;
    }

    private double[] asDoubleArray(List<Double> data) {
        double[] _data = new double[data.size()];
        for (int i = 0; i < data.size(); i++) _data[i] = data.get(i);
        return _data;
    }

    private List<Double> getUnconditionedDataContinuous() {
        int index = this.dataSet.getColumn(this.target);

        List<Double> _data = new ArrayList<>();

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            _data.add(this.dataSet.getDouble(i, index));
        }

        return _data;
    }

    private List<Double> getConditionedDataContinuous() {
        List<Integer> rows = getConditionedRows();

        int index = this.dataSet.getColumn(this.target);

        List<Double> _data = new ArrayList<>();

        for (Integer row : rows) {
            _data.add(this.dataSet.getDouble(row, index));
        }

        return _data;
    }

    private List<Integer> getConditionedDataDiscrete() {
        List<Integer> rows = getConditionedRows();

        int index = this.dataSet.getColumn(this.target);

        List<Integer> _data = new ArrayList<>();

        for (Integer row : rows) {
            _data.add(this.dataSet.getInt(row, index));
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
}




