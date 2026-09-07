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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static org.junit.Assert.*;

/**
 * Tests that the ScatterPlot and QQPlot models used by the Plot Matrix and Q-Q Plot tools produce sensible plots in
 * the presence of missing values. Missing continuous values are NaN; missing discrete values are
 * DiscreteVariable.MISSING_VALUE.
 *
 * @author josephramsey
 */
public class TestPlotMissingValues {

    /**
     * Builds a continuous data set x, y with y roughly linear in x, then knocks out some values of each. Rows 3 and 7
     * are missing x; rows 5 and 7 are missing y. So there are 3 incomplete rows and n - 3 complete pairs.
     */
    private static DataSet continuousDataWithMissing(int n) {
        RandomUtil.getInstance().setSeed(38482L);

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("x"));
        vars.add(new ContinuousVariable("y"));
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(n, 2), vars);

        for (int i = 0; i < n; i++) {
            double x = RandomUtil.getInstance().nextGaussian(0, 1);
            double y = 0.8 * x + RandomUtil.getInstance().nextGaussian(0, 0.5);
            data.setDouble(i, 0, x);
            data.setDouble(i, 1, y);
        }

        data.setDouble(3, 0, Double.NaN);
        data.setDouble(7, 0, Double.NaN);
        data.setDouble(5, 1, Double.NaN);
        data.setDouble(7, 1, Double.NaN);

        return data;
    }

    @Test
    public void testScatterPlotWithMissingValues() {
        int n = 100;
        DataSet data = continuousDataWithMissing(n);

        ScatterPlot plot = new ScatterPlot(data, false, "x", "y", false);

        // The axis bounds must be finite; with any NaN point included, min/max propagate NaN and the plot goes blank.
        assertTrue("Xmin should be finite", Double.isFinite(plot.getXmin()));
        assertTrue("Xmax should be finite", Double.isFinite(plot.getXmax()));
        assertTrue("Ymin should be finite", Double.isFinite(plot.getYmin()));
        assertTrue("Ymax should be finite", Double.isFinite(plot.getYmax()));

        // Only complete pairs are plotted: rows 3, 5, and 7 are incomplete.
        Vector<Point2D.Double> points = plot.getSievedValues();
        assertEquals(n - 3, points.size());

        for (Point2D.Double point : points) {
            assertTrue("Plotted point should be finite",
                    Double.isFinite(point.getX()) && Double.isFinite(point.getY()));
        }

        // The correlation is computed from the same complete pairs, so it is finite and strongly positive here.
        double r = plot.getCorrelationCoeff();
        assertTrue("Correlation should be finite, was " + r, Double.isFinite(r));
        assertTrue("Correlation should be strongly positive, was " + r, r > 0.5);

        // The regression line for the trend-lines option must also be computable.
        assertTrue("Regression coefficient should be finite", Double.isFinite(plot.getRegressionCoeff()));
        assertTrue("Regression intercept should be finite", Double.isFinite(plot.getRegressionIntercept()));
    }

    @Test
    public void testScatterPlotWithMissingDiscreteValues() {
        RandomUtil.getInstance().setSeed(38483L);
        int n = 60;

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("x"));
        vars.add(new DiscreteVariable("d", 3));
        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, RandomUtil.getInstance().nextGaussian(0, 1));
            data.setInt(i, 1, RandomUtil.getInstance().nextInt(3));
        }

        data.setInt(4, 1, DiscreteVariable.MISSING_VALUE);
        data.setInt(9, 1, DiscreteVariable.MISSING_VALUE);

        ScatterPlot plot = new ScatterPlot(data, false, "x", "d", false);

        assertEquals(n - 2, plot.getSievedValues().size());
        assertTrue("Ymin should be finite", Double.isFinite(plot.getYmin()));
        assertTrue("Ymax should not include the missing-value code",
                plot.getYmin() >= 0.0 && plot.getYmax() <= 2.0);
    }

    @Test
    public void testQQPlotWithMissingValues() {
        int n = 200;
        RandomUtil.getInstance().setSeed(38484L);

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("x"));
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(n, 1), vars);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, RandomUtil.getInstance().nextGaussian(5, 2));
        }

        int numMissing = 20;
        for (int i = 0; i < numMissing; i++) {
            data.setDouble(i * 7, 0, Double.NaN);
        }

        QQPlot qqPlot = new QQPlot(data, data.getVariable("x"));

        double[] sample = qqPlot.getSampleVariable();
        double[] comparison = qqPlot.getComparisonVariable();

        // Exactly the nonmissing values are plotted, paired index-for-index with the quantiles.
        assertEquals(n - numMissing, sample.length);
        assertEquals(sample.length, comparison.length);

        // Both arrays are sorted ascending, and all values are finite.
        for (int i = 0; i < sample.length; i++) {
            assertTrue(Double.isFinite(sample[i]));
            assertTrue(Double.isFinite(comparison[i]));
            if (i > 0) {
                assertTrue("Sample should be sorted", sample[i - 1] <= sample[i]);
                assertTrue("Comparison should be sorted", comparison[i - 1] <= comparison[i]);
            }
        }

        // Min and max reflect the nonmissing data.
        assertEquals(sample[0], qqPlot.getMinSample(), 0.0);
        assertEquals(sample[sample.length - 1], qqPlot.getMaxSample(), 0.0);

        // For Gaussian data the q-q points should lie near the diagonal: the mean absolute difference between the
        // order statistics and the matched theoretical quantiles should be small relative to the spread.
        double meanAbsDiff = 0.0;
        for (int i = 0; i < sample.length; i++) {
            meanAbsDiff += Math.abs(sample[i] - comparison[i]);
        }
        meanAbsDiff /= sample.length;
        assertTrue("Q-Q points should lie near the diagonal for Gaussian data; mean abs diff = " + meanAbsDiff,
                meanAbsDiff < 0.5);
    }

    @Test
    public void testQQPlotAllNegativeData() {
        int n = 50;
        RandomUtil.getInstance().setSeed(38485L);

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("x"));
        DataSet data = new BoxDataSet(new VerticalDoubleDataBox(n, 1), vars);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, -10.0 + RandomUtil.getInstance().nextGaussian(0, 1));
        }

        data.setDouble(2, 0, Double.NaN);

        QQPlot qqPlot = new QQPlot(data, data.getVariable("x"));

        // The old code initialized the sample max to 0.0, which is wrong for all-negative data.
        assertTrue("Max sample should be negative for all-negative data, was " + qqPlot.getMaxSample(),
                qqPlot.getMaxSample() < 0.0);
        assertEquals(n - 1, qqPlot.getSampleVariable().length);
    }
}
