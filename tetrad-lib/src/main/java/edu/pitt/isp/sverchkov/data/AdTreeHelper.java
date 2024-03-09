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

package edu.pitt.isp.sverchkov.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The AdTreeHelper class is a helper class for constructing and manipulating
 * an AD (Attribute Decision) tree.
 */
class AdTreeHelper implements Serializable {

    /**
     * The number of attributes.
     */
    protected final int m; // The number of attributes

    /**
     * The airities of the attributes. The i-th element of this array is the airity of the i-th attribute.
     */
    protected final int[] airities;

    /**
     * Constructs an AD tree helper for the given data set.
     *
     * @param m The number of attributes.
     */
    protected AdTreeHelper(int m) {
        this.m = m;
        this.airities = new int[m];
    }

    /**
     * Constructs an AD tree helper for the given data set.
     *
     * @param assignment The assignment of values to attributes.
     * @param ptr        The root of the AD tree.
     * @return The number of instances in the data set that match the given assignment.
     */
    protected int count(int[] assignment, CountNode ptr) {
        if (null == ptr) return 0;

        for (int i = ptr.attr - 1; i >= 0 && ptr != null; i--) {
            VaryNode vary = ptr.vary[i];
            if (assignment[i] >= 0) {
                if (assignment[i] == vary.mcv) {
                    int[] a = new int[this.m];
                    System.arraycopy(assignment, 0, a, 0, this.m);
                    a[i] = -1;
                    int count = count(a, ptr);
                    for (int v = 0; v < vary.values.length; v++)
                        if (v != vary.mcv) {
                            a[i] = v;
                            count -= count(a, ptr);
                        }
                    return count;
                } else
                    ptr = vary.values[assignment[i]];
            }
        }

        return null == ptr ? 0 : ptr.count;
    }

    /**
     * Constructs an AD tree helper for the given data set.
     */
    protected class CountNode implements Serializable {

        /**
         * The number of instances in the data set.
         */
        protected final int count;

        /**
         * The children of this node.
         */
        protected final VaryNode[] vary;

        /**
         * The attribute of this node.
         */
        private final int attr;

        protected CountNode(int attribute, int[][] array) {
            this.attr = attribute;
            this.count = array.length;
            this.vary = new VaryNode[this.attr];
            for (int i = 0; i < this.attr; i++)
                this.vary[i] = new VaryNode(i, array);
        }
    }

    /**
     * Constructs an AD tree helper for the given data set.
     */
    protected class VaryNode implements Serializable {

        /**
         * The values of the attribute.
         */
        protected final CountNode[] values;

        /**
         * The most common value of the attribute.
         */
        protected int mcv = -1;

        private VaryNode(int attr, int[][] array) {
            System.out.println(attr);

            int airity = AdTreeHelper.this.airities[attr];

            this.values = new CountNode[airity];

            List<List<Integer>> childArrayIndexes = new ArrayList<>(airity);
            for (int i = 0; i < airity; i++)
                childArrayIndexes.add(new ArrayList<>());

            for (int r = 0; r < array.length; r++)
                childArrayIndexes.get(array[r][attr]).add(r);

            int maxCount = 0;
            for (int i = 0; i < airity; i++) {
                int count = childArrayIndexes.get(i).size();
                if (count > maxCount) {
                    maxCount = count;
                    this.mcv = i;
                }
            }

            for (int i = 0; i < airity; i++)
                if (i != this.mcv) {
                    List<Integer> indexes = childArrayIndexes.get(i);
                    if (!indexes.isEmpty()) {
                        int[][] childArray = new int[indexes.size()][];
                        int j = 0;
                        for (int index : indexes)
                            childArray[j++] = array[index];

                        this.values[i] = new CountNode(attr, childArray);
                    }
                }
        }
    }
}

