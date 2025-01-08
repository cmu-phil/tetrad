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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TextTable;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Contains some descriptive stats.
 *
 * @author Michael Freenor
 */
class DescriptiveStats {

    /**
     * Constructs a readable table of normality test results
     *
     * @param dataSet               a {@link edu.cmu.tetrad.data.DataSet} object
     * @param variable              a {@link edu.cmu.tetrad.graph.Node} object
     * @param precomputeCovariances a boolean
     * @return a {@link java.lang.String} object
     */
    public static String generateDescriptiveStats(DataSet dataSet, Node variable,
                                                  boolean precomputeCovariances) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        int col = dataSet.getColumnIndex(variable);

        double[] data = new double[dataSet.getNumRows()];
        boolean continuous = false;

        if (variable instanceof ContinuousVariable) {
            continuous = true;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                data[i] = dataSet.getDouble(i, col);
            }
        } else {
            try {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    DiscreteVariable var = (DiscreteVariable) variable;
                    String category = var.getCategory(dataSet.getInt(i, col));
                    int value = Integer.parseInt(category);
                    data[i] = value;
                }
            } catch (NumberFormatException e) {
                return "Not a numerical discrete column.";
            }
        }

        int numVars = dataSet.getNumRows();

        StringBuilder b = new StringBuilder();

        b.append("Descriptive Statistics for: ").append(variable.getName()).append("\n\n");

        double[] normalValues = DescriptiveStats.normalParams(data);
        TextTable table;

        int numRows = continuous ? 13 : 9;
        table = new TextTable(numVars, numRows);

        int rowindex = 0;

        table.setToken(rowindex, 0, "Sample Size:");
        table.setToken(rowindex++, 1, String.valueOf(dataSet.getNumRows()));

        table.setToken(rowindex, 0, "Mean:");
        table.setToken(rowindex++, 1, nf.format(normalValues[0]));

        table.setToken(rowindex, 0, "Standard Deviation:");
        table.setToken(rowindex++, 1, nf.format(normalValues[1]));

        table.setToken(rowindex, 0, "Variance:");
        table.setToken(rowindex++, 1, nf.format(normalValues[2]));

        table.setToken(rowindex, 0, "Skewness:");
        table.setToken(rowindex++, 1, nf.format(StatUtils.skewness(data)));

        table.setToken(rowindex, 0, "Kurtosis:");
        table.setToken(rowindex++, 1, nf.format(StatUtils.kurtosis(data)));

        if (continuous) {
            double[] median = DescriptiveStats.median(data);

            table.setToken(rowindex, 0, "SE Mean:");
            table.setToken(rowindex++, 1, nf.format(DescriptiveStats.standardErrorMean(normalValues[1], dataSet.getNumRows())));

            table.setToken(rowindex, 0, "Median:");
            table.setToken(rowindex++, 1, nf.format(median[0]));

            table.setToken(rowindex, 0, "Minimum:");
            table.setToken(rowindex++, 1, nf.format(median[1]));

            table.setToken(rowindex, 0, "Maximum:");
            table.setToken(rowindex++, 1, nf.format(median[2]));
        }

        table.setToken(rowindex, 0, "Constant Columns:");
        List<Node> constantColumns = DataTransforms.getConstantColumns(dataSet);
        table.setToken(rowindex++, 1, constantColumns.isEmpty() ? "None" : constantColumns.toString());

        table.setToken(rowindex, 0, "Example Nonsingular (2 - 3 vars):");

//        CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
        ICovarianceMatrix covarianceMatrix = SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances);
        List<Node> exampleNonsingular = DataUtils.getExampleNonsingular(covarianceMatrix, 3);
        table.setToken(rowindex, 1, exampleNonsingular == null ? "None" : exampleNonsingular.toString());

        b.append(table);

        return b.toString();
    }

    /*
        Returns the median in index 0, but also returns the min and max in 1 and 2 respectively.
     */

    /**
     * <p>median.</p>
     *
     * @param data an array of  objects
     * @return an array of  objects
     */
    public static double[] median(double[] data) {
        Arrays.sort(data);

        double[] result = new double[3];

        result[1] = data[0];
        result[2] = data[data.length - 1];

        if (data.length % 2 == 1) //dataset is odd, finding middle value is easy
        {
            result[0] = data[data.length / 2];
        } else {
            //average the two middle values
            double firstValue = data[data.length / 2];
            double secondValue = data[data.length / 2 - 1];
            result[0] = (firstValue + secondValue) / 2;
        }
        return result;
    }

    /**
     * <p>standardErrorMean.</p>
     *
     * @param stdDev     a double
     * @param sampleSize a double
     * @return a double
     */
    public static double standardErrorMean(double stdDev, double sampleSize) {
        return stdDev / (FastMath.sqrt(sampleSize));
    }

    /**
     * Given some variable, returns the mean, standard deviation, and variance.
     *
     * @param data an array of  objects
     * @return [0] -&gt; mean, [1] -&gt; standard deviation, [2] -&gt; variance
     */
    public static double[] normalParams(double[] data) {
        double mean = 0.0;
        double sd = 0.0;

        //calculate the mean
        for (double datum : data) {
            mean += datum;
        }

        mean /= data.length;

        //calculate the standard deviation
        for (double datum : data) {
            sd += (datum - mean) * (datum - mean);
        }

        sd /= data.length - 1.0;

        double[] result = new double[3];
        result[2] = sd; //this is still the variance at this point
        sd = FastMath.sqrt(sd);

        result[0] = mean;
        result[1] = sd;

        return result;
    }
}



