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
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests that the data audit cooperates with thread interruption (so the GUI's stop dialog can cancel it) and that the
 * per-cell missingness test and the normal CDF are cheap enough for the audit to finish promptly on a wide dataset.
 * Before the corresponding fixes, MissingDataAudit.isMissing copied the whole variable list into a LinkedList on
 * every call and RandomUtil.normalCdf seeded a fresh random generator on every call; on a 148 x 5000 dataset the
 * audit then took minutes on the event thread with no way to stop it.
 *
 * @author josephramsey
 */
public class TestDataAuditInterruption {

    private static DataSet mixedData(int n, int pc, int pd, long seed) {
        Random rnd = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < pc; j++) vars.add(new ContinuousVariable("X" + j));
        for (int j = 0; j < pd; j++) vars.add(new DiscreteVariable("D" + j, 3));
        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < pc; j++) d.setDouble(i, j, rnd.nextGaussian());
            for (int j = 0; j < pd; j++) d.setInt(i, pc + j, rnd.nextInt(3));
        }

        return d;
    }

    /**
     * An interrupted thread constructing the audit gets a RuntimeException caused by an InterruptedException (the
     * form the GUI recognizes as a user stop), not a completed audit and not some other failure.
     */
    @Test
    public void testInterruptedThreadAbortsAudit() throws Exception {
        DataSet d = mixedData(300, 20, 5, 1);
        Throwable[] thrown = new Throwable[1];
        boolean[] finished = new boolean[1];

        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                new DataAudit(d);
                finished[0] = true;
            } catch (Throwable e) {
                thrown[0] = e;
            }
        });

        t.start();
        t.join(60_000);

        assertFalse("Audit should not complete on an interrupted thread", finished[0]);
        assertNotNull(thrown[0]);
        assertTrue(thrown[0] instanceof RuntimeException);
        assertTrue("Cause should be InterruptedException, was " + thrown[0].getCause(),
                thrown[0].getCause() instanceof InterruptedException);
    }

    /**
     * An uninterrupted audit of the same data completes normally.
     */
    @Test
    public void testUninterruptedAuditCompletes() {
        DataSet d = mixedData(300, 20, 5, 1);
        DataAudit audit = new DataAudit(d);
        assertNotNull(audit.getFindings());
    }

    /**
     * The per-cell missingness test must not scale with the number of columns. Ten million calls on a 148-column
     * dataset take about 0.06 s with the O(1) variable lookup and about 4.6 s with the old list copy; the bound
     * sits between, with a wide margin on each side.
     */
    @Test
    public void testIsMissingIsCheapOnWideData() {
        DataSet d = mixedData(1000, 100, 48, 2);
        int p = d.getNumColumns();
        long t0 = System.nanoTime();
        int missing = 0;

        for (int k = 0; k < 10_000_000; k++) {
            if (MissingDataAudit.isMissing(d, k % 1000, k % p)) missing++;
        }

        double seconds = (System.nanoTime() - t0) / 1e9;
        assertEquals(0, missing);
        assertTrue("isMissing took " + seconds + " s for 1e7 calls; expected < 1.5 s", seconds < 1.5);
    }

    /**
     * normalCdf must not pay a distribution construction per call. One million calls take tens of milliseconds
     * when the standard normal is cached and several seconds when it is rebuilt each time. Also checks a few values
     * against known constants, since the fix changed how the distribution is constructed.
     */
    @Test
    public void testNormalCdfIsCheapAndCorrect() {
        RandomUtil ru = RandomUtil.getInstance();
        assertEquals(0.5, ru.normalCdf(0, 1, 0), 1e-12);
        assertEquals(0.8413447460685429, ru.normalCdf(0, 1, 1), 1e-12);
        assertEquals(0.9772498680518208, ru.normalCdf(1, 2, 5), 1e-12);

        long t0 = System.nanoTime();
        double s = 0;
        for (int k = 0; k < 1_000_000; k++) s += ru.normalCdf(0, 1, (k % 2000 - 1000) / 250.0);
        double seconds = (System.nanoTime() - t0) / 1e9;
        assertTrue(s > 0);
        assertTrue("normalCdf took " + seconds + " s for 1e6 calls; expected < 2 s", seconds < 2);
    }

    /**
     * End to end: the default audit of a 148 x 5000 mixed dataset finishes in reasonable time. This took longer
     * than ten minutes before the fixes; the bound is generous to allow for slow machines.
     */
    @Test
    public void testWideAuditFinishesPromptly() {
        DataSet d = mixedData(5000, 100, 48, 3);
        long t0 = System.nanoTime();
        new DataAudit(d);
        double seconds = (System.nanoTime() - t0) / 1e9;
        assertTrue("Audit of 148 x 5000 took " + seconds + " s; expected < 120 s", seconds < 120);
    }
}
