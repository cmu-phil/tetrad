///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Stores a boolean function from a set of boolean-valued parents to a single boolean-valued column.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BooleanFunction implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The array of parents for the stored boolean function.
     */
    private final IndexedParent[] parents;

    /**
     * The stored boolean function.  The order of the rows (for the given parents array, for two parents) is 00, 01, 10,
     * 11, and so on for higher numbers of parents.
     */
    private final boolean[] lookupTable;

    //==============================CONSTRUCTORS=========================//

    /**
     * Constructs a new boolean function for the given array of parents.
     *
     * @param parents an array containing each of the parents (lagged factors) of a given factor.
     */
    public BooleanFunction(IndexedParent[] parents) {
        if (parents == null) {
            throw new NullPointerException();
        }

        for (IndexedParent parent : parents) {
            if (parent == null) {
                throw new NullPointerException();
            }
        }

        this.parents = parents;

        int length = 1;

        for (int i = 0; i < parents.length; i++) {
            length *= 2;
        }

        this.lookupTable = new boolean[length];
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static BooleanFunction serializableInstance() {
        IndexedParent[] parents = new IndexedParent[2];
        parents[0] = new IndexedParent(0, 1);
        parents[1] = new IndexedParent(1, 2);
        return new BooleanFunction(parents);
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Returns the parents of this function.
     *
     * @return this array.
     */
    public Object[] getParents() {
        return this.parents;
    }

    /**
     * Returns the boolean value in a given row of the table. To get the proper row, for a given combination of parent
     * values, use the method
     * <code>getRow</code>
     *
     * @param row the row of the table.
     * @return the value in the table.
     * @see #getRow
     */
    public boolean getValue(int row) {
        return this.lookupTable[row];
    }

    /**
     * Sets the boolean value in a given row of the table. To get the proper row, for a given combination of parent
     * values, use the method
     * <code>getRow</code>
     *
     * @param row   the row of the table.
     * @param value the value to set.
     * @see #getRow
     */
    public void setValue(int row, boolean value) {
        this.lookupTable[row] = value;
    }

    /**
     * Returns the row for the given combination of parent values. The rows are zero-indexed and are arranged in
     * truth-table fashion, as follows. Row 0 is the row where all parents have the value <code>true</code>. The last
     * row (row 2 ^ numParents - 1) is the row where all parents have the value <code>false</code>. Reading down the
     * truth table, the parent whose value changes most rapidly is the rightmost parent (parent[numParents - 1]); the
     * parent whose value changes the most slowly, in fact changes only once halfway down the table, is the leftmost
     * parent (parent[0]). This convention is adopted to make it easier to set the values of boolean functions manually;
     * the majority of truth tables in logic books are set up this way.
     *
     * @param parentValues an array of parent values. Should be in the same order as the parents, as returned by
     *                     <code>getParents</code>.
     * @return the row of the table.
     * @see #getParents
     */
    public int getRow(boolean[] parentValues) {

        int row = 0;

        for (int i = this.parents.length - 1; i >= 0; i--) {
            row *= 2;
            row += parentValues[i] ? 0 : 1;
        }

        return row;
    }

    /**
     * <p>getNumRows.</p>
     *
     * @return the number of rows in the table.
     */
    public int getNumRows() {
        return this.lookupTable.length;
    }

    /**
     * <p>getParentValues.</p>
     *
     * @param row the row of the table.
     * @return the combination of parent values represented by a given row in the table.
     */
    public boolean[] getParentValues(int row) {

        boolean[] parentValues = new boolean[this.parents.length];

        if (row >= this.lookupTable.length) {
            throw new IllegalArgumentException();
        }

        for (int i = this.parents.length - 1; i >= 0; i--) {
            parentValues[i] = row % 2 == 1;
            row /= 2;
        }

        return parentValues;
    }

    /**
     * Chooses a random function by flipping a coin for each value in table.
     */
    public void randomize() {
        for (int i = 0; i < this.lookupTable.length; i++) {
            this.lookupTable[i] =
                    RandomUtil.getInstance().nextDouble() > 0.5;
        }
    }

    /**
     * Determines whether the getModel function is canalyzing, according to Kaufmann's definition in Oridins of Order:
     * "I define as a canalyzing Boolean function any Boolean function having the property that it has at least one
     * input having at least one value (1 or 0) which suffices to guarantee that the regulated element assumes a
     * specific value (1 or 0)" (page 203-4).
     *
     * @return true if the getModel function is canalyzing, false if not.
     */
    public boolean isCanalyzing() {

        // Stores, for each parent encounted in the parent column, the
        // last value encounted in the function value column in an
        // unbroken CPDAG. -2 means the pattern was broken, -1 means
        // no value was encounntered yet, 0 means false, and 1 means
        // true.
        int[] lastValues = new int[2];

        // Find the first parent such that all the true's or all the
        // false's map to the same value in the same value.
        for (int jump = 1; jump < this.lookupTable.length; jump *= 2) {
            lastValues[0] = -1;
            lastValues[1] = -1;

            for (int row = 0; row < this.lookupTable.length; row++) {

                // 0 means false, 1 means true.
                int value = this.lookupTable[row] ? 1 : 0;
                int parentValue = (row / jump) % 2 == 0 ? 1 : 0;

                if (-2 != lastValues[parentValue]) {
                    if (value != lastValues[parentValue]) {
                        if (-1 == lastValues[parentValue]) {

                            // We're encountering this parent value for the
                            // first time.
                            lastValues[parentValue] = value;
                        } else {

                            // The pattern has just been broken.
                            lastValues[parentValue] = -2;
                        }
                    }
                }
            }

            // If for this parent the function is canalyzing for
            // either true values or false values of the parent, return
            // true.
            if ((lastValues[0] != -2) || (lastValues[1] != -2)) {
                return true;
            }
        }

        // If the canalyzing test fails for all columns, return false.
        return false;
    }

    /**
     * Determines whether each input parent has an influence on the outcome--that is, whether for each parent there is a
     * combination of states for the other parents for which the function would be different if the given parent were
     * true as opposed to false.
     *
     * @return true if each input parent has an influence on the outcome, false if not.
     */
    public boolean isEffective() {

        boolean[] result = new boolean[this.parents.length];

        for (int row = 0; row < this.lookupTable.length; row++) {
            int jump = 1;

            for (int i = 0; i < this.parents.length; i++) {
                if ((row / jump) % 2 == 0) {
                    if (this.lookupTable[row] != this.lookupTable[row + jump]) {
                        result[i] = true;
                    }
                }

                jump *= 2;
            }
        }

        for (int i = 0; i < this.parents.length; i++) {
            if (!result[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a string representation of the boolean function.
     *
     * @return this string.
     */
    public String toString() {

        StringBuilder buf = new StringBuilder("\nBoolean Function:");

        for (int i = 0; i < this.lookupTable.length; i++) {
            buf.append("\n").append(i).append("\t");
            buf.append(this.lookupTable[i]);
        }

        buf.append("\n\n");

        return buf.toString();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}





