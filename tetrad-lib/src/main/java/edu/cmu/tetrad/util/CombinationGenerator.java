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

/**
 * Generates (nonrecursively) all of the combinations of objects, where the
 * number of objects in each dimension is specified. The sequence of choices is
 * obtained by repeatedly calling the next() method.  When the sequence is
 * finished, null is returned.
 * <p>
 * A valid combination for the sequence of combinations for a choose b generated
 * by this class is an array x[] of b integers i, 0 <= i < a, such that x[j] <
 * x[j + 1] for each j from 0 to b - 1.
 *
 * @author Joseph Ramsey
 */
public final class CombinationGenerator {

    /**
     * The number of items for each dimension.
     */
    private final int[] dims;

    /**
     * The internally stored choice.
     */
    private final int[] local;

    /**
     * The choice that is returned. Used, since the returned array can be
     * modified by the user.
     */
    private final int[] returned;

    /**
     * Indicates whether the next() method has been called since the last
     * initialization.
     */
    private boolean begun;

    /**
     * Constructs a new combination of objects, choosing one object from
     * each dimension.
     *
     * @param dims the number of objects in each dimension. Each member must
     *             be >= 0.
     * @throws NullPointerException if dims is null.
     */
    public CombinationGenerator(int[] dims) {
        this.dims = dims;
        local = new int[dims.length];
        returned = new int[dims.length];

        // Initialize the combination array with successive integers [0 1 2 ...].
        // Set the value at the last index one less than it would be in such
        // a series, ([0 1 2 ... b - 2]) so that on the first call to next()
        // the first combination ([0 1 2 ... b - 1]) is returned correctly.
        for (int i = 0; i < dims.length - 1; i++) {
            local[i] = 0;
        }

        if (local.length > 0) {
            local[local.length - 1] = -1;
        }

        begun = false;
    }

    /**
     * @return the next combination in the series, or null if the series is
     * finished.
     */
    public int[] next() {
        int i = getNumObjects();

        // Scan from the right for the first index whose value is less than
        // its expected maximum (i + diff) and perform the fill() operation
        // at that index.
        while (--i > -1) {
            if (this.local[i] < dims[i] - 1) {
                fill(i);
                begun = true;
                System.arraycopy(local, 0, returned, 0, getNumObjects());
                return returned;
            }
        }

        if (this.begun) {
            return null;
        } else {
            begun = true;
            System.arraycopy(local, 0, returned, 0, getNumObjects());
            return returned;
        }
    }

    /**
     * This static method will print the series of combinations for a choose b
     * to System.out.
     *
     * @param dims An int array consisting of the number of dimensions for each
     *             variable, in order.
     */
    public static void testPrint(int[] dims) {
        CombinationGenerator cg = new CombinationGenerator(dims);
        int[] choice;

        System.out.println();
        System.out.print("Printing combinations for (");

        for (int i = 0; i < dims.length; i++) {
            System.out.print(dims[i]);

            if (i < dims.length - 1) {
                System.out.print(", ");
            }
        }

        System.out.println(")\n");

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
     * @return Ibid.
     */
    private int getNumObjects() {
        return local.length;
    }

    /**
     * Fills the 'choice' array, from index 'index' to the end of the array,
     * with successive integers starting with choice[index] + 1.
     *
     * @param index the index to begin this incrementing operation.
     */
    private void fill(int index) {
        this.local[index]++;

        for (int i = index + 1; i < getNumObjects(); i++) {
            local[i] = 0;
        }
    }
}



