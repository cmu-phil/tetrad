package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestEquivalence;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The equivalence (TOST) independence test: its calibration, and the two ways a badly chosen
 * margin fails. Data are bivariate normal with a known correlation, generated in closed form from
 * a pinned seed, so the tests are deterministic.
 */
public class TestIndTestEquivalence {

    private static final double ALPHA = 0.05;
    private static final double MARGIN = 0.10;

    private static DataSet bivariate(double rho, int n, long seed) {
        Random rng = new Random(seed);
        double[][] d = new double[n][2];
        double b = Math.sqrt(1 - rho * rho);

        for (int i = 0; i < n; i++) {
            double e1 = rng.nextGaussian();
            double e2 = rng.nextGaussian();
            d[i][0] = e1;
            d[i][1] = rho * e1 + b * e2;
        }

        return new BoxDataSet(new DoubleDataBox(d),
                List.of(new ContinuousVariable("X"), new ContinuousVariable("Y")));
    }

    /** The rate at which the test calls the pair independent, over many replicates. */
    private static double rate(double rho, int n, double margin, int reps) {
        int indep = 0;

        for (int rep = 0; rep < reps; rep++) {
            DataSet d = bivariate(rho, n, 90001L + 7919L * rep);
            IndTestEquivalence test = new IndTestEquivalence(
                    new IndTestFisherZ(d, ALPHA), margin, ALPHA);
            Node x = d.getVariable(0), y = d.getVariable(1);
            if (test.checkIndependence(x, y, new HashSet<>()).isIndependent()) indep++;
        }

        return indep / (double) reps;
    }

    /**
     * At the boundary of the test's null - a true correlation equal to the margin - the rate of
     * wrongly declaring negligibility must sit near alpha, and must not drift with the sample
     * size. An ordinary test has no such guarantee here.
     */
    @Test
    public void sizeIsNearAlphaAtTheMarginAtEverySampleSize() {
        for (int n : new int[]{500, 2000, 10000}) {
            double size = rate(MARGIN, n, MARGIN, 600);
            assertTrue("size " + size + " at n=" + n + " should not exceed about 2x alpha",
                    size < 2 * ALPHA);
        }
    }

    /** Well above the margin the test should essentially never claim negligibility. */
    @Test
    public void almostNeverCertifiesAClearAssociation() {
        assertTrue(rate(0.30, 2000, MARGIN, 300) < 0.01);
    }

    /** At rho = 0 the test should certify negligibility nearly always once there are data. */
    @Test
    public void certifiesIndependenceAtLargeSampleSizes() {
        assertTrue(rate(0.0, 5000, MARGIN, 300) > 0.95);
    }

    /**
     * The point of the rule: a weak-but-real association, of the kind a Richardson virtual edge
     * carries, is deleted far less often than by an ordinary Fisher Z test.
     */
    @Test
    public void deletesWeakRealAssociationsLessOftenThanFisherZ() {
        final int n = 500;
        final double rho = 0.05;
        int fisherDeletes = 0, tostDeletes = 0;

        for (int rep = 0; rep < 400; rep++) {
            DataSet d = bivariate(rho, n, 4242L + 7919L * rep);
            Node x = d.getVariable(0), y = d.getVariable(1);

            if (new IndTestFisherZ(d, ALPHA).checkIndependence(x, y, new HashSet<>()).isIndependent()) {
                fisherDeletes++;
            }
            if (new IndTestEquivalence(new IndTestFisherZ(d, ALPHA), MARGIN, ALPHA)
                    .checkIndependence(x, y, new HashSet<>()).isIndependent()) {
                tostDeletes++;
            }
        }

        assertTrue("Fisher Z should delete this weak real edge most of the time, but deleted "
                + fisherDeletes + "/400", fisherDeletes > 250);
        assertTrue("the equivalence test should delete it far less often, but deleted "
                        + tostDeletes + "/400 against Fisher Z's " + fisherDeletes,
                tostDeletes < fisherDeletes / 2);
    }

