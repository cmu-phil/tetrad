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

import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 * Implements some tests of the FDR (False Discovery Rate) test.
 *
 * @author josephramsey
 */
public class TestFdr {

    @Test
    public void testSimpleCase() {
        double[] p = {
                .8, .01, .2, .07, .003, .9, .05, .03, .0001
        };

        List<Double> pValues = new ArrayList<>();
        for (double _p : p) pValues.add(_p);

        final double alpha = 0.05;
        boolean negativelyCorrelated = false;

        double cutoff = StatUtils.fdrCutoff(alpha, pValues, negativelyCorrelated);

        assertEquals(.01, cutoff, .0001);

        negativelyCorrelated = true;
        cutoff = StatUtils.fdrCutoff(alpha, pValues, negativelyCorrelated);
        assertEquals(0.003, cutoff, .0001);

    }
}



