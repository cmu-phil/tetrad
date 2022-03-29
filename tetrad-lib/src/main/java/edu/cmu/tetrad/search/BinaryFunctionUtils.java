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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.PermutationGenerator;

import java.util.HashSet;
import java.util.Set;

/**
 * A gadget to count truth table functions for Clark. Counting the number of truth table functions from a set of "cause"
 * binary variables to an outcome binary variable such that for each variable there is are two rows that differ only in
 * the value of that variable such that the function value for those two rows is different.
 *
 * @author Joseph Ramsey
 */
public class BinaryFunctionUtils {

    /**
     * The number of arguments of the binary function.
     */
    private final int numArgs;

    public BinaryFunctionUtils(final int numArgs) {
        this.numArgs = numArgs;
    }

//    public void printAllFunctions() {
//        for (int i = 0; i < getNumFunctions(); i++) {
//            System.out.println(new BinaryFunction(numArgs, i));
//        }
//    }

    public long getNumFunctions() {
        final int numRows = getNumRows();
        long numFunctions = 1L;

        for (int i = 0; i < numRows; i++) {
            numFunctions *= 2L;
        }
        return numFunctions;
    }

    public int getNumRows() {
        final BinaryFunction first = new BinaryFunction(this.numArgs, 0);
        return first.getNumRows();
    }

    public long count() {
        final Set<Integer> variables = new HashSet<>();
        for (int i = 0; i < this.numArgs; i++) variables.add(i);
        long numValidated = 0;
        final long numFunctions = getNumFunctions();
        final BinaryFunction function = new BinaryFunction(this.numArgs, 0);

        FUNCTION:
        for (long index = 0; index < numFunctions; index++) {
            if (index % 100000 == 0) {
                System.out.println("..." + index + " counted so far, " +
                        numValidated + " validated tables so far...");
            }

            function.resetFunction(index);
            final Set<Integer> validated = new HashSet<>();

            for (int row = 0; row < getNumRows(); row++) {
                final boolean[] vals = function.getRow(row);
                final boolean val1 = function.getValue(vals);

                for (final int v : variables) {
                    if (validated.contains(v)) continue;

                    vals[v] = !vals[v];
                    final boolean val2 = function.getValue(vals);

                    if (val1 != val2) {
                        validated.add(v);
                        if (validated.size() == this.numArgs) {
                            if (function.getOppositeFunction() < index) {
                                continue FUNCTION;
                            }

                            if (function.getSymmetricFunction() < index) {
                                continue FUNCTION;
                            }

                            if (function.getSymmetricOppositeFunction() < index) {
                                continue FUNCTION;
                            }

//                            System.out.println(new BinaryFunction(numArgs, index));

                            numValidated++;
                            continue FUNCTION;
                        }
                    }

                    vals[v] = !vals[v];
                }
            }
        }

        return numValidated;
    }

    public boolean satisfiesTestPair(final BinaryFunction function) {
        final Set<Integer> variables = new HashSet<>();
        for (int i = 0; i < this.numArgs; i++) variables.add(i);
        final Set<Integer> validated = new HashSet<>();

        for (int row = 0; row < getNumRows(); row++) {
            final boolean[] vals = function.getRow(row);
            final boolean val1 = function.getValue(vals);

            for (final int v : variables) {
                if (validated.contains(v)) continue;

                vals[v] = !vals[v];
                final boolean val2 = function.getValue(vals);

                if (val1 != val2) {
                    validated.add(v);
                    if (validated.size() == this.numArgs) {
                        return true;
                    }
                }

                vals[v] = !vals[v];
            }
        }

        return false;
    }

    /**
     * @return true if f1 is equal to f2 under some column permutation.
     */
    private boolean equalsUnderSomePermutation(final BinaryFunction f1, final BinaryFunction f2) {
        final PermutationGenerator pg = new PermutationGenerator(f1.getNumArgs());
        int[] permutation;

        while ((permutation = pg.next()) != null) {
            if (equalsUnderPermutation(f1, f2, permutation)) {
                return true;
            }
        }

        return false;
    }

//    private boolean equalsUnderBinaryPermutation(BinaryFunction f1, BinaryFunction f2) {
//        ChoiceGenerator pg = new ChoiceGenerator(f1.getNumArgs(), 2);
//        int[] permutation;
//
//        while ((permutation = pg.next()) != null) {
//            if (equalsUnderBinaryPermutation(f1, f2, permutation)) {
//                return true;
//            }
//        }
//
//        return false;
//    }

    public boolean checkTriple(final int numArgs, final boolean[] g1Rows, final boolean[] g2Rows, final boolean[] g3Rows) {
        final BinaryFunction g1 = new BinaryFunction(numArgs, g1Rows);
        final BinaryFunction g2 = new BinaryFunction(numArgs, g2Rows);
        final BinaryFunction g3 = new BinaryFunction(numArgs, g3Rows);

        System.out.println("g1");
        System.out.println(g1);
        System.out.println("g2");
        System.out.println(g2);
        System.out.println("g3");
        System.out.println(g3);

        if (g1.equals(g2) || g2.equals(g3) || g1.equals(g3)) {
            return false;
        }

        if (!satisfiesTestPair(g1)) {
            System.out.println("g1 fails test pair condition.");
//            return false;
        }

        if (!satisfiesTestPair(g2)) {
            System.out.println("g2 fails test pair condition.");
//            return false;
        }

        if (!satisfiesTestPair(g3)) {
            System.out.println("g3 fails test pair condition.");
//            return false;
        }

        if (!equalsUnderSomePermutation(g1, g2)) {
            System.out.println("g1 and g2 not transposable.");
//            return false;
        }

        if (!equalsUnderSomePermutation(g2, g3)) {
            System.out.println("g2 and g3 not transposable.");
//            return false;
        }

        if (equalsUnderSomePermutation(g1, g3)) {
            System.out.println("g1 and g3 transposable.");
//            return false;
        }

        return true;
    }

//    /**
//     * True if f1 equals f3, where f3 has been tranposed according to choice.
//     */
//    private boolean equalsUnderBinaryPermutation(BinaryFunction f1, BinaryFunction f3, int[] choice) {
//        boolean isTransposable = true;
//
//        for (int i = 0; i < f1.getNumRows(); i++) {
//            boolean[] row = f1.getRow(i);
//
//            boolean temp = row[choice[0]];
//            row[choice[0]] = row[choice[1]];
//            row[choice[1]] = temp;
//
//            if (f1.getValue(row) != f3.getValue(row)) {
//                isTransposable = false;
//            }
//        }
//
//        return isTransposable;
//    }

    private boolean equalsUnderPermutation(final BinaryFunction f1,
                                           final BinaryFunction f3,
                                           final int[] permutation) {
        for (int i = 0; i < f1.getNumRows(); i++) {
            final boolean[] row = f1.getRow(i);
            final boolean[] newRow = new boolean[row.length];

            for (int j = 0; j < row.length; j++) {
                newRow[j] = row[permutation[j]];
            }

            if (f1.getValue(row) != f3.getValue(newRow)) {
                return false;
            }
        }

        return true;
    }

}





