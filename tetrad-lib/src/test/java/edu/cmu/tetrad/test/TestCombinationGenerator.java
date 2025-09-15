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

import edu.cmu.tetrad.util.CombinationGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestCombinationGenerator {

    @Test
    public void test1() {
        CombinationGenerator gen = new CombinationGenerator(new int[]{5, 3});
        int count = 0;

        while (gen.next() != null) {
            count++;
        }

        assertEquals(15, count);
    }

    @Test
    public void test2() {
        CombinationGenerator gen = new CombinationGenerator(new int[]{2, 1});
        int count = 0;

        while (gen.next() != null) {
            count++;
        }

        assertEquals(2, count);
    }

    @Test
    public void test3() {
        CombinationGenerator gen = new CombinationGenerator(new int[]{2, 3, 4});
        int count = 0;

        while (gen.next() != null) {
            count++;
        }

        assertEquals(24, count);
    }
}




