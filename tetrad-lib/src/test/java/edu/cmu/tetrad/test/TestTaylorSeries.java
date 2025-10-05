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

import edu.cmu.tetrad.util.TaylorSeries;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestTaylorSeries {

    @Test
    public void testTaylorSeries() {

        // Example 1: Derivatives for f(x) = 1 - x^2 at a = 0
        double[] derivatives = {1.0, 0.0, -2.0, 0.0}; // f(x) = 1 - x^2

        double x = 0.5;
        System.out.println("f(" + x + ") = " + TaylorSeries.get(derivatives, 0).evaluate(x) + ", actual value: " + (1 - x * x));

        // Print the Taylor series
        TaylorSeries.get(derivatives, 0).printSeries();

        assertEquals(0.75, TaylorSeries.get(derivatives, 0).evaluate(x), 1e-10);

        // Example 2: Derivatives for e^x at a = 1 (f^(n)(a) = e^1 = e for all n)
        int numTerms = 30;
        double[] derivatives2 = new double[numTerms];
        Arrays.fill(derivatives2, Math.E);

        double x2 = 1.5; // Point to evaluate the Taylor series
        double a2 = 1.0; // Center of the series

        System.out.println("f(" + x + ") = " + TaylorSeries.get(derivatives2, a2).evaluate(x2));

        double result = TaylorSeries.get(derivatives2, a2).evaluate(x2);
        System.out.println("Taylor series approximation: " + result + ", actual value: " + Math.exp(x2));

        TaylorSeries.get(derivatives2, a2).printSeries();

        assertEquals(Math.exp(x2), TaylorSeries.get(derivatives2, a2).evaluate(x2), 1e-10);
    }
}

