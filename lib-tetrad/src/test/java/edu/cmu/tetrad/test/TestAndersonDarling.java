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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.*;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author Joseph Ramsey
 */
public final class TestAndersonDarling extends TestCase {
    public TestAndersonDarling(String name) {
        super(name);
    }

    public void test1() {
        double[] x = rand1(100);
        double[] y = rand2(100);

        x = standardize(x);
        y = standardize(y);

        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) z[i] = (x[i] + y[i]) / 2;

        x = sort(x);
        y = sort(y);
        z = sort(z);
        z = standardize(z);

        System.out.println("x " + getS(x));
        System.out.println("y " + getS(y));
        System.out.println("z " + getS(z));

        double avg = (getS(x) + getS(y)) / 2.0;

        System.out.println("Avg S(x) and S(y) = " + avg);


//        Arrays.sort(x);
//        System.out.println("x sorted " + getS(x));
//
//        Arrays.sort(y);
//        System.out.println("y sorted " + getS(y));
//
//        Arrays.sort(z);
//        System.out.println("z sorted " + getS(z));
//
        z = standardize(z);
        z = sort(z);
        System.out.println("z sorted standardized " + getS(z));


    }

    public void test2() {
        double[] x = standardize(rand1(100));
        double[] y = standardize(rand1(100));

        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) z[i] = (x[i] + y[i]) / 2;

        printDiff(x, y, z);
