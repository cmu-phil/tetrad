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
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.MissingnessInjector.Mechanism;
import edu.cmu.tetrad.data.MissingnessInjector.MechanismClass;
import edu.cmu.tetrad.data.MissingnessInjector.Result;
import edu.cmu.tetrad.data.MissingnessInjector.Spec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks the mechanism terms of {@link MissingnessInjector}: that rates stay calibrated whatever the terms, that
 * each term moves the data the way it should, and that the mechanism is classified correctly.
 */
public class TestMissingnessInjectorMechanisms {

    private static final int N = 20000;
    private static final double[] RATES = {0.0, 0.3, 0.3, 0.3, 0.3};

    @Test
    public void emptyMechanismMatchesTwoArgumentInject() {
        DataSet d = chain(500, 7);
        for (double lambda : new double[]{0.0, 1.0}) {
            RandomUtil.getInstance().setSeed(42L);
            DataSet a = MissingnessInjector.inject(d, new Spec(RATES, lambda)).data();
            RandomUtil.getInstance().setSeed(42L);
            DataSet b = MissingnessInjector.inject(d, new Spec(RATES, lambda), Mechanism.none()).data();
            for (int i = 0; i < d.getNumRows(); i++) {
                for (int j = 0; j < d.getNumColumns(); j++) {
                    assertEquals(Double.isNaN(a.getDouble(i, j)), Double.isNaN(b.getDouble(i, j)));
                }
            }
        }
    }

    @Test
    public void marOnObservedDriverIsCalibratedAndClassified() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.0), Mechanism.none().withValueEffect(3, 0, 1.5));
        assertEquals(0.3, rate(r.data(), 3), 0.015);
        assertEquals(MechanismClass.MAR, r.mechanism().overall());
        // High X0 deletes X3, and X3 tracks X0, so the observed X3 mean falls.
        assertTrue(observedMean(r.data(), d, 3) < -0.1);
    }

    @Test
    public void driverWithMissingValuesIsNotCertifiedMar() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.0), Mechanism.none().withValueEffect(3, 1, 1.0));
        assertEquals(MechanismClass.MNAR, r.mechanism().perVariable().get(3));
    }

    @Test
    public void selfMaskingDeletesLowValuesAndIsFlagged() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.5), Mechanism.none().withSelfMasking(2, -2.0));
        assertEquals(0.3, rate(r.data(), 2), 0.015);
        assertEquals(MechanismClass.MNAR, r.mechanism().overall());
        assertTrue(r.mechanism().selfMasking()[2]);
        assertTrue(observedMean(r.data(), d, 2) > 0.3);
    }

    @Test
    public void colluderIsFlaggedAndIndicatorEffectActs() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.0),
                Mechanism.none().withValueEffect(3, 1, 1.0).withIndicatorEffect(3, 1, 2.0));
        assertTrue(r.mechanism().colluded()[3]);
        assertFalse(r.mechanism().colluded()[1]);
        assertEquals(0.3, rate(r.data(), 3), 0.015);
        assertTrue(conditionalMissing(r.data(), 3, 1) > 0.45);
    }

    @Test
    public void indicatorDriverIsRealizedFirstWhateverItsIndex() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.0), Mechanism.none().withIndicatorEffect(1, 4, 3.0));
        assertTrue(conditionalMissing(r.data(), 1, 4) > 0.6);
        assertEquals(MechanismClass.MCAR, r.mechanism().overall());
    }

    @Test
    public void indicatorCycleIsRejected() {
        DataSet d = chain(100, 3);
        assertThrows(IllegalArgumentException.class, () -> MissingnessInjector.inject(d, new Spec(RATES, 0.0),
                Mechanism.none().withIndicatorEffect(1, 2, 1.0).withIndicatorEffect(2, 1, 1.0)));
    }

    @Test
    public void attritionIsMonotoneCalibratedAndSequentialMar() {
        DataSet d = chain(N, 3);
        double[] rates = {0.0, 0.1, 0.2, 0.3, 0.4};
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(rates, 1.0),
                Mechanism.none().withAttrition(1, 2, 3, 4).withValueEffect(3, 2, -1.5).withValueEffect(2, 0, 1.0));
        for (int j = 1; j < 5; j++) assertEquals(rates[j], rate(r.data(), j), 0.015);
        assertEquals(N, r.mechanism().monotoneRows());
        assertEquals(0.0, r.mechanism().earlierMissingGivenLater(), 0.0);
        assertEquals(MechanismClass.MAR, r.mechanism().overall());
    }

    @Test
    public void attritionDrivenByLaterVariableIsMnar() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(new double[]{0.0, 0.1, 0.2, 0.3, 0.4}, 0.0),
                Mechanism.none().withAttrition(1, 2, 3, 4).withValueEffect(2, 3, 1.0));
        assertEquals(MechanismClass.MNAR, r.mechanism().overall());
    }

    @Test
    public void decreasingAttritionRatesAreRejected() {
        DataSet d = chain(100, 3);
        assertThrows(IllegalArgumentException.class, () -> MissingnessInjector.inject(d,
                new Spec(new double[]{0.0, 0.3, 0.2, 0.3, 0.4}, 0.0), Mechanism.none().withAttrition(1, 2, 3, 4)));
    }

    @Test
    public void mcarMonotonicityMeasureMatchesItsExpectation() {
        DataSet d = chain(N, 3);
        RandomUtil.getInstance().setSeed(1L);
        Result r = MissingnessInjector.inject(d, new Spec(RATES, 0.0));
        // Pairs pooled by earlier-variable count: (0 + 0.3 + 0.6 + 0.9) / (1 + 2 + 3 + 4) = 0.18.
        assertEquals(0.18, r.mechanism().earlierMissingGivenLater(), 0.01);
        assertEquals(MechanismClass.MCAR, r.mechanism().overall());
    }

    // ---------------------------------------------------------------- helpers

    /** A linear Gaussian chain X0 -> X1 -> ... -> X4 with coefficient 0.8. */
    private static DataSet chain(int n, long seed) {
        Random random = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 5; j++) vars.add(new ContinuousVariable("X" + j));
        double[][] x = new double[n][5];
        for (int i = 0; i < n; i++) {
            x[i][0] = random.nextGaussian();
            for (int j = 1; j < 5; j++) x[i][j] = 0.8 * x[i][j - 1] + random.nextGaussian();
        }
        return new BoxDataSet(new DoubleDataBox(x), vars);
    }

    private static double rate(DataSet d, int j) {
        int c = 0;
        for (int i = 0; i < d.getNumRows(); i++) if (Double.isNaN(d.getDouble(i, j))) c++;
        return c / (double) d.getNumRows();
    }

    private static double observedMean(DataSet withMissing, DataSet full, int j) {
        double s = 0.0;
        int c = 0;
        for (int i = 0; i < withMissing.getNumRows(); i++) {
            if (!Double.isNaN(withMissing.getDouble(i, j))) {
                s += full.getDouble(i, j);
                c++;
            }
        }
        return s / c;
    }

    /** P(target missing | driver missing). */
    private static double conditionalMissing(DataSet d, int target, int driver) {
        int both = 0, driverMissing = 0;
        for (int i = 0; i < d.getNumRows(); i++) {
            if (Double.isNaN(d.getDouble(i, driver))) {
                driverMissing++;
                if (Double.isNaN(d.getDouble(i, target))) both++;
            }
        }
        return both / (double) driverMissing;
    }
}
