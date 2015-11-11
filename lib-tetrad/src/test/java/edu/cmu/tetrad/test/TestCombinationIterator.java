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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.CombinationIterator;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Iterator;

public class TestCombinationIterator extends TestCase {
    public TestCombinationIterator(String name) {
        super(name);
    }

    public void test() {
//        int[] values = {2, 2, 2, 2};
        int[] values = {3, 2, 2, 5};
        int counter = 1;

        Iterator i = new CombinationIterator(values);
        int[] combination = (int[]) i.next();

        /** check first combination */
        assertTrue(Arrays.equals(combination, new int[]{0, 0, 0, 0}));

        /** check expected count */
        while (i.hasNext()) {
            combination = (int[]) i.next();

            for (int aCombination : combination) {
                System.out.print(aCombination + "\t");
            }
            System.out.println();

            counter++;
        }
        int expectedCount = 1;
        for (int value : values) {
            expectedCount *= value;
        }

        System.out.println("expected count = " + expectedCount);
        System.out.println("actual count = " + counter);
        assertEquals(expectedCount, counter);

        /** check last combination matches the value array */
        int[] lastCombination = combination; /** should be {2, 1, 1} */
        for (int j = 0; j < values.length; j++) {
            lastCombination[j]++;
        }
        assertTrue(Arrays.equals(lastCombination, values));
    }
}





