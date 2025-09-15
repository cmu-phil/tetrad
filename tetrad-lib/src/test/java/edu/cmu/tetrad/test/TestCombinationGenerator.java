/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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



