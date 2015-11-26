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
import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Variable;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains some normality tests.
 *
 * @author Michael Freenor
 */
public class NormalityTests {

    /**
     * Constructs a readable table of normality test results
     * 
     */

    public static String runNormalityTests(DataSet dataSet, ContinuousVariable variable) {
        NumberFormat nf =  NumberFormatUtil.getInstance().getNumberFormat();

        String result = "Normality Tests for: " + variable.getName() +" (sample size:" + dataSet.getNumRows() + ")";
        int lengthOfTitle = result.length();
        result += "\n";
        for (int i = 0; i < lengthOfTitle; i++)
        {
            result += "-";
        }
        result += "\n\nKolmogorov Smirnov:\n--------------------------------\n";
        double ksResults[] = kolmogorovSmirnov(dataSet, variable);
        double ksStat = Math.round((ksResults[0] * 10000000.0)) / 10000000.0;
        result += "K-S Statistic: " + ksStat + "\n\n";
        result += "Significance Levels:\t.20\t.15\t.10\t.05\t.01\nK-S Critical Values:";

        result += "\t" + nf.format(ksResults[0]) + "\t" + nf.format(ksResults[1]) + "\t" +
                nf.format(ksResults[2]) + "\t" + nf.format(ksResults[3]) + "\t" + nf.format(ksResults[4]) + "\n";

        boolean testResult = false;
        String pass = "FAIL";
        if (ksResults[0] < ksResults[1]) testResult = true;
        if (testResult) pass = "ACCEPT";
        else pass = "FAIL";
        result += "Test Result:\t\t" + pass;
        testResult = false;
        if (ksResults[0] < ksResults[2]) testResult = true;
        if (testResult) pass = "ACCEPT";
        else pass = "FAIL";
        result += "\t" + pass;
        testResult = false;
        if (ksResults[0] < ksResults[3]) testResult = true;
        if (testResult) pass = "ACCEPT";
        else pass = "FAIL";
        result += "\t" + pass;
        testResult = false;
        if (ksResults[0] < ksResults[4]) testResult = true;
        if (testResult) pass = "ACCEPT";
        else pass = "FAIL";
        result += "\t" + pass;
        testResult = false;
        if (ksResults[0] < ksResults[5]) testResult = true;
        if (testResult) pass = "ACCEPT";
        else pass = "FAIL";
        result += "\t" + pass;
        testResult = false;

        result += "\n\nH0 = " + variable + " is Normal.\n";
        result += "(Normal if ACCEPT.)\n";

        result += "\n\n";

        result += "Anderson Darling Test:\n";
        result += "---------------------\n";

        int column = dataSet.getVariables().indexOf(variable);
        double[] data = dataSet.getDoubleData().getColumn(column).toArray();
        AndersonDarlingTest andersonDarlingTest = new AndersonDarlingTest(data);
        result += "A^2 = " + nf.format(andersonDarlingTest.getASquared()) + "\n";
        result += "A^2* = " + nf.format(andersonDarlingTest.getASquaredStar()) + "\n";
        result += "p = " + nf.format(andersonDarlingTest.getP()) + "\n";

        result += "\nH0 = " + variable + " is Non-normal.";
        result += "\n(Normal if p > alpha.)\n";

        return result;
    }

    /**
     * Calculates the Kolmogorov-Smirnov statistics for a variable
     *
     * @param dataSet relevant data set
     * @param variable continuous variable whose normality is in question
     *
     * @return Kolmogorov-Smirnov statistics: index 0 is the D_n value, 1-5 are the critical values at alpha = .2, .15. .10, .05, and .01 respectively.
     */

    public static double[] kolmogorovSmirnov(DataSet dataSet, ContinuousVariable variable)
    {
        int n = dataSet.getNumRows();
        int columnIndex = dataSet.getColumn(variable);
        Normal idealDistribution = getNormal(dataSet, variable);

        double ks[] = new double[6];

        //get all critical values
        for(int i = 1; i < 6; i++)
        {
            ks[i] = estimateKSCriticalValue(i, n);
        }

        double[] _data = dataSet.getDoubleData().getColumn(columnIndex).toArray();

        List<Double> _leaveOutMissing = new ArrayList<Double>();

        for (int i = 0; i < _data.length; i++) {
            if (!Double.isNaN(_data[i])) {
                _leaveOutMissing.add(_data[i]);
            }
        }

        double[] data = new double[_leaveOutMissing.size()];

        for (int i = 0; i < _leaveOutMissing.size(); i++) data[i] = _leaveOutMissing.get(i);
        
        Arrays.sort(data);
//        sortVariable(dataSet, variable);

        //d = sup x |Fn(X) - i / n| -- the greatest distance between the ideal cdf and the edf
        double d = 0.0;
        for(int i = 1; i <= n; i++)
        {
//            double x = dataSet.getDouble(i - 1, columnIndex);
            double x = data[i - 1];
            //System.out.println("****" + x);
            //double valueAtQuantile = QQPlot.findQuantile((i + 1) / (dataSet.getNumRows() + 1.0), dataSet.getDouble(0, columnIndex), dataSet.getDouble(n - 1, columnIndex), idealDistribution, .0001, 0, 50);
            double idealValue = idealDistribution.cdf(x);
            //System.out.println(idealValue);
            //System.out.println((double)i / n);
            double difference = Math.abs(idealValue - ((double)i / n));
            if(difference > d)
            {
                //System.out.println("$$$$$" + difference);
                d = difference;
            }
        }

        ks[0] = d;

        /*
        System.out.println(ks[0]);
        System.out.println(ks[1]);
        System.out.println(ks[2]);
        System.out.println(ks[3]);
        System.out.println(ks[4]);
        System.out.println(ks[5]);
        */

        return ks;
    }

