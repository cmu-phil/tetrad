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

import edu.cmu.tetrad.util.CombinationIterator;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestCombinationIterator {

    @Test
    public void test() {
        int[] values = {3, 2, 2, 5};
        int counter = 1;

        Iterator i = new CombinationIterator(values);
        int[] combination = (int[]) i.next();

        /* check first combination */
        assertArrayEquals(combination, new int[]{0, 0, 0, 0});

        /* check expected count */
        while (i.hasNext()) {
            combination = (int[]) i.next();
            counter++;
        }
        int expectedCount = 1;
        for (int value : values) {
            expectedCount *= value;
        }

        assertEquals(expectedCount, counter);

        /* check last combination matches the value array */
        int[] lastCombination = combination; /* should be {2, 1, 1} */
        for (int j = 0; j < values.length; j++) {
            lastCombination[j]++;
        }
        assertArrayEquals(lastCombination, values);
    }
}






