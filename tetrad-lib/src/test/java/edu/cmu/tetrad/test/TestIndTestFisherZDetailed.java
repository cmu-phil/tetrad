package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.Matrix;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class TestIndTestFisherZDetailed {

    @Test
    public void testBasicIndependence() {
        // X -> Y, Z independent
        double[][] data = {
                {1.0, 1.1, 0.5},
                {2.0, 2.1, 0.6},
                {3.0, 2.9, 0.4},
                {4.0, 4.2, 0.7},
                {5.0, 4.8, 0.5}
        };
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);

        // X and Y should be dependent
        IndependenceResult resXY = test.checkIndependence(vars.get(0), vars.get(1), Collections.emptySet());
        assertFalse("X and Y should be dependent", resXY.isIndependent());

        // X and Z should be independent
        IndependenceResult resXZ = test.checkIndependence(vars.get(0), vars.get(2), Collections.emptySet());
        assertTrue("X and Z should be independent", resXZ.isIndependent());
    }

    @Test
    public void testPerfectCorrelation() {
        // X and Y are perfectly correlated: r=1.0
        double[][] data = {
                {1.0, 1.0, 0.5},
                {2.0, 2.0, 0.6},
                {3.0, 3.0, 0.4},
                {4.0, 4.0, 0.7},
                {5.0, 5.0, 0.5}
        };
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);

        // This might fail with Infinity or NaN if not handled
        try {
            IndependenceResult resXY = test.checkIndependence(vars.get(0), vars.get(1), Collections.emptySet());
            assertFalse("X and Y should be dependent", resXY.isIndependent());
            assertEquals(0.0, resXY.getPValue(), 1e-10);
        } catch (Exception e) {
            fail("Should handle r=1.0 without exception: " + e.getMessage());
        }
    }

    @Test
    public void testSingularMatrix() {
        // X, Y, Z such that Z = X + Y (perfectly linear)
        // Making the correlation matrix singular
        double[][] data = {
                {1, 0, 1},
                {0, 1, 1},
                {2, 3, 5},
                {4, 1, 5},
                {1, 1, 2}
        };
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);

        // Testing X _||_ Y | Z
        // Singular matrix should be handled gracefully (dependent, p=0)
        try {
            IndependenceResult res = test.checkIndependence(vars.get(0), vars.get(1), Set.of(vars.get(2)));
            assertFalse("X and Y should be dependent (singular matrix)", res.isIndependent());
            assertEquals(0.0, res.getPValue(), 1e-10);
        } catch (Exception e) {
            fail("Should handle singular matrix without exception: " + e.getMessage());
        }

        // With pseudoinverse
        test.setUsePseudoinverse(true);
        try {
            IndependenceResult res = test.checkIndependence(vars.get(0), vars.get(1), Set.of(vars.get(2)));
            assertNotNull(res);
        } catch (Exception e) {
            fail("Should handle singular matrix with pseudoinverse: " + e.getMessage());
        }
    }

    @Test
    public void testLedoitWolfShrinkage() {
        // Small sample size, many variables -> shrinkage should trigger
        int n = 10;
        int p = 5;
        double[][] data = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                data[i][j] = Math.random();
            }
        }
        List<Node> vars = new java.util.ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("V" + j));

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);
        test.setShrinkageMode(IndTestFisherZ.ShrinkageMode.LEDOIT_WOLF);

        try {
            IndependenceResult res = test.checkIndependence(vars.get(0), vars.get(1), Set.of(vars.get(2)));
            assertNotNull(res);
        } catch (Exception e) {
            fail("Ledoit-Wolf should work: " + e.getMessage());
        }
    }
    
    @Test
    public void testRidgeShrinkage() {
        double[][] data = {
                {1, 0, 1},
                {0, 1, 1},
                {2, 3, 5},
                {4, 1, 5},
                {1, 1, 2},
                {0, 0, 0}
        };
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05);
        test.setShrinkageMode(IndTestFisherZ.ShrinkageMode.RIDGE);
        test.setRidge(0.1);

        try {
            IndependenceResult res = test.checkIndependence(vars.get(0), vars.get(1), Set.of(vars.get(2)));
            assertNotNull(res);
            assertFalse(Double.isNaN(res.getPValue()));
        } catch (Exception e) {
            fail("Ridge shrinkage should work: " + e.getMessage());
        }
    }
}
