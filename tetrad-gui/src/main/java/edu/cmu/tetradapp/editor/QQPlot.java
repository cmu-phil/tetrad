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

import cern.jet.random.engine.MersenneTwister;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;

/**
 * Immutable object that wraps a dataset and gives a q-q plot.
 *
 * @author Michael Freenor
 */
class QQPlot {


    /**
     * The complete data set
     */
    private final DataSet dataSet;

    /**
     * The variable that we are showing a q-q plot for.
     */
    private ContinuousVariable selectedVariable;

    /**
     * The variable that we store the comparison variable in
     */
    private double[] comparisonVariable;

    /**
     * The min value in the comparison distribution
     */

    private double minComparison;

    /**
     * The max value in the comparison distribution
     */

    private double maxComparison;

    /**
     * The min value in the sample
     */
    private double minData;


    /**
     * The max value in the sample
     */
    private double maxData;

    /**
     * Constructs the histogram given the dataset to wrap and the node that should be viewed.
     */
    public QQPlot(DataSet dataSet, Node selectedNode) {

        if (dataSet == null) {
            throw new NullPointerException("the given dataset must not be null");
        }
        if (dataSet.getNumColumns() == 0) {
            throw new IllegalArgumentException("The given dataset should not be empty");
        }

        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            if (dataSet.getVariable(i) instanceof ContinuousVariable) {
                break;
            }
            if (i == dataSet.getNumColumns() - 1) {
                JOptionPane.showMessageDialog(new JFrame(), "You must have at least one continuous variable to construct a q-q plot!");
                throw new IllegalArgumentException("You must have at least one continuous variable to construct a q-q plot!");
            }
        }

        this.dataSet = dataSet.copy();
        if (selectedNode == null && dataSet.getNumColumns() != 0) {
            int[] selected = dataSet.getSelectedIndices();
            assert selected != null;
        }

        try {
            this.selectedVariable = (ContinuousVariable) selectedNode;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(new JFrame(), "You cannot construct a q-q plot for a discrete variable!");
            throw new IllegalArgumentException("Only attempt to construct a q-q plot on a continuous variable!");
        }