//
//        x = sortAs(x, z);
//        y = sortAs(y, z);
//        z = sort(z);
//
//        System.out.println("x " + getS(x));
//        System.out.println("y " + getS(y));
//        System.out.println("z " + getS(z));
//
//        double avg = (getS(x) + getS(y)) / 2.0;
//
//        System.out.println("Avg S(x) and S(y) = " + avg);
//
//        System.out.println("sort(x) " + getS(sort(x)));
//        System.out.println("sort(y) " + getS(sort(y)));
//        System.out.println("std(sort(z))) " + getS(standardize(sort(z))));
//
//        System.out.println("ad x = " + ad(sort(x)));
//        System.out.println("ad y = " + ad(sort(y)));
//        System.out.println("ad z = " + ad(standardize(sort(z))));
//
//        System.out.println("\nSorting by x");
//
//        y = sortAs(y, x);
//        z = sortAs(z, x);
//        x = sort(x);
//
//        System.out.println("x " + getS(x));
//        System.out.println("y " + getS(y));
//        System.out.println("z " + getS(z));
//
//        avg = (getS(x) + getS(y)) / 2.0;
//
//        System.out.println("Avg S(x) and S(y) = " + avg);
//
//        System.out.println("sort(x) " + getS(sort(x)));
//        System.out.println("sort(y) " + getS(sort(y)));
//        System.out.println("std(sort(z))) " + getS(standardize(sort(z))));
//
//        System.out.println("\nSorting by y");
//
//        x = sortAs(x, y);
//        z = sortAs(z, y);
//        y = sort(y);
//
//        System.out.println("x " + getS(x));
//        System.out.println("y " + getS(y));
//        System.out.println("z " + getS(z));
//
//        avg = (getS(x) + getS(y)) / 2.0;
//
//        System.out.println("Avg S(x) and S(y) = " + avg);
//
//        System.out.println("sort(x) " + getS(sort(x)));
//        System.out.println("sort(y) " + getS(sort(y)));
//        System.out.println("std(z) " + getS(standardize(z)));
//        System.out.println("std(sort(z))) " + getS(standardize(sort(z))));
//


    }

    private void printDiff(double[] x, double[] y, double[] z) {
//        for (int i = 0; i < z.length; i++) {
//            double a = RandomUtil.getInstance().normalCdf(0, 1, z[i]);
//            double b = RandomUtil.getInstance().normalCdf(0, 1, x[i]);
//            double c = RandomUtil.getInstance().normalCdf(0, 1, y[i]);
//            System.out.println(a - 0.5 * (b + c));
//        }
//
        List<Double> n = new ArrayList<Double>();

        int size = z.length;

        for (int k = 1; k <= size; k++) {
            n.add((2 * k - 1) / (double) size);
        }

        List<Double> m = new ArrayList<Double>(n);
        Collections.shuffle(m);

        double sum = 0.0;

        for (int k = 0; k < size; k++) {
            double _z = Math.log(RandomUtil.getInstance().normalCdf(0, 1, z[k]));
            double _x = Math.log(RandomUtil.getInstance().normalCdf(0, 1, x[k]));
            double _y = Math.log(RandomUtil.getInstance().normalCdf(0, 1, y[k]));

            double _z2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -z[k]));
            double _x2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -x[k]));
            double _y2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -y[k]));

            sum += (n.get(k) * _z - m.get(k) * (.5*(_x + _y)));
            sum += (n.get(size - k - 1) * _z2 - m.get(size - k - 1) * (.5*(_x2 + _y2)));
        }

        System.out.println("sum = " + sum);

        double sum2 = 0.0;

        for (int k = 0; k < size; k++) {
            double _z = Math.log(RandomUtil.getInstance().normalCdf(0, 1, Math.sqrt(2) * z[k]));
            double _x = Math.log(RandomUtil.getInstance().normalCdf(0, 1, x[k]));
            double _y = Math.log(RandomUtil.getInstance().normalCdf(0, 1, y[k]));

            double _z2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -Math.sqrt(2) * z[k]));
            double _x2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -x[k]));
            double _y2 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, -y[k]));

            sum2 += (n.get(k) * _z - m.get(k) * (.5*(_x + _y)));
            sum2 += (n.get(size - k - 1) * _z2 - m.get(size - k - 1) * (.5*(_x2 + _y2)));
        }

        System.out.println("sum2 = " + sum2);
    }

    private double ad(double[] x) {
        return -x.length - getS(x);
    }

    private double[] sortAs(double[] x, double[] z) {
        x = Arrays.copyOf(x, x.length);
        z = Arrays.copyOf(z, z.length);

        Map<Double, Double> m = new HashMap<Double, Double>();

        for (int i = 0; i < x.length; i++) {
            m.put(z[i], x[i]);
        }

        Arrays.sort(z);

        for (int i = 0; i < x.length; i++) {
            x[i] = m.get(z[i]);
        }

        return x;
    }

    private double[] sort(double[] z) {
        z = Arrays.copyOf(z, z.length);
        Arrays.sort(z);
        return z;
    }

    private double[] rand1(int n) {
        double[] x = new double[n];

        for (int i = 0; i < x.length; i++) {
            x[i] = RandomUtil.getInstance().nextUniform(0, 1);
        }
        return x;
    }

    private double[] rand2(int n) {
        double[] x = new double[n];

        for (int i = 0; i < x.length; i++) {
            x[i] = RandomUtil.getInstance().nextBeta(2,5);
        }
        return x;
    }

    private double getS(double[] x) {
        int n = x.length;

        double h = 0.0;

        for (int i = 0; i < n; i++) {
            double x1 = x[i];
            double a1 = Math.log(RandomUtil.getInstance().normalCdf(0, 1, x1));

            double x2 = x[n - i - 1];
            double a2 = Math.log(1.0 - RandomUtil.getInstance().normalCdf(0, 1, x2));

            double k = (2 * (i + 1) - 1) * (a1 + a2);
            h += k;
        }

        h /= (double) n;

        return h;

    }

    private double[] standardize(double[] x) {
        x = Arrays.copyOf(x, x.length);

        double mean = StatUtils.mean(x);
        double sd = StatUtils.sd(x);

        for (int i = 0; i < x.length; i++) {
            x[i] = (x[i] - mean) / sd;
        }

        return x;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestAndersonDarling.class);
    }
}





