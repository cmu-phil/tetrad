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

import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TestBooleanFunction extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestBooleanFunction(String name) {
        super(name);
    }

    /**
     * Tests to make sure that null parent throw an exception.
     */
    public void testNullConstruction() {
        List parents = new ArrayList();
        for (int i = 0; i < 5; i++) {
            parents.add(new IndexedParent(1, 2));
        }
        parents.set(RandomUtil.getInstance().nextInt(5), null);

        try {
            new BooleanFunction(
                    (IndexedParent[]) parents.toArray(new IndexedParent[0]));
        }
        catch (NullPointerException e) {
            return;
        }

        fail("Should have thrown an NullPointerException.");
    }

    /**
     * Tests to make sure the table is the correct size.
     */
    public void testTableSize() {
        int numParents = RandomUtil.getInstance().nextInt(5);

        List parents = new ArrayList();
        for (int i = 0; i < numParents; i++) {
            parents.add(new IndexedParent(1, 2));
        }

        BooleanFunction function = new BooleanFunction(
                (IndexedParent[]) parents.toArray(new IndexedParent[0]));

        int size = 1;
        for (int i = 0; i < numParents; i++) {
            size *= 2;
        }

        assertEquals(size, function.getNumRows());
    }

    /**
     * Tests whether rows are stored in the correct order.
     */
    public void testRowOrder() {

        // Create an AND function the hard way.
        boolean[] values = new boolean[2];
        int row = 0;

        // Set up the function object with two parents.
        IndexedParent x = new IndexedParent(1, 2);
        IndexedParent y = new IndexedParent(2, 1);
        IndexedParent[] twoParents = new IndexedParent[]{x, y};
        BooleanFunction function = new BooleanFunction(twoParents);

        // Set the first row to true.
        values[0] = true;
        values[1] = true;
        row = function.getRow(values);
        function.setValue(row, true);

        // Set the second row to false.
        values[0] = true;
        values[1] = false;
        row = function.getRow(values);
        function.setValue(row, false);

        // Set the third row to false.
        values[0] = false;
        values[1] = true;
        row = function.getRow(values);
        function.setValue(row, false);

        // Set the fourth row to false.
        values[0] = false;
        values[1] = false;
        row = function.getRow(values);
        function.setValue(row, false);

        // Now see if the values are in the right order.
        assertEquals(true, function.getValue(0));
        assertEquals(false, function.getValue(1));
        assertEquals(false, function.getValue(2));
        assertEquals(false, function.getValue(3));
    }

    /**
     * Tests to see whether some known effective functions pass the
     * isEffective() test. Also tests to see whether some known non-effective
     * functions fail.
     */
    public void testIsEffective() {
        IndexedParent x = new IndexedParent(0, 2);
        IndexedParent y = new IndexedParent(1, 2);
        IndexedParent z = new IndexedParent(2, 3);
        IndexedParent[] threeParents = new IndexedParent[]{x, y, z};
        BooleanFunction function = null;

        // The following 3-parent function should pass.
        function = new BooleanFunction(threeParents);
        function.setValue(0, true);
        function.setValue(1, false);
        function.setValue(2, false);
        function.setValue(3, true);
        function.setValue(4, false);
        function.setValue(5, false);
        function.setValue(6, false);
        function.setValue(7, false);

        assertTrue(function.isEffective());

        // This following 3-parent function should fail.
        function = new BooleanFunction(threeParents);
        function.setValue(0, true);
        function.setValue(1, false);
        function.setValue(2, false);
        function.setValue(3, true);
        function.setValue(4, true);
        function.setValue(5, false);
        function.setValue(6, false);
        function.setValue(7, true);

        assertTrue(!(function.isEffective()));
    }

    /**
     * Tests to see whether some known canalyzing functions (AND, OR, ...) pass
     * the isCanalyzing() test. Also tests to see whether some known
     * non-canalyzing functions (exclusive-OR, ...) fail.
     */
    public void testIsCanalyzing() {
        IndexedParent x = new IndexedParent(0, 1);
        IndexedParent y = new IndexedParent(1, 2);
        IndexedParent z = new IndexedParent(2, 3);
        IndexedParent[] twoParents = new IndexedParent[]{x, y};
        IndexedParent[] threeParents = new IndexedParent[]{x, y, z};
        BooleanFunction function = null;

        // AND should pass the isCanalyzing() test.
        function = new BooleanFunction(twoParents);
        function.setValue(0, true);
        function.setValue(1, false);
        function.setValue(2, false);
        function.setValue(3, false);

        assertTrue(function.isCanalyzing());

        // Inclusive OR should pass the isCanalyzing() test.
        function = new BooleanFunction(twoParents);
        function.setValue(0, true);
        function.setValue(1, true);
        function.setValue(2, true);
        function.setValue(3, false);

        assertTrue(function.isCanalyzing());

        // Exclusive OR should fail the isCanalyzing() test.
        function = new BooleanFunction(twoParents);
        function.setValue(0, false);
        function.setValue(1, true);
        function.setValue(2, true);
        function.setValue(3, false);

        assertTrue(!(function.isCanalyzing()));

        // The following 3-parent function should fail for y and z but
        // pass for x, thereby passing.
        function = new BooleanFunction(threeParents);
        function.setValue(0, true);
        function.setValue(1, false);
        function.setValue(2, false);
        function.setValue(3, true);
        function.setValue(4, false);
        function.setValue(5, false);
        function.setValue(6, false);
        function.setValue(7, false);

        assertTrue(function.isCanalyzing());

        // This slight variation of the previous function should fail
        // for all three parents, thereby failing.
        function = new BooleanFunction(threeParents);
        function.setValue(0, true);
        function.setValue(1, false);
        function.setValue(2, false);
        function.setValue(3, true);
        function.setValue(4, false);
        function.setValue(5, false);
        function.setValue(6, false);
        function.setValue(7, true);

        assertTrue(!(function.isCanalyzing()));
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBooleanFunction.class);
    }
}