        buildQQPlotData(this.selectedVariable);

    }

    //==================================== Public Methods ====================================//

    /**
     * @return the max sample value.
     */
    public double getMaxSample() {
        return this.maxData;
    }


    /**
     * @return the min sample value.
     */
    public double getMinSample() {
        return this.minData;
    }


    /**
     * @return the min comparison value.
     */
    public double getMinIdeal() {
        return this.minComparison;
    }

    /**
     * @return the node that has been selected.
     */
    public Node getSelectedVariable() {
        return this.selectedVariable;
    }

    public void setSelectedVariable(ContinuousVariable c) {
        this.selectedVariable = c;
    }

    public double[] getComparisonVariable() {
        return this.comparisonVariable;
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    //============================ Private Methods =======================//

    /**
     * Calculates the ideal quantiles values for the provided dataset.
     *
     * @param n    Normal distribution generated from the dataset.
     * @param data Dataset that n is generated from, and whose normality is in question.
     */
    private void calculateComparisonSet(cern.jet.random.Normal n, DataSet data) {
        this.comparisonVariable = new double[data.getNumRows()];

        for (int i = 0; i < data.getNumRows(); i++) {
            double valueAtQuantile = QQPlot.findQuantile((i + 1) / (data.getNumRows() + 1.0), this.minData, this.maxData, n, .0001, 0, 50);
            this.comparisonVariable[i] = valueAtQuantile;

            if (valueAtQuantile < this.minComparison) {
                this.minComparison = valueAtQuantile;
            }
            if (valueAtQuantile > this.maxComparison) {
                this.maxComparison = valueAtQuantile;
            }
        }
    }

    /**
     * @param quantile  Desired quantile you wish to find
     * @param low       The minimum of your dataset
     * @param high      The maximum of your dataset
     * @param n         Your normal distribution you wish to search among
     * @param precision The desired precision of your search (in quantiles)
     * @param count     Feed this zero -- ensures the stack doesn't fill up
     * @param searchCap Desired maximum number of searches -- too high and the stack might overflow!
     * @return an estimation of the point in a Normal distribution at a specific quantile.
     */
    private static double findQuantile(double quantile, double low, double high, cern.jet.random.Normal n, double precision, int count, int searchCap) {
        //System.out.println("Low: " + low + "High: " + high);
        double mid = low + ((high - low) / 2.);
        //System.out.println("Mid: " + mid);
        double cdfResult = n.cdf(mid);
        //System.out.println("CDF: " + cdfResult + " Abs value of difference: " + Math.abs(cdfResult - quantile) + " Count: " + count);
        if (Math.abs(cdfResult - quantile) < precision || count > searchCap) {
            //System.out.println("Found result: " + mid);
            return mid;
        } else {
            if (cdfResult > quantile) {
                //System.out.println("Searching lesser");
                return QQPlot.findQuantile(quantile, low, mid - precision, n, precision, count + 1, searchCap);
            } else {
                //System.out.println("Searching greater");
                return QQPlot.findQuantile(quantile, mid + precision, high, n, precision, count + 1, searchCap);
            }
        }
    }

    /**
     * Builds the q-q data if required, otherwise does nothing
     */
    private void buildQQPlotData(Node selectedNode) {
        int columnIndex = this.dataSet.getColumn(selectedNode);

        double mean = 0.0;
        double sd = 0.0;

        this.minData = 10000000000000.0;
        this.maxData = 0.0;

        this.minComparison = 1000000000000.0;
        this.maxComparison = 0.0;

        //the only case in which this should be -1 is if there's a continuous variable, but it's incomplete
        if (columnIndex == -1) {
            for (int i = 0; i < this.dataSet.getNumColumns(); i++) {
                //set selected variable if there is none
                if (this.dataSet.getVariable(i) instanceof ContinuousVariable) {
                    this.selectedVariable = (ContinuousVariable) this.dataSet.getVariable(i);
                    columnIndex = i;
                    break;
                }
            }
            if (columnIndex == -1) {
                JOptionPane.showMessageDialog(new JFrame(), "You need at least one complete continuous variable for a q-q plot!");
                throw new IllegalArgumentException("You need at least one complete continuous variable for a q-q plot!");
            }
        }

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            double value = this.dataSet.getDouble(i, columnIndex);

            if (Double.isNaN(value) || value == Double.NEGATIVE_INFINITY
                    || value == Double.POSITIVE_INFINITY) {
                continue;
            }

            mean += value;
            if (value < this.minData) this.minData = value;
            if (value > this.maxData) this.maxData = value;

        }

        //sort the dataset
        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            for (int k = i; k < this.dataSet.getNumRows(); k++) {
                double value1 = this.dataSet.getDouble(i, columnIndex);
                double value2 = this.dataSet.getDouble(k, columnIndex);

                if (Double.isNaN(value1) || value1 == Double.NEGATIVE_INFINITY
                        || value1 == Double.POSITIVE_INFINITY) {
                    continue;
                }

                if (Double.isNaN(value2) || value2 == Double.NEGATIVE_INFINITY
                        || value2 == Double.POSITIVE_INFINITY) {
                    continue;
                }

                if (value1 > value2) {
                    double temp = this.dataSet.getDouble(i, columnIndex);
                    this.dataSet.setDouble(i, columnIndex, value2);
                    this.dataSet.setDouble(k, columnIndex, temp);
                }
            }
        }

        if (mean == 0.0) mean = 1.0;
        else mean /= this.dataSet.getNumRows();

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            double value1 = this.dataSet.getDouble(i, columnIndex);
            double value2 = this.dataSet.getDouble(i, columnIndex);

            if (Double.isNaN(value1) || value1 == Double.NEGATIVE_INFINITY
                    || value1 == Double.POSITIVE_INFINITY) {
                continue;
            }

            if (Double.isNaN(value2) || value2 == Double.NEGATIVE_INFINITY
                    || value2 == Double.POSITIVE_INFINITY) {
                continue;
            }

            sd += (value1 - mean) * (value2 - mean);
        }

        if (sd == 0.0) {
            sd = 1.0;
        } else {
            sd /= this.dataSet.getNumRows() - 1.0;
            sd = Math.sqrt(sd);
        }

        //System.out.println("Mean: " + mean + " SD: " + sd + " Min: " + this.minData + " Max: " + this.maxData);

        cern.jet.random.Normal comparison = new cern.jet.random.Normal(mean, sd, new MersenneTwister());

        calculateComparisonSet(comparison, this.dataSet);

    }
}



