package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.ArrayList;
import java.util.List;

public class UniformityTest {

    public static double getPValue(List<Double> points) {
        // Convert the list to a primitive double array
        double[] data = points.stream().mapToDouble(Double::doubleValue).toArray();

        // Create a uniform distribution with the same range as the data
        double min = points.stream().min(Double::compareTo).orElse(0.0);
        double max = points.stream().max(Double::compareTo).orElse(1.0);
        UniformRealDistribution distribution;
        try {
            distribution = new UniformRealDistribution(min, max);
        } catch (NumberIsTooLargeException e) {
            e.printStackTrace();
            return Double.NaN;
        }

        // Perform the Kolmogorov-Smirnov test
        KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
        return test.kolmogorovSmirnovTest(distribution, data);
    }

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

