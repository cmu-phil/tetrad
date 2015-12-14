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

import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.PermutationGenerator;
import edu.cmu.tetrad.util.SelectionGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Implements basic tests of the choice generator. The choice generator should visit every
 * choice in a choose b exactly once, and then return null.
 *
 * @author Joseph Ramsey
 */
public class TestChoiceGenerator {

    /**
     * Prints all of the choices for the given a and b.
     */
    @Test
    public void testPrintChoiceGenerator() {
        int a = 10;
        int b = 3;

        int numCombinations = ChoiceGenerator.getNumCombinations(a, b);

        assertEquals(120, numCombinations);
    }

    @Test
    public void testPrintDepthChoiceGenerator() {
        int a = 4;
        int b = 2;

        int numCombinations = DepthChoiceGenerator.getNumCombinations(a, b);

        assertEquals(11, numCombinations);
    }

    @Test
    public void testPrintPermutationGenerator() {
        PermutationGenerator gen = new PermutationGenerator(4);
        int count = 0;

        while (gen.next() != null) {
            count++;
        }

        assertEquals(24, count);
    }

    @Test
    public void testPrintSelectionGenerator() {
        SelectionGenerator gen = new SelectionGenerator(4);
        int count = 0;

        while (gen.next() != null) {
            count++;
        }

        assertEquals(256, count);
    }

    /**
     * Tests to make sure the ChoiceGenerator is output the correct number of choices
     * for various values of and b.
     */
    @Test
    public void testChoiceGeneratorCounts() {
        for (int a = 0; a <= 20; a++) {
            for (int b = 0; b <= a; b++) {
                ChoiceGenerator generator = new ChoiceGenerator(a, b);

                int n = 0;

                while ((generator.next()) != null) {
                    n++;
                }

                long numerator = 1;
                long denominator = 1;

                for (int k = a; k - b > 0; k--) {
                    numerator *= k;
                    denominator *= k - b;
                }

                long numChoices = numerator / denominator;

                if (n != numChoices) {
                    fail("a = " + a + " b = " + b + " numChoices = " + numChoices + " n = " + n);
                }
            }
        }
    }


}