    public static double empiricalDistributionFunction(DataSet dataSet, Variable var, double x, int n)
    {
        double value = 0.0;
        int columnIndex = dataSet.getColumn(var);
        for (int i = 0; i < n; i++)
        {
            if(dataSet.getDouble(i, columnIndex) <= x)
            {
                value += 1;
            }
        }
        value /= n;
        //System.out.println(value);
        return value;
    }

    public static void sortVariable(DataSet dataSet, Variable variable)
    {
        int columnIndex = dataSet.getColumn(variable);
        for (int i = 0; i < dataSet.getNumRows(); i++)
        {
            for (int k = i; k < dataSet.getNumRows(); k++)
            {
                 if (dataSet.getDouble(i, columnIndex) > dataSet.getDouble(k, columnIndex))
                 {
                     double temp = dataSet.getDouble(i, columnIndex);
                     dataSet.setDouble(i, columnIndex, dataSet.getDouble(k, columnIndex));
                     dataSet.setDouble(k, columnIndex, temp);
                 }
            }
        }
    }

    /**
     * Estimates values of the Kolmogorov-Smirnov cdf to 100 iterations
     *
     */

    public static double kolmogorovSmirnovCDF(double x)
    {
        double sum = 0.0;
        for (int i = 0; i < 100; i++)
        {
            sum += Math.pow(Math.E, (-1 * Math.pow((2 * i) - 1, 2) * Math.pow(Math.PI, 2)) / Math.pow(8 * x, 2) );    
        }

        double estimatedValue = sum * (Math.sqrt(2 * Math.PI) / x);

        return estimatedValue;
    }

    /**
     * Calculates the K-S critical value
     *
     * @param level the level at which you are rejecting or accepting the test.  Use one of the following values: 1 for alpha = .20, 2 for .15, 3 for .10, 4 for .05, 5 for .01
     * @param n sample size
     *
     * @return criticalValue the critical value for the given level
     */

