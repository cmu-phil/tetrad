///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.gene.tetrad.gene.history;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the PolynomialTerm class.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TestPolynomialTerm extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestPolynomialTerm(String name) {
        super(name);
    }

    /**
     * Tests to make sure that null parent throw an exception.
     */
    public void testConstruction() {
        PolynomialTerm term = new PolynomialTerm(0.5, new int[]{1, 2, 0});

        assertEquals(0.5, term.getCoefficient(), 0.00001);
        assertEquals(1, term.getVariable(0));
        assertEquals(2, term.getVariable(1));
        assertEquals(0, term.getVariable(2));
        System.out.println(term);
    }

    /**
     * Test the evaluation of terms.
     */
    public void testEvaluation() {
        PolynomialTerm term = new PolynomialTerm(0.5, new int[]{0, 1, 1, 2});
        double[] values = new double[]{1.0, 2.0, 3.0};
        assertEquals(6.0, term.evaluate(values), 0.00001);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestPolynomialTerm.class);
    }
}





