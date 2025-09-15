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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author josephramsey
 */
public final class TestAndersonDarling {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(384829384L);

        double[] x = rand1();
        double[] y = rand2();

        x = standardize(x);
        y = standardize(y);

        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) z[i] = (x[i] + y[i]) / 2;

        x = sort(x);
        y = sort(y);
        z = sort(z);
        z = standardize(z);

        assertEquals(-100, getS(z), 5.0);
    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(4838582394L);

        double[] x = rand1();

        double aa = new AndersonDarlingTest(x).getASquared();

        assertEquals(1.93, aa, 0.1);
    }

    private double[] sort(double[] z) {
        z = Arrays.copyOf(z, z.length);
        Arrays.sort(z);
        return z;
    }

    private double[] rand1() {
        double[] x = new double[100];

        for (int i = 0; i < x.length; i++) {
            x[i] = RandomUtil.getInstance().nextUniform(0, 1);
        }
        return x;
    }

    private double[] rand2() {
        double[] x = new double[100];

        for (int i = 0; i < x.length; i++) {
            x[i] = RandomUtil.getInstance().nextBeta(2, 5);
        }
        return x;
    }

    private double getS(double[] x) {
        int n = x.length;

        double h = 0.0;

        for (int i = 0; i < n; i++) {
            double x1 = x[i];
            double a1 = FastMath.log(RandomUtil.getInstance().normalCdf(0, 1, x1));

            double x2 = x[n - i - 1];
            double a2 = FastMath.log(1.0 - RandomUtil.getInstance().normalCdf(0, 1, x2));

            double k = (2 * (i + 1) - 1) * (a1 + a2);
            h += k;
        }

        h /= n;

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
}






