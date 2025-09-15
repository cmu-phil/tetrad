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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the Polynomial class.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestPolynomial extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     *
     * @param name a {@link java.lang.String} object
     */
    public TestPolynomial(String name) {
        super(name);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     *
     * @return a {@link junit.framework.Test} object
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestPolynomial.class);
    }

    /**
     * Tests to make sure that null parent throw an exception.
     */
    public void testConstruction() {
        PolynomialTerm term0 = new PolynomialTerm(1.0, new int[]{0});
        PolynomialTerm term1 = new PolynomialTerm(1.0, new int[]{1});
        PolynomialTerm term2 = new PolynomialTerm(1.0, new int[]{2, 3});

        List terms = new ArrayList();
        terms.add(term0);
        terms.add(term1);
        terms.add(term2);

        Polynomial p = new Polynomial(terms);

        System.out.println(p);
    }

    /**
     * Test the evaluation of terms.
     */
    public void testEvaluation() {
        PolynomialTerm term0 = new PolynomialTerm(1.0, new int[]{0});
        PolynomialTerm term1 = new PolynomialTerm(1.0, new int[]{1});
        PolynomialTerm term2 = new PolynomialTerm(1.0, new int[]{2, 3});

        List terms = new ArrayList();
        terms.add(term0);
        terms.add(term1);
        terms.add(term2);

        Polynomial p = new Polynomial(terms);

        double[] values = {1.0, 2.0, 3.0, 4.0};

        assertEquals(15.0, p.evaluate(values), 0.00001);
    }
}






