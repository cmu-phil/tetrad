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

package edu.cmu.tetrad.util;

import java.util.Iterator;

/**
 * Iterates through all the posible combinations for a set of variables (each
 * with a different number of possible values). </p> For example, if the number
 * of values for each variable is two, this would iterate through a truth table.
 * </p> Not to be confused with a combinatorial (taking n values from m possible
 * values).
 *
 * @author Juan Casares
 */
public class CombinationIterator implements Iterator {
    private final int[] values;
    private final int[] maxValues;
    private final int numValues;
    private boolean hasNext;

    /**
     * Creates a combination set for a set of variables with the given number of
     * maxValues
     *
     * @param maxValues An int array consisting of the maximum values of each variable,
     *                  in order.
     */
    public CombinationIterator(int[] maxValues) {
        this.numValues = maxValues.length;
        this.values = new int[this.numValues];
        this.maxValues = maxValues;
        this.hasNext = true;
    }

    /**
     * @return true iff there is still a combination that has not been returned
     * by the next() method.
     */
    public boolean hasNext() {
        return this.hasNext;
    }

    /**
     * @return an int[] array with the next combination.
     */
    public int[] next() {
        int[] clone = new int[this.numValues];
        System.arraycopy(this.values, 0, clone, 0, this.numValues);

        int i;
        for (i = this.numValues - 1; i >= 0; i--) {
            if (this.values[i] + 1 < this.maxValues[i]) {
                break;
            }
        }

        if (i < 0) {
            this.hasNext = false;
        } else {
            this.values[i]++;
            for (int j = i + 1; j < this.numValues; j++) {
                this.values[j] = 0;
            }
        }

        return clone;
    }

    /**
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}





