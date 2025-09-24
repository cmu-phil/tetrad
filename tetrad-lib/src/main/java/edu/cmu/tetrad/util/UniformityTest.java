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

package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.ArrayList;
import java.util.List;

/**
 * The UniformityTest class provides methods to calculate the p-value of a list of points using the Kolmogorov-Smirnov
 * test and determine if the distribution is uniform.
 *
 * @author josephramsey
 */
public class UniformityTest {

    /**
     * The UniformityTest class is used to calculate the p-value of a list of points using the Kolmogorov-Smirnov test
     * and determine if the distribution is uniform.
     */
    public UniformityTest() {
    }

    /**
     * Calculates the p-value of a list of points using the Kolmogorov-Smirnov test.
     *
     * @param points A list of double values representing the data points.
     * @return The p-value of the test.
     */
    public static double getKsPValue(List<Double> points) {

        // Create a uniform distribution with the same range as the data
        double min = points.stream().min(Double::compareTo).orElse(0.0);
        double max = points.stream().max(Double::compareTo).orElse(1.0);

        return getKsPValue(points, min, max);
    }

    /**
     * Calculates the p-value of a list of points using the Kolmogorov-Smirnov test.
     *
     * @param points A list of double values representing the data points.
     * @param min    The minimum value of the data range.
     * @param max    The maximum value of the data range.
     * @return The p-value of the Kolmogorov-Smirnov test.
     */
    public static double getKsPValue(List<Double> points, double min, double max) {

        // Create a uniform distribution with the same range as the data
        try {
            double[] data = points.stream().mapToDouble(Double::doubleValue).toArray();

            UniformRealDistribution distribution = new UniformRealDistribution(min, max);

            // Perform the Kolmogorov-Smirnov test
            KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
            return data.length > 2 ? test.kolmogorovSmirnovTest(distribution, data) : Double.NaN;
        } catch (NumberIsTooLargeException e) {
            System.out.println(e.getMessage());
            return Double.NaN;
        }

    }

    /**
     * The main method of the UniformityTest class. It calculates the p-value of a list of points using the
     * Kolmogorov-Smirnov test and checks the p-value against a significance level to determine if the distribution is
     * uniform.
     *
     * @param args The command-line arguments passed to the program.
     */
    public static void main(String[] args) {
        // Generate a list of points (sample data)
        List<Double> points = generatePoints();

        double pValue = getKsPValue(points);

        // Check the p-value against a significance level (e.g., 0.05)
        double significanceLevel = 0.05;
        if (pValue < significanceLevel) {
            System.out.println("The distribution is not uniform.");
        } else {
            System.out.println("The distribution is uniform.");
        }
    }

    // Helper method to generate a list of points (sample data)
    private static List<Double> generatePoints() {
        List<Double> points = new ArrayList<>();
        points.add(0.1);
        points.add(0.2);
        points.add(0.3);
        points.add(0.4);
        points.add(0.5);
        return points;
    }
}


