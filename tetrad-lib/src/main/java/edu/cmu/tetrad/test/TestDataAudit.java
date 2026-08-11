///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the pre-search data audit. Each test plants one pathology in simulated data and asserts that the
 * corresponding finding fires; the clean-data test asserts that no warnings fire on well-behaved Gaussian data. All
 * simulations use fixed seeds.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestDataAudit {

    /**
     * Constructs a new test.
     */
    public TestDataAudit() {
    }

    /**
     * Clean Gaussian data should produce zero WARNING findings.
     */
    @Test
    public void testCleanGaussianNoWarnings() {
        Random rand = new Random(42);
        int n = 500, p = 5;
        double[][] data = new double[n][p];

        for (int i = 0; i < n; i++) {
            data[i][0] = rand.nextGaussian();

            for (int j = 1; j < p; j++) {
                data[i][j] = 0.4 * data[i][j - 1] + rand.nextGaussian();
            }
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));

        long warnings = audit.getFindings().stream()
                .filter(f -> f.getSeverity() == AuditFinding.Severity.WARNING).count();

        assertEquals(0, warnings);
    }

    /**
     * A near-copy pair should fire HIGH_CORRELATION and NEAR_DETERMINISM_CONTINUOUS but not
     * EXACT_LINEAR_DEPENDENCE.
     */
    @Test
    public void testCollinearPair() {
        Random rand = new Random(7);
        int n = 500;
        double[][] data = new double[n][3];

        for (int i = 0; i < n; i++) {
            data[i][0] = rand.nextGaussian();
            data[i][1] = data[i][0] + 0.01 * rand.nextGaussian();
            data[i][2] = rand.nextGaussian();
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));

        assertTrue(audit.hasFinding(FindingCode.HIGH_CORRELATION));
        assertTrue(audit.hasFinding(FindingCode.NEAR_DETERMINISM_CONTINUOUS));
        assertFalse(audit.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));
    }

    /**
     * An exact linear copy should fire EXACT_LINEAR_DEPENDENCE.
     */
    @Test
    public void testExactLinearDependence() {
        Random rand = new Random(7);
        int n = 300;
        double[][] data = new double[n][3];

        for (int i = 0; i < n; i++) {
            data[i][0] = rand.nextGaussian();
            data[i][1] = 2.0 * data[i][0];
            data[i][2] = rand.nextGaussian();
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));

        assertTrue(audit.hasFinding(FindingCode.EXACT_LINEAR_DEPENDENCE));
    }

    /**
     * A lognormal column should fire NON_GAUSSIAN at severity INFO; a Gaussian column should not fire.
     */
    @Test
    public void testSkewedColumn() {
        Random rand = new Random(11);
        int n = 500;
        double[][] data = new double[n][2];

        for (int i = 0; i < n; i++) {
            data[i][0] = Math.exp(rand.nextGaussian());
            data[i][1] = rand.nextGaussian();
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));
        List<AuditFinding> ng = audit.getFindings(FindingCode.NON_GAUSSIAN);

        assertTrue(ng.stream().anyMatch(f -> f.getVariables().contains("X1")));
        assertFalse(ng.stream().anyMatch(f -> f.getVariables().contains("X2")));
        assertTrue(ng.stream().allMatch(f -> f.getSeverity() == AuditFinding.Severity.INFO));
    }

    /**
     * A discrete variable with a 3-case category should fire SMALL_MARGINAL_CELL.
     */
    @Test
    public void testSmallMarginalCell() {
        int n = 500;
        int[][] disc = new int[1][n];

        for (int i = 0; i < n; i++) {
            disc[0][i] = (i < 3) ? 2 : (i % 2);
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, new double[0][0], disc, 3));

        assertTrue(audit.hasFinding(FindingCode.SMALL_MARGINAL_CELL));
    }

    /**
     * Two 5-level discrete variables at n = 100 should fire SMALL_PAIRWISE_CELLS.
     */
    @Test
    public void testSmallPairwiseCells() {
        Random rand = new Random(3);
        int n = 100;
        int[][] disc = new int[2][n];

        for (int i = 0; i < n; i++) {
            disc[0][i] = rand.nextInt(5);
            disc[1][i] = rand.nextInt(5);
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, new double[0][0], disc, 5));

        assertTrue(audit.hasFinding(FindingCode.SMALL_PAIRWISE_CELLS));
    }

    /**
     * A class variable with well-separated class means nearly determining a continuous variable should fire
     * NEAR_DETERMINISM_DISCRETE_CONTINUOUS.
     */
    @Test
    public void testNearDeterminismDiscreteContinuous() {
        Random rand = new Random(5);
        int n = 400;
        double[][] cont = new double[1][n];
        int[][] disc = new int[1][n];

        for (int i = 0; i < n; i++) {
            int cls = rand.nextInt(3);
            disc[0][i] = cls;
            cont[0][i] = 100.0 * cls + rand.nextGaussian();
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, cont, disc, 3));

        assertTrue(audit.hasFinding(FindingCode.NEAR_DETERMINISM_DISCRETE_CONTINUOUS));
    }

    /**
     * A negligible-variance (but varying) continuous column and a 99.5-percent-modal (but two-category) discrete
     * column should each fire NEAR_CONSTANT, and neither should fire CONSTANT_COLUMN.
     */
    @Test
    public void testNearConstant() {
        Random rand = new Random(9);
        int n = 400;
        double[][] cont = new double[2][n];
        int[][] disc = new int[1][n];

        for (int i = 0; i < n; i++) {
            cont[0][i] = 3.14 + 1e-8 * rand.nextGaussian();
            cont[1][i] = rand.nextGaussian();
            disc[0][i] = (i < 2) ? 1 : 0;
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, cont, disc, 2));
        List<AuditFinding> nc = audit.getFindings(FindingCode.NEAR_CONSTANT);

        assertTrue(nc.stream().anyMatch(f -> f.getVariables().contains("X1")));
        assertTrue(nc.stream().anyMatch(f -> f.getVariables().contains("D1")));
        assertFalse(audit.hasFinding(FindingCode.CONSTANT_COLUMN));
    }

    /**
     * An exactly constant continuous column, a continuous column constant on its two non-missing entries, and a
     * single-category discrete column should each fire CONSTANT_COLUMN (and not NEAR_CONSTANT); a varying Gaussian
     * column should fire neither.
     */
    @Test
    public void testConstantColumn() {
        Random rand = new Random(31);
        int n = 400;
        double[][] cont = new double[3][n];
        int[][] disc = new int[1][n];

        for (int i = 0; i < n; i++) {
            cont[0][i] = 3.14;
            cont[1][i] = rand.nextGaussian();
            cont[2][i] = (i < 2) ? 7.0 : Double.NaN;
            disc[0][i] = 0;
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, cont, disc, 2));
        List<AuditFinding> cc = audit.getFindings(FindingCode.CONSTANT_COLUMN);

        assertTrue(cc.stream().anyMatch(f -> f.getVariables().contains("X1")));
        assertTrue(cc.stream().anyMatch(f -> f.getVariables().contains("X3")));
        assertTrue(cc.stream().anyMatch(f -> f.getVariables().contains("D1")));
        assertFalse(cc.stream().anyMatch(f -> f.getVariables().contains("X2")));
        assertFalse(audit.hasFinding(FindingCode.NEAR_CONSTANT));
        assertEquals(3.14, cc.stream().filter(f -> f.getVariables().contains("X1"))
                .findFirst().orElseThrow().getValues().get("value"), 0.0);
    }

    /**
     * A column with no non-missing values at all should fire CONSTANT_COLUMN with numNonMissing = 0.
     */
    @Test
    public void testAllMissingColumn() {
        Random rand = new Random(37);
        int n = 200;
        double[][] data = new double[n][2];

        for (int i = 0; i < n; i++) {
            data[i][0] = rand.nextGaussian();
            data[i][1] = Double.NaN;
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));
        List<AuditFinding> cc = audit.getFindings(FindingCode.CONSTANT_COLUMN);

        assertEquals(1, cc.size());
        assertTrue(cc.get(0).getVariables().contains("X2"));
        assertEquals(0.0, cc.get(0).getValues().get("numNonMissing"), 0.0);
    }

    /**
     * A 13-level discrete variable should fire DISCRETE_MANY_LEVELS, and a continuous variable taking three values
     * should fire CONTINUOUS_FEW_VALUES.
     */
    @Test
    public void testManyLevelsAndFewValues() {
        Random rand = new Random(13);
        int n = 400;
        double[][] cont = new double[1][n];
        int[][] disc = new int[1][n];

        for (int i = 0; i < n; i++) {
            cont[0][i] = rand.nextInt(3);
            disc[0][i] = i % 13;
        }

        DataAudit audit = new DataAudit(mixedDataSet(n, cont, disc, 13));

        assertTrue(audit.hasFinding(FindingCode.DISCRETE_MANY_LEVELS));
        assertTrue(audit.hasFinding(FindingCode.CONTINUOUS_FEW_VALUES));
    }

    /**
     * n = 30 with p = 10 should fire LOW_SAMPLE_RATIO.
     */
    @Test
    public void testLowSampleRatio() {
        Random rand = new Random(17);
        int n = 30, p = 10;
        double[][] data = new double[n][p];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                data[i][j] = rand.nextGaussian();
            }
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));

        assertTrue(audit.hasFinding(FindingCode.LOW_SAMPLE_RATIO));
    }

    /**
     * MCAR-injected missingness should produce exactly one MISSING_DATA finding, a non-null delegated
     * MissingDataAudit, and a Little's MCAR p-value among the finding's values.
     */
    @Test
    public void testMissingData() {
        Random rand = new Random(21);
        int n = 500, p = 4;
        double[][] data = new double[n][p];

        for (int i = 0; i < n; i++) {
            data[i][0] = rand.nextGaussian();

            for (int j = 1; j < p; j++) {
                data[i][j] = 0.5 * data[i][j - 1] + rand.nextGaussian();
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (rand.nextDouble() < 0.05) data[i][j] = Double.NaN;
            }
        }

        DataAudit audit = new DataAudit(continuousDataSet(data));
        List<AuditFinding> md = audit.getFindings(FindingCode.MISSING_DATA);

        assertEquals(1, md.size());
        assertNotNull(audit.getMissingDataAudit());
        assertTrue(md.get(0).getValues().containsKey("littlesMcarP"));
    }

    /**
     * The JSON rendering should contain the expected top-level keys and have balanced braces.
     */
    @Test
    public void testJsonWellFormed() {
        Random rand = new Random(23);
        int n = 100;
        double[][] data = new double[n][2];

        for (int i = 0; i < n; i++) {
            data[i][0] = Math.exp(rand.nextGaussian());
            data[i][1] = rand.nextGaussian();
        }

        String json = new DataAudit(continuousDataSet(data)).toJson();

        assertTrue(json.contains("\"findings\":["));
        assertTrue(json.contains("\"numRows\":100"));

        long open = json.chars().filter(c -> c == '{').count();
        long close = json.chars().filter(c -> c == '}').count();

        assertEquals(open, close);
    }

    //==================================== HELPERS ====================================//

    /**
     * Builds a continuous dataset with variables X1..Xp from a row-major data matrix.
     */
    private DataSet continuousDataSet(double[][] data) {
        List<Node> variables = new ArrayList<>();

        for (int j = 0; j < data[0].length; j++) {
            variables.add(new ContinuousVariable("X" + (j + 1)));
        }

        return new BoxDataSet(new DoubleDataBox(data), variables);
    }

    /**
     * Builds a mixed dataset with continuous columns cont[c][row] named X1..Xk followed by discrete columns
     * disc[d][row] named D1..Dm, each with the given number of categories.
     */
    private DataSet mixedDataSet(int n, double[][] cont, int[][] disc, int numCategories) {
        List<Node> variables = new ArrayList<>();

        for (int c = 0; c < cont.length; c++) {
            variables.add(new ContinuousVariable("X" + (c + 1)));
        }

        for (int d = 0; d < disc.length; d++) {
            List<String> cats = new ArrayList<>();

            for (int k = 0; k < numCategories; k++) {
                cats.add("c" + k);
            }

            variables.add(new DiscreteVariable("D" + (d + 1), cats));
        }

        MixedDataBox box = new MixedDataBox(variables, n);

        for (int i = 0; i < n; i++) {
            for (int c = 0; c < cont.length; c++) {
                box.set(i, c, cont[c][i]);
            }

            for (int d = 0; d < disc.length; d++) {
                box.set(i, cont.length + d, disc[d][i]);
            }
        }

        return new BoxDataSet(box, variables);
    }
}
