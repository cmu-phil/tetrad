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

import edu.cmu.tetrad.util.Function;
import edu.cmu.tetrad.util.Integrator;
import edu.cmu.tetrad.util.PartialCorrelationPdf;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestPartialCorrelationPdf {
    private Function function;

    public void setUp() {
        this.function = new PartialCorrelationPdf(1000, 5);
    }

    @Test
    public void testIntegralSumToOne() {
        setUp();
        final String message = "Integrator does not properly integrate a p.d.f.";
        double area = Integrator.getArea(this.function, -1.0, 1.0, 10000);
        final double tolerance = 0.000001;
        assertEquals(message, 1.0, area, tolerance);
    }
}






