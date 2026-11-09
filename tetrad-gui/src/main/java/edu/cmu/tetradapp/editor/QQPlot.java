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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.NormalDistribution;
import edu.cmu.tetrad.util.TMath;

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
     * The sorted nonmissing sample values for the selected variable, paired index-for-index with the comparison
     * quantiles.
     */
    private double[] sampleVariable;

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
     *
     * @param dataSet      a {@link edu.cmu.tetrad.data.DataSet} object
     * @param selectedNode a {@link edu.cmu.tetrad.graph.Node} object
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

        this.dataSet = dataSet;
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
     * <p>getMaxSample.</p>
     *
     * @return the max sample value.
     */
    public double getMaxSample() {
        return this.maxData;
    }

    /**
     * <p>getMinSample.</p>
     *
     * @return the min sample value.
     */
    public double getMinSample() {
        return this.minData;
    }

    /**
     * <p>getMinIdeal.</p>
     *
     * @return the min comparison value.
     */
    public double getMinIdeal() {
        return this.minComparison;
    }

    /**
     * <p>Getter for the field <code>selectedVariable</code>.</p>
     *
     * @return the node that has been selected.
     */
    public Node getSelectedVariable() {
        return this.selectedVariable;
    }

    /**
     * <p>Setter for the field <code>selectedVariable</code>.</p>
     *
     * @param c a {@link edu.cmu.tetrad.data.ContinuousVariable} object
     */
    public void setSelectedVariable(ContinuousVariable c) {
        this.selectedVariable = c;
    }

    /**
     * <p>Getter for the field <code>comparisonVariable</code>.</p>
     *
     * @return an array of  objects
     */
    public double[] getComparisonVariable() {
        return this.comparisonVariable;
    }

    /**
     * <p>Getter for the field <code>sampleVariable</code>.</p>
     *
     * @return the sorted nonmissing sample values, paired index-for-index with the comparison quantiles.
     */
    public double[] getSampleVariable() {
        return this.sampleVariable;
    }

    //============================ Private Methods =======================//

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Builds the q-q plot data: the sorted nonmissing sample values and, for each, the corresponding quantile of a
     * Normal distribution with the sample's mean and standard deviation. Missing (NaN) and infinite values are
     * excluded; the i'th sorted sample value is paired with the (i + 1) / (m + 1) quantile, where m is the number of
     * nonmissing values.
     */
    private void buildQQPlotData(Node selectedNode) {
        int columnIndex = this.dataSet.getColumnIndex(selectedNode);

        //the only case in which this should be -1 is if there's no selected variable yet
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
                JOptionPane.showMessageDialog(new JFrame(), "You need at least one continuous variable for a q-q plot!");
                throw new IllegalArgumentException("You need at least one continuous variable for a q-q plot!");
            }
        }

        // Extract the nonmissing, finite values.
        int numRows = this.dataSet.getNumRows();
        double[] values = new double[numRows];
        int m = 0;

        for (int i = 0; i < numRows; i++) {
            double value = this.dataSet.getDouble(i, columnIndex);
            if (Double.isFinite(value)) {
                values[m++] = value;
            }
        }

        if (m == 0) {
            JOptionPane.showMessageDialog(new JFrame(),
                    "The variable " + this.selectedVariable.getName() + " has no nonmissing values, so a q-q plot cannot be constructed for it.");
            throw new IllegalArgumentException("No nonmissing values for variable " + this.selectedVariable.getName());
        }

        this.sampleVariable = new double[m];
        System.arraycopy(values, 0, this.sampleVariable, 0, m);
        java.util.Arrays.sort(this.sampleVariable);

        this.minData = this.sampleVariable[0];
        this.maxData = this.sampleVariable[m - 1];

        // Mean and standard deviation over the nonmissing values only.
        double mean = 0.0;
        for (int i = 0; i < m; i++) mean += this.sampleVariable[i];
        mean /= m;

        double sd = 0.0;
        for (int i = 0; i < m; i++) {
            double dev = this.sampleVariable[i] - mean;
            sd += dev * dev;
        }
        sd = m > 1 ? TMath.sqrt(sd / (m - 1.0)) : 1.0;
        if (!(sd > 0.0)) sd = 1.0;

        NormalDistribution comparison = new NormalDistribution(mean, sd);

        // Theoretical quantiles, paired index-for-index with the sorted sample.
        this.comparisonVariable = new double[m];

        for (int i = 0; i < m; i++) {
            this.comparisonVariable[i] = comparison.inverseCumulativeProbability((i + 1) / (m + 1.0));
        }

        this.minComparison = this.comparisonVariable[0];
        this.maxComparison = this.comparisonVariable[m - 1];
    }
}




