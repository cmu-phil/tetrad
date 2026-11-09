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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for per-variable logarithmic transforms.
 *
 * @author josephramsey
 */
public class TestLogTransform {

    /**
     * A dataset with a small-valued column, a large-valued column, a column containing zero, and a discrete column,
     * mirroring the airfoil-self-noise case that motivated per-variable transforms.
     */
    private static DataSet mixedScaleData() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("Small"));      // 0.0004 .. 0.06
        vars.add(new ContinuousVariable("Large"));      // 200 .. 20000
        vars.add(new ContinuousVariable("HasZero"));    // 0 .. 22
        vars.add(new DiscreteVariable("D", List.of("0", "1")));

        int n = 50;
        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, 0.0004 + i * 0.001);
            data.setDouble(i, 1, 200.0 + i * 400.0);
            data.setDouble(i, 2, i * 0.45);
            data.setInt(i, 3, i % 2);
        }

        return data;
    }

    /**
     * The dataset-wide entry point must behave exactly as before now that it delegates to the per-variable one:
     * every continuous column logged with the shared offset, discrete columns untouched.
     */
    @Test
    public void testUniformModeUnchanged() {
        DataSet data = mixedScaleData();
        DataSet out = DataTransforms.logData(data, 10.0, false, 0);

        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals("Continuous column " + j + " must be logged with the shared offset",
                        Math.log(10.0 + data.getDouble(i, j)), out.getDouble(i, j), 1e-12);
            }

            assertEquals("Discrete columns must pass through unchanged",
                    data.getInt(i, 3), out.getInt(i, 3));
        }
    }

    /**
     * Base handling in uniform mode is likewise unchanged.
     */
    @Test
    public void testUniformModeWithBase() {
        DataSet data = mixedScaleData();
        DataSet out = DataTransforms.logData(data, 1.0, false, 10);

        assertEquals(Math.log(1.0 + data.getDouble(3, 1)) / Math.log(10),
                out.getDouble(3, 1), 1e-12);
    }

    /**
     * Variables absent from the spec map are passed through unchanged, and each named variable uses its own offset
     * and base. This is the airfoil case: log Large with no offset, leave HasZero alone.
     */
    @Test
    public void testPerVariableTransform() {
        DataSet data = mixedScaleData();

        Map<String, LogTransformSpec> specs = new LinkedHashMap<>();
        specs.put("Small", new LogTransformSpec(0.0, 0, false));
        specs.put("Large", new LogTransformSpec(0.0, 10, false));

        DataSet out = DataTransforms.logData(data, specs);

        for (int i = 0; i < data.getNumRows(); i++) {
            assertEquals("Small must be logged with offset 0 and natural base",
                    Math.log(data.getDouble(i, 0)), out.getDouble(i, 0), 1e-12);
            assertEquals("Large must be logged with offset 0 and base 10",
                    Math.log(data.getDouble(i, 1)) / Math.log(10), out.getDouble(i, 1), 1e-12);
            assertEquals("HasZero was not named and must pass through unchanged",
                    data.getDouble(i, 2), out.getDouble(i, 2), 1e-12);
            assertEquals("Discrete columns must pass through unchanged",
                    data.getInt(i, 3), out.getInt(i, 3));
        }
    }

    /**
     * A discrete variable is never transformed, even if it is named in the spec map.
     */
    @Test
    public void testDiscreteNeverTransformed() {
        DataSet data = mixedScaleData();

        Map<String, LogTransformSpec> specs = new LinkedHashMap<>();
        specs.put("D", new LogTransformSpec(1.0, 0, false));

        DataSet out = DataTransforms.logData(data, specs);

        for (int i = 0; i < data.getNumRows(); i++) {
            assertEquals(data.getInt(i, 3), out.getInt(i, 3));
        }
    }

    /**
     * Logging then unlogging with the same spec returns the original values.
     */
    @Test
    public void testRoundTrip() {
        DataSet data = mixedScaleData();

        Map<String, LogTransformSpec> forward = new LinkedHashMap<>();
        forward.put("Large", new LogTransformSpec(0.0, 10, false));

        Map<String, LogTransformSpec> backward = new LinkedHashMap<>();
        backward.put("Large", new LogTransformSpec(0.0, 10, true));

        DataSet out = DataTransforms.logData(DataTransforms.logData(data, forward), backward);

        for (int i = 0; i < data.getNumRows(); i++) {
            assertEquals(data.getDouble(i, 1), out.getDouble(i, 1), 1e-9);
        }
    }

    /**
     * The encoding used to store specs in a session round-trips.
     */
    @Test
    public void testEncodeDecode() {
        Map<String, LogTransformSpec> specs = new LinkedHashMap<>();
        specs.put("Small", new LogTransformSpec(0.0, 0, false));
        specs.put("Large", new LogTransformSpec(2.5, 10, true));

        Map<String, LogTransformSpec> decoded = LogTransformSpec.decode(LogTransformSpec.encode(specs));

        assertEquals(specs, decoded);
        assertTrue("An empty encoding must decode to an empty map, which selects uniform mode",
                LogTransformSpec.decode("").isEmpty());
        assertTrue("A null encoding must decode to an empty map",
                LogTransformSpec.decode(null).isEmpty());
        assertEquals("An empty map must encode to the empty string", "",
                LogTransformSpec.encode(new LinkedHashMap<>()));
    }

    /**
     * The safe-offset helper proposes 0 for a strictly positive column and something that makes a column with zero
     * or negative values loggable.
     */
    @Test
    public void testSafeOffset() {
        assertEquals(0.0, LogTransformSpec.safeOffsetFor(new double[]{1.0, 2.0, 0.5}), 0.0);

        double[] withZero = {0.0, 1.0, 22.2};
        double offset = LogTransformSpec.safeOffsetFor(withZero);
        assertTrue("Offset must make the minimum strictly positive", offset > 0.0);

        for (double x : withZero) {
            assertTrue("Logging with the safe offset must be finite",
                    Double.isFinite(Math.log(offset + x)));
        }
    }
}
