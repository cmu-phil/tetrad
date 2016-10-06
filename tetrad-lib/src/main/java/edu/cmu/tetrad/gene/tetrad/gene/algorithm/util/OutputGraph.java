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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.util;


/**
 * Represents a graph that's output by a genetic search algorithm.
 *
 * @author Joseph Ramsey
 */
public interface OutputGraph {

    /**
     * Returns the number of variables over which the graph is defined.
     */
    int getSize();

    /**
     * Returns the indices of the parent variables for the given variable.
     *
     * @param index the index of the variable whose parents are requested.
     */
    int[] getParents(int index);

    /**
     * Returns the lags of the parent variables for the given variable, provided
     * parents have associated time lags; otherwise, returns null. A lag is a
     * number >= 0, where 0 indicates the getModel time step and integers > 0
     * indicate that many time steps back into the past. If getLags(index) is
     * non-null, the length of getLags(index) should be the same as the length
     * of getParents(index), and getLags(i) should be the lag for the
     * corresponding position in getParents(i), for i = 0,...,
     * getParents(i).length.
     *
     * @param index the lags of the variable whose parents are requested.
     */
    int[] getLags(int index);

    /**
     * Returns the name of the variable at the given index.
     */
    String getNodeName(int index);

    /**
     * Returns the name of the graph
     */
    String getGraphName();

}





