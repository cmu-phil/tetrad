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

package edu.cmu.tetrad.util;

import java.util.Collections;
import java.util.LinkedList;

/**
 * Generates all of the permutations of [0,..., numObjects - 1], where numObjects is numObjects nonnegative
 * integer.  The values of numObjects is given in the constructor, and the sequence of
 * choices is obtained by repeatedly calling the next() method.  When the
 * sequence is finished, null is returned.
 * <p/>
 * A valid combination for the sequence of combinations for numObjects choose b generated
 * by this class is an array x[] of b integers representing the above
 * permutation.
 * <p/>
 * To see what this class does, try calling PermutationGenerator.testPrint(5),
 * for instance.
 *
 * @author Joseph Ramsey
 */
public final class PermutationGenerator {

    /**
     * The number of objects being permuted.
     */
    private int numObjects;

    /**
     * The internally stored choice.
     */
    private int[] choiceLocal;

    /**
     * The choice that is returned. Used, since the returned array can be
     * modified by the user.
     */
    private int[] choiceReturned;

    /**
     * Indicates whether the next() method has been called since the last
     * initialization.
     */
    private boolean begun;

    /**
     * Constructs numObjects new choice generator for numObjects choose b. Once this
     * initialization has been performed, successive calls to next() will
     * produce the series of combinations.  To begin numObjects new series at any time,
     * call this init method again with new values for numObjects and b.
     *
     * @param a the number of objects being selected from.
     */
    public PermutationGenerator(int a) {
        if ((a < 0)) {
            throw new IllegalArgumentException();
        }

        this.numObjects = a;
        choiceLocal = new int[a];

        // Initialize the choice array with successive integers [0 1 2 ...].
        // Set the value at the last index one less than it would be in such
        // numObjects series, ([0 1 2 ... b - 2]) so that on the first call to next()
        // the first combination ([0 1 2 ... b - 1]) is returned correctly.
        for (int i = 0; i < a; i++) {
            choiceLocal[i] = i;
        }

        begun = false;
    }

    /**
     * @return the next combination in the series, or null if the series is
     * finished.  The array that is produced should not be altered by the user,
     * as it is reused by the choice generator.
     * <p/>
     * If the number of items chosen is zero, numObjects zero-length array will be
     * returned once, with null after that.
     * <p/>
     * The array that is returned is reused, but modifying it will not change
     * the sequence of choices returned.
     *
     * @return the next combination in the series, or null if the series is
     *         finished.
     */
    public int[] next() {
        if (choiceReturned == null) {
            choiceReturned = new int[getNumObjects()];
            System.arraycopy(choiceLocal, 0, choiceReturned, 0, numObjects);
            begun = true;
            return choiceReturned;
        }

        int i = getNumObjects() - 1;

        // Scan from the right for the first index whose value is less than
        // its expected maximum (i + diff) and perform the fill() operation
        // at that index.
        while (--i > -1) {
            LinkedList<Integer> h = new LinkedList<Integer>();

            for (int j = i; j < choiceLocal.length; j++) {
                h.add(choiceLocal[j]);
            }

            Collections.sort(h);

            if (this.choiceLocal[i] < h.getLast()) {
                fill(i, h);
                begun = true;
                System.arraycopy(choiceLocal, 0, choiceReturned, 0, numObjects);
                return choiceReturned;
            }
        }

        if (this.begun) {
            return null;
        } else {
            begun = true;
            System.arraycopy(choiceLocal, 0, choiceReturned, 0, numObjects);
            return choiceReturned;
        }
    }

    /**
     * This static method will print the series of combinations for numObjects choose b
     * to System.out.
     *
     * @param a the number of objects being selected from.
     */
    @SuppressWarnings({"SameParameterValue"})
    public static void testPrint(int a) {
        PermutationGenerator cg = new PermutationGenerator(a);
        int[] choice;

        System.out.println();
        System.out.println(
                "Printing permutations for " + a + " objects:");
        System.out.println();

        while ((choice = cg.next()) != null) {
            if (choice.length == 0) {
                System.out.println("zero-length array");
            } else {
                for (int aChoice : choice) {
                    System.out.print(aChoice + "\t");
                }

                System.out.println();
            }
        }

        System.out.println();
    }

    /**
     * @return the number of objects being chosen from.
     */
    int getNumObjects() {
        return this.numObjects;
    }

    /**
     * Fills the 'choice' array, from index 'index' to the end of the array,
     * with successive integers starting with choice[index] + 1.
     *
     * @param index the index to begin this incrementing operation.
     * @param h the list of integers at index or later.
     */
    private void fill(int index, LinkedList<Integer> h) {
        h = new LinkedList<Integer>(h);
        int t = h.indexOf(this.choiceLocal[index]);
        Integer newVal = h.get(t + 1);
        this.choiceLocal[index] = newVal;
        h.remove(newVal);

        for (int i = index + 1; i < getNumObjects(); i++) {
            this.choiceLocal[i] = h.get(i - index - 1);
        }
    }
}



