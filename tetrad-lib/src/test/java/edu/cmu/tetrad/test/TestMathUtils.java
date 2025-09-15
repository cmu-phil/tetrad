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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.MathUtils;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Tests some extra mathematical functions not contained in org.apache.commons.math3.util.FastMath.
 *
 * @author josephramsey
 */
public class TestMathUtils {

    @Test
    public void testLogistic() {
        assertEquals(0.5, MathUtils.logistic(0.), 0.0);
        assertTrue(MathUtils.logistic(-10.) < 1.e-4);
        assertTrue(MathUtils.logistic(+10.) > 1. - 1.e-4);
    }

    @Test
    public void testExpSums() {
        final double d = 100.0;

        assertEquals(7.22E86,
                FastMath.exp(d + d), 1E86);
        assertEquals(7.22E86, FastMath.exp(d) * FastMath.exp(d), 1E86);
    }
}