    /**
     * A margin at or below the smallest certifiable one makes every pair unremovable, which is how
     * the rule fails when the margin is too fine for the sample size.
     */
    @Test
    public void aMarginBelowTheCertifiableFloorNeverReportsIndependence() {
        DataSet d = bivariate(0.0, 500, 12345L);
        IndTestEquivalence probe = new IndTestEquivalence(new IndTestFisherZ(d, ALPHA), 0.5, ALPHA);
        double floor = probe.smallestUsableMargin(500, 0);

        assertTrue("the floor should be a usable number", floor > 0 && floor < 1);

        IndTestEquivalence tooFine = new IndTestEquivalence(
                new IndTestFisherZ(d, ALPHA), floor * 0.9, ALPHA);
        assertFalse("nothing should be certifiable below the floor",
                tooFine.checkIndependence(d.getVariable(0), d.getVariable(1), new HashSet<>())
                        .isIndependent());
    }

    /** The floor shrinks with the sample size, which is what makes a fine margin usable later. */
    @Test
    public void theCertifiableFloorShrinksWithSampleSize() {
        DataSet d = bivariate(0.0, 500, 777L);
        IndTestEquivalence test = new IndTestEquivalence(new IndTestFisherZ(d, ALPHA), 0.1, ALPHA);

        double small = test.smallestUsableMargin(500, 0);
        double large = test.smallestUsableMargin(50000, 0);

        assertTrue("floor at n=500 should exceed the floor at n=50000", small > large);
        assertEquals(0.0736, small, 0.005);
        assertEquals(0.0074, large, 0.002);
    }

    /** autoMargin places the margin at the requested multiple of the floor. */
    @Test
    public void autoMarginScalesToTheSampleSize() {
        DataSet d = bivariate(0.0, 2000, 31L);
        IndTestEquivalence auto = IndTestEquivalence.autoMargin(
                new IndTestFisherZ(d, ALPHA), 2.0, ALPHA, 2000);

        double floor = auto.smallestUsableMargin(2000, 0);
        assertEquals(2.0 * floor, auto.getMargin(), 1e-9);
        assertTrue("the auto margin must sit above the floor it is built from",
                auto.getMargin() > floor);
    }

    @Test
    public void badArgumentsAreRejected() {
        DataSet d = bivariate(0.0, 500, 5L);
        IndTestFisherZ fz = new IndTestFisherZ(d, ALPHA);

        for (double margin : new double[]{0.0, -0.1, 1.0, 2.0}) {
            try {
                new IndTestEquivalence(fz, margin, ALPHA);
                fail("expected a rejection for margin " + margin);
            } catch (IllegalArgumentException expected) {
                // wanted
            }
        }

        for (double alpha : new double[]{0.0, 0.5, 0.9}) {
            try {
                new IndTestEquivalence(fz, MARGIN, alpha);
                fail("expected a rejection for alpha " + alpha);
            } catch (IllegalArgumentException expected) {
                // wanted
            }
        }

        try {
            IndTestEquivalence.autoMargin(fz, 1.0, ALPHA, 2000);
            fail("expected a rejection for a multiplier of 1");
        } catch (IllegalArgumentException expected) {
            // wanted
        }
    }

    /** Leaving the adjacency test unset must leave CCD's behaviour exactly as it was. */
    @Test
    public void nullAdjacencyTestIsTheDefaultAndChangesNothing() {
        DataSet d = bivariate(0.4, 1000, 99L);
        Ccd ccd = new Ccd(new IndTestFisherZ(d, ALPHA));
        assertEquals(null, ccd.getAdjacencyTest());

        ccd.setAdjacencyTest(new IndTestEquivalence(new IndTestFisherZ(d, ALPHA), MARGIN, ALPHA));
        assertTrue(ccd.getAdjacencyTest() instanceof IndTestEquivalence);

        ccd.setAdjacencyTest(null);
        assertEquals(null, ccd.getAdjacencyTest());
    }
}
