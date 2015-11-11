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

/**
 * A function from n binary variables to a binary variable.
 *
 * @author Joseph Ramsey
 */
public class BinaryFunction {

    /**
     * The function index.
     */
    private long functionIndex;

    /**
     * The number of arguments to the function.
     */
    private int numArgs;

    /**
     * Represents the function column of the truth table.
     */
    int[] functionColumn;

    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a binary function by giving the number of arguments and the function index, from which the function
     * column will be calculated.
     *
     * @param numArgs       The number of arguments to the function. The order of these is T-F, left to right in
     *                      increasing period of alternation.
     * @param functionIndex Represents the function column of the truth table. Should be a number from 0 to 2 ^ numArgs
     *                      - 1. The value for a given row in the truth table is calculated by the getValue() method.
     */
    public BinaryFunction(int numArgs, long functionIndex) {
        this.numArgs = numArgs;
        if (functionIndex > getNumFunctions()) {
            throw new IllegalArgumentException("Function index out of range " +
                    "for " + numArgs + " arguments.");
        }
        functionColumn = new int[getNumRows()];
        resetFunction(functionIndex);
    }

    /**
     * Constructs a binary function by giving the number of arguments and the function column itself.
     *
     * @param numArgs        The number of arguments to the function. The order of these is T-F, left to right in
     *                       increasing period of alternation.
     * @param functionColumn The function column.
     */
    public BinaryFunction(int numArgs, boolean[] functionColumn) {
        this.numArgs = numArgs;
        if (functionColumn.length > getNumRows()) {
            throw new IllegalArgumentException("Function column does not have " +
                    "the right number of rows for " + numArgs + " argument: " +
                    functionColumn.length);
        }
        this.functionColumn = new int[getNumRows()];
        resetFunction(getIndex(functionColumn));
    }

    //=============================PUBLIC METHODS=========================//

    public void resetFunction(long functionIndex) {
        this.functionIndex = functionIndex;

        for (int i = 0; i < getNumRows(); i++) {
            functionColumn[getNumRows() - i - 1] = (int) functionIndex % 2;
            functionIndex /= 2;
        }
    }

    public long getOppositeFunction() {
        long functionIndex = 0;

        for (int i = 0; i < getNumRows(); i++) {
            functionIndex *= 2;
            functionIndex = getFunctionIndex(i, functionIndex);
        }

        return functionIndex;
    }

    public long getSymmetricFunction() {
        long functionIndex = 0;

        for (int i = 0; i < getNumRows(); i++) {
            functionIndex *= 2;
            functionIndex += functionColumn[getNumRows() - i - 1];
        }

        return functionIndex;
    }

    public long getSymmetricOppositeFunction() {
        long functionIndex = 0;

        for (int i = 0; i < getNumRows(); i++) {
            functionIndex *= 2;
            functionIndex += 1 - functionColumn[getNumRows() - i - 1];
        }

        return functionIndex;
    }

    public long switchColsBinary(int col1, int col2) {
        int[] functionColumn = new int[getNumRows()];

        for (int i = 0; i < getNumRows(); i++) {
            boolean[] row = getRow(i);

            boolean temp = row[col1];
            row[col1] = row[col2];
            row[col2] = temp;

            boolean b = getValue(row);
            functionColumn[i] = b ? 1 : 0;
        }

        return getIndex(functionColumn);
    }

    public long switchColsFull(int[] permutation) {
        int[] functionColumn = new int[getNumRows()];

        for (int i = 0; i < getNumRows(); i++) {
            boolean[] row = getRow(i);
            boolean[] newRow = new boolean[row.length];

            for (int j = 0; j < row.length; j++) {
                newRow[permutation[j]] = row[j];
            }

            boolean b = getValue(newRow);
            functionColumn[i] = b ? 1 : 0;
        }

        return getIndex(functionColumn);
    }

    public boolean getValue(boolean[] values) {
        if (values.length != numArgs) throw new IllegalArgumentException();
        return functionColumn[getRowIndex(values)] == 1;
    }

    public boolean getValue(int row) {
        return functionColumn[row] == 1;
    }

    public boolean[] getRow(int rowIndex) {
        boolean[] values = new boolean[numArgs];

        for (int i = 0; i < numArgs; i++) {
            values[i] = (rowIndex % 2) == 1;
            rowIndex /= 2;
        }

        return values;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\n");

        for (int j = 0; j < numArgs; j++) {
            buf.append("V").append(j + 1).append("   \t");
        }

        buf.append("G");

        buf.append("\n");

        for (int i = 0; i < (getNumRows()); i++) {
            boolean[] argumentVals = getRow(i);

            for (int j = 0; j < numArgs; j++) {
                buf.append(argumentVals[j]).append("\t");
            }

            buf.append(getValue(argumentVals));

            buf.append("\n");
        }

        buf.append("\n");
        return buf.toString();
    }

    public boolean equals(Object o) {
        if (!(o instanceof BinaryFunction)) {
            return false;
        }

        return functionIndex == ((BinaryFunction) o).getFunctionIndex();
    }

    public int getNumRows() {
        int numRows = 1;

        for (int i = 0; i < getNumArgs(); i++) {
            numRows *= 2;
        }
        return numRows;
    }

    public int getNumArgs() {
        return numArgs;
    }

    public long getFunctionIndex() {
        return functionIndex;
    }

    public long getNumFunctions() {
        long n = 1;

        for (int i = 0; i < getNumRows(); i++) {
            n *= 2;
        }

        return n;
    }

    //=============================PRIVATE METHODS=========================//

    private long getIndex(int[] functionColumn) {
        long functionIndex = 0;

        for (int i = 0; i < getNumRows(); i++) {
            functionIndex *= 2;
            functionIndex += functionColumn[i];
        }

        return functionIndex;
    }

    private long getIndex(boolean[] functionColumn) {
        long functionIndex = 0;

        for (int i = 0; i < getNumRows(); i++) {
            functionIndex *= 2;
            functionIndex += functionColumn[i] ? 1 : 0;
        }

        return functionIndex;
    }

    private long getFunctionIndex(int i, long functionIndex) {
        functionIndex += 1 - functionColumn[i];
        return functionIndex;
    }

    private int getRowIndex(boolean[] values) {
        int rowIndex = 0;

        for (int i = 0; i < numArgs; i++) {
            rowIndex *= 2;
            rowIndex += values[i] ? 1 : 0;
        }

        return rowIndex;
    }
}