    public static double estimateKSCriticalValue(int level, int n)
    {
        double criticalValue = 0.0;
        //if n <= 35, lookup from table . . .
        if (n <= 35)
        {
            if (n >= 20 && n < 25) n = 20;
            if (n >= 25 && n < 30) n = 25;
            if (n >= 30 && n < 35) n = 30;
            double table[] = new double[36];
            switch(level)
            {
                case(1):
                    table[1] = .900;
                    table[2] = .684;
                    table[3] = .565;
                    table[4] = .494;
                    table[5] = .446;
                    table[6] = .410;
                    table[7] = .381;
                    table[8] = .358;
                    table[9] = .339;
                    table[10] = .322;
                    table[11] = .307;
                    table[12] = .295;
                    table[13] = .284;
                    table[14] = .274;
                    table[15] = .266;
                    table[16] = .258;
                    table[17] = .250;
                    table[18] = .244;
                    table[19] = .237;
                    table[20] = .231;
                    table[25] = .210;
                    table[30] = .190;
                    table[35] = .180;
                    break;
                case(2):
                    table[1] = .925;
                    table[2] = .726;
                    table[3] = .597;
                    table[4] = .525;
                    table[5] = .474;
                    table[6] = .436;
                    table[7] = .405;
                    table[8] = .381;
                    table[9] = .360;
                    table[10] = .342;
                    table[11] = .326;
                    table[12] = .313;
                    table[13] = .302;
                    table[14] = .292;
                    table[15] = .283;
                    table[16] = .274;
                    table[17] = .266;
                    table[18] = .259;
                    table[19] = .252;
                    table[20] = .246;
                    table[25] = .220;
                    table[30] = .200;
                    table[35] = .190;
                    break;
                case(3):
                    table[1] = .950;
                    table[2] = .776;
                    table[3] = .642;
                    table[4] = .564;
                    table[5] = .510;
                    table[6] = .470;
                    table[7] = .438;
                    table[8] = .411;
                    table[9] = .388;
                    table[10] = .368;
                    table[11] = .352;
                    table[12] = .338;
                    table[13] = .325;
                    table[14] = .314;
                    table[15] = .304;
                    table[16] = .295;
                    table[17] = .286;
                    table[18] = .278;
                    table[19] = .272;
                    table[20] = .264;
                    table[25] = .240;
                    table[30] = .220;
                    table[35] = .210;
                    break;
                case(4):
                    table[1] = .975;
                    table[2] = .842;
                    table[3] = .708;
                    table[4] = .624;
                    table[5] = .565;
                    table[6] = .521;
                    table[7] = .486;
                    table[8] = .457;
                    table[9] = .432;
                    table[10] = .410;
                    table[11] = .391;
                    table[12] = .375;
                    table[13] = .361;
                    table[14] = .349;
                    table[15] = .338;
                    table[16] = .328;
                    table[17] = .318;
                    table[18] = .309;
                    table[19] = .301;
                    table[20] = .294;
                    table[25] = .270;
                    table[30] = .240;
                    table[35] = .230;
                    break;
                case(5):
                    table[1] = .995;
                    table[2] = .929;
                    table[3] = .828;
                    table[4] = .733;
                    table[5] = .669;
                    table[6] = .618;
                    table[7] = .577;
                    table[8] = .543;
                    table[9] = .514;
                    table[10] = .490;
                    table[11] = .468;
                    table[12] = .450;
                    table[13] = .433;
                    table[14] = .418;
                    table[15] = .404;
                    table[16] = .392;
                    table[17] = .381;
                    table[18] = .371;
                    table[19] = .363;
                    table[20] = .356;
                    table[25] = .320;
                    table[30] = .290;
                    table[35] = .270;
                    break;
            }
            criticalValue = table[n];
        }
        //else, estimate
        else
        {
            switch(level)
            {
                case(1):
                    criticalValue = 1.07 / Math.sqrt(n);
                    break;
                case(2):
                    criticalValue = 1.14 / Math.sqrt(n);
                    break;
                case(3):
                    criticalValue = 1.22 / Math.sqrt(n);
                    break;
                case(4):
                    criticalValue = 1.36 / Math.sqrt(n);
                    break;
                case(5):
                    criticalValue = 1.63 / Math.sqrt(n);
                    break;
            }
        }
        return criticalValue;
    }

    /**
     * Calculates the Cramer-von-Mises statistic for a variable
     *
     * @param dataSet relevant data set
     * @param variable continuous variable whose normality is in question
     *
     * @return Cramer-von-Mises statistic
     */
    public static double cramerVonMises(DataSet dataSet, ContinuousVariable variable)
    {
        int n = dataSet.getNumRows();
        int columnIndex = dataSet.getColumn(variable);
        Normal idealDistribution = getNormal(dataSet, variable);

        double cvmStatistic = 0.0;

        for (int i = 1; i <= n; i++)
        {
            double summedTerm = (((2 * i) - 1) / (2 * n)) - idealDistribution.cdf(dataSet.getDouble(i - 1, columnIndex));
            summedTerm *= summedTerm;
            cvmStatistic += summedTerm;
        }

        cvmStatistic += 1 / (12 * n);
        cvmStatistic /= n;

        return cvmStatistic;
    }

    /**
     * Generates an ideal Normal distribution for some variable.
     *
     * @return Ideal Normal distribution for a variable.
     */

    public static Normal getNormal(DataSet dataSet, Variable variable)
    {
        double[] paramsForNormal = normalParams(dataSet, variable);
        double mean = paramsForNormal[0];
        double sd = paramsForNormal[1];

        return new Normal(mean, sd, new MersenneTwister());   
    }

    /**
     * Given some variable, returns the mean and standard deviation in indices 0 and 1 respectively.
     *
     * @return [0] -&gt; mean, [1] -&gt; standard deviation
     */

    public static double[] normalParams(DataSet dataSet, Variable variable)
    {
        int columnIndex = dataSet.getColumn(variable);
        double mean = 0.0;
        double sd = 0.0;

        //calculate the mean
        for (int i = 0; i < dataSet.getNumRows(); i++)
        {
            mean += dataSet.getDouble(i, columnIndex);
        }

        mean /= dataSet.getNumRows();

        //calculate the standard deviation
        for (int i = 0; i < dataSet.getNumRows(); i++)
        {
            sd += (dataSet.getDouble(i, columnIndex) - mean) * (dataSet.getDouble(i, columnIndex) - mean);
        }

        sd /= dataSet.getNumRows() - 1.0;
        sd = Math.sqrt(sd);

        double result[] = new double[2];
        result[0] = mean;
        result[1] = sd;
        
        return result;
    }
}



