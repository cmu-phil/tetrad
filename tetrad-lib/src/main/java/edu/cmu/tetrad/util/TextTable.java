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
 * Stores a 2D array of Strings for printing out tables. The table can print out
 * columns either left justified or right justified, with a given number of
 * spaces between columns.
 *
 * @author Joseph Ramsey
 */
public class TextTable {

    /**
     * Set <code>justification</code> to this if the columns should be left
     * justified.
     */
    private static final int LEFT_JUSTIFIED = 0;

    /**
     * Set <code>justification</code> to this if the columns should be right
     * justified.
     */
    private static final int RIGHT_JUSTIFIED = 1;

    /**
     * The tokens to be printed out.
     */
    private String[][] tokens;

    /**
     * True if columns should be left justified, false if right justified.
     */
    private int justification = RIGHT_JUSTIFIED;

    /**
     * The number of spaces between columns. By default, 2.
     */
    private int columnSpacing = 2;

    /**
     * Construct the text table; the table has a fixed number of rows and
     * columns, each greater than zero.
     */
    public TextTable(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException();
        }

        this.tokens = new String[rows][columns];

        for (int i = 0; i < tokens.length; i++) {
            for (int j = 0; j < tokens[0].length; j++) {
                tokens[i][j] = "";
            }
        }
    }

    /**
     * Sets the token at the given row and column, each of which must be >= 0
     * and less than the number of rows or columns, respectively.
     */
    public void setToken(int row, int column, String token) {
        if (token == null) {
            throw new NullPointerException();
        }

        if (row >= tokens.length) {
            throw new IllegalArgumentException("Row out of bound " + row + " of " + tokens.length);
        }

        if (column >= tokens[0].length) {
            throw new IllegalArgumentException("Column out of bound " + column + " of " + tokens[0].length);
        }

        tokens[row][column] = token;
    }

    /**
     * @return the token at the given row and column.
     */
    public String getTokenAt(int row, int column) {
        return tokens[row][column];
    }

    /**
     * @return the number of rows, as set in the constructor.
     */
    public int getNumRows() {
        return tokens.length;
    }

    /**
     * @return the number of rows, as set in the constructor.
     */
    public int getNumColumns() {
        return tokens[0].length;
    }

    /**
     * @return the number of spaces between columns, by default 2.
     */
    private int getColumnSpacing() {
        return columnSpacing;
    }

    /**
     * Sets the number of spaces between columns, to some number >= 0.
     */
    public void setColumnSpacing(int numSpaces) {
        if (numSpaces < 0) {
            throw new IllegalArgumentException();
        }

        this.columnSpacing = numSpaces;
    }

    /**
     * @return the justification, either LEFT_JUSTIFIED or RIGHT_JUSTIFIED.
     */
    private int getJustification() {
        return justification;
    }

    /**
     * Sets the justification, either LEFT_JUSTIFIED or RIGHT_JUSTIFIED.
     */
    public void setJustification(int justification) {
        if (!(justification == LEFT_JUSTIFIED || justification == RIGHT_JUSTIFIED)) {
            throw new IllegalArgumentException();
        }

        this.justification = justification;
    }

    public String toString() {
        StringBuilder buffer = new StringBuilder();

        int[] colWidths = new int[tokens[0].length];

        for (int j = 0; j < tokens[0].length; j++) {
            for (String[] token : tokens) {
                if (token[j].length() > colWidths[j]) {
                    colWidths[j] = token[j].length();
                }
            }
        }

        for (String[] token1 : tokens) {
            for (int j = 0; j < tokens[0].length; j++) {
                for (int k = 0; k < getColumnSpacing(); k++) {
                    buffer.append(' ');
                }

                int numPaddingSpaces = colWidths[j] - token1[j].length();

                if (getJustification() == RIGHT_JUSTIFIED) {
                    for (int k = 0; k < numPaddingSpaces; k++) {
                        buffer.append(' ');
                    }
                }

                buffer.append(token1[j]);

                if (getJustification() == LEFT_JUSTIFIED) {
                    for (int k = 0; k < numPaddingSpaces; k++) {
                        buffer.append(' ');
                    }
                }
            }

            buffer.append("\n");
        }

        return buffer.toString();
    }
}



