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

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;

import java.text.NumberFormat;
import java.util.Arrays;

/**
 * Contains some descriptive stats.
 *
 * @author Michael Freenor
 */
public class DescriptiveStats {

    /**
     * Constructs a readable table of normality test results
     *
     */

    public static String generateDescriptiveStats(DataSet dataSet, Node variable) {
        NumberFormat nf =  NumberFormatUtil.getInstance().getNumberFormat();

        int col = dataSet.getColumn(variable);

        double[] data = new double[dataSet.getNumRows()];
        boolean continuous = false;

        if (variable instanceof ContinuousVariable) {
            continuous = true;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                data[i] = dataSet.getDouble(i, col);
            };
        }
        else {
            try {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    DiscreteVariable var = (DiscreteVariable) variable;
                    String category = var.getCategory(dataSet.getInt(i, col));
                    int value = Integer.parseInt(category);
                    data[i] = value;
                }
                ;
            } catch (NumberFormatException e) {
                return "Not a numerical discrete column.";
            }
        }

        String result = "Descriptive Statistics for: " + variable.getName() + "\n";
        result +=       "------------------------------------\n\n";

        double[] normalValues = normalParams(data);

        result += "Sample Size:\t\t" + dataSet.getNumRows() + "\n";
        result += "Mean:\t\t\t" + nf.format(normalValues[0]) + "\nStandard Deviation:\t" + nf.format(normalValues[1]) + "\nVariance:\t\t" + nf.format(normalValues[2]) + "\n";

        result += "Skewness:\t\t" + nf.format(StatUtils.skewness(data)) + "\n";
        result += "Excess Kurtosis:\t" + nf.format(StatUtils.kurtosis(data)) + "\n";

        if (continuous) {
            result += "SE Mean:\t\t" + nf.format(standardErrorMean(normalValues[1], dataSet.getNumRows())) + "\n";
        }

        double[] median = median(data);

        result += "Median:\t\t\t" + nf.format(median[0]) + "\n";

        if (continuous) {
            result += "Minimum Value:\t\t" + nf.format(median[1]) + "\nMaximum Value:\t\t" + nf.format(median[2]);
        }

        return result;
    }

    /*
        Returns the median in index 0, but also returns the min and max in 1 and 2 respectively.
     */
    public static double[] median(double[] data)
    {
        Arrays.sort(data);

        double result[] = new double[3];

        result[1] = data[0];
        result[2] = data[data.length - 1];

        if(data.length % 2 == 1) //dataset is odd, finding middle value is easy
        {
            result[0] = data[data.length / 2];
            return result;
        }
        else
        {
            //average the two middle values
            double firstValue = data[data.length / 2];
            double secondValue = data[data.length / 2 - 1];
            result[0] = (firstValue + secondValue) / 2;
            return result;
        }
    }

    /**
     * Generates an ideal Normal distribution for some variable.
     *
     * @return Ideal Normal distribution for a variable.
     */

    public static Normal getNormal(double[] data)
    {
        double[] paramsForNormal = normalParams(data);
        double mean = paramsForNormal[0];
        double sd = paramsForNormal[1];

        return new Normal(mean, sd, new MersenneTwister());
    }

    public static double standardErrorMean(double stdDev, double sampleSize)
    {
        return stdDev / (Math.sqrt(sampleSize));
    }

    /**
     * Given some variable, returns the mean, standard deviation, and variance.
     *
     * @return [0] -&gt; mean, [1] -&gt; standard deviation, [2] -&gt; variance
     */

    public static double[] normalParams(double[] data)
    {
        double mean = 0.0;
        double sd = 0.0;

        //calculate the mean
        for (int i = 0; i < data.length; i++)
        {
            mean += data[i];
        }

        mean /= data.length;

        //calculate the standard deviation
        for (int i = 0; i < data.length; i++)
        {
            sd += (data[i] - mean) * (data[i] - mean);
        }

        sd /= data.length - 1.0;

        double result[] = new double[3];
        result[2] = sd; //this is still the variance at this point
        sd = Math.sqrt(sd);

        result[0] = mean;
        result[1] = sd;

        return result;
    }
}



