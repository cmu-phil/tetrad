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
     * The UniformityTest class is used to calculate the p-value of a list of points
     * using the Kolmogorov-Smirnov test and determine if the distribution is uniform.
     */
    public UniformityTest() {
    }

    /**
     * Calculates the p-value of a list of points using the Kolmogorov-Smirnov test.
     *
     * @param points A list of double values representing the data points.
     * @return The p-value of the test.
     */
    public static double getPValue(List<Double> points) {

        // Create a uniform distribution with the same range as the data
        double min = points.stream().min(Double::compareTo).orElse(0.0);
        double max = points.stream().max(Double::compareTo).orElse(1.0);

        return getPValue(points, min, max);
    }

    /**
     * Calculates the p-value of a list of points using the Kolmogorov-Smirnov test.
     *
     * @param points A list of double values representing the data points.
     * @param min    The minimum value of the data range.
     * @param max    The maximum value of the data range.
     * @return The p-value of the Kolmogorov-Smirnov test.
     */
    public static double getPValue(List<Double> points, double min, double max) {

        // Create a uniform distribution with the same range as the data
        try {
            double[] data = points.stream().mapToDouble(Double::doubleValue).toArray();

            UniformRealDistribution distribution = new UniformRealDistribution(min, max);

            // Perform the Kolmogorov-Smirnov test
            KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
            return test.kolmogorovSmirnovTest(distribution, data);
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

        double pValue = getPValue(points);

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

