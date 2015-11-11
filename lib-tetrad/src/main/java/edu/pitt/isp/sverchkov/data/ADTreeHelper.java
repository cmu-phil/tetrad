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

package edu.pitt.isp.sverchkov.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author user
 */
class ADTreeHelper implements Serializable {

    protected final int m; // The number of attributes
    protected final int[] airities;

    protected ADTreeHelper(int m) {
        this.m = m;
        airities = new int[m];
    }

    protected int count(int[] assignment, CountNode ptr) {
           if (null == ptr) return 0;

        for (int i = ptr.attr - 1; i >= 0 && ptr != null; i--) {
            VaryNode vary = ptr.vary[i];
            if (assignment[i] >= 0) {
                if (assignment[i] == vary.mcv) {
                    int[] a = new int[m];
                    System.arraycopy(assignment, 0, a, 0, m);
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

    protected class CountNode implements Serializable {
        private final int attr;
        protected final int count;
        protected final VaryNode[] vary;

        protected CountNode(final int attribute, final int[][] array) {
            attr = attribute;
            count = array.length;
            vary = new VaryNode[attr];
            for (int i = 0; i < attr; i++)
                vary[i] = new VaryNode(i, array);
        }
    }

    protected class VaryNode implements Serializable {
        protected final CountNode[] values;
        protected int mcv = -1;

        private VaryNode(final int attr, final int[][] array) {
            System.out.println(attr);

            final int airity = airities[attr];

            values = new CountNode[airity];

            List<List<Integer>> childArrayIndexes = new ArrayList<>(airity);
            for (int i = 0; i < airity; i++)
                childArrayIndexes.add(new ArrayList<Integer>());

            for (int r = 0; r < array.length; r++)
                childArrayIndexes.get(array[r][attr]).add(r);

            int maxCount = 0;
            for (int i = 0; i < airity; i++) {
                int count = childArrayIndexes.get(i).size();
                if (count > maxCount) {
                    maxCount = count;
                    mcv = i;
                }
            }

            for (int i = 0; i < airity; i++)
                if (i != mcv) {
                    List<Integer> indexes = childArrayIndexes.get(i);
                    if (indexes.size() > 0) {
                        int[][] childArray = new int[indexes.size()][];
                        int j = 0;
                        for (int index : indexes)
                            childArray[j++] = array[index];

                        values[i] = new CountNode(attr, childArray);
                    }
                }
        }
    }

}

