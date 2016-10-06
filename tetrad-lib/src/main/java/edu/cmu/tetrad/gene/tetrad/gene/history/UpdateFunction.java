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

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * <p>Implements a function from the previous time steps of a history array to
 * the getModel time step. The function is implemented factor by factor; for each
 * factor, the indexed connectivity specifies a specific set of parents
 * (IndexedParents) that the function is permitted to depend on for that factor;
 * the getLabel method returns the value for that set of parents in the
 * history. The update function may optionally be associated with an
 * IndexedLagGraph to provide information about the <i>intended</i> graphical
 * structure of the function (say, from a causal point of view). This
 * IndexedLagGraph gives information about the string names and order of the
 * factors and the number and order of the parents (IndexedParent's) for each
 * factor. This information should be encoded in the function itself, but there
 * is no requirement that the function be checked against the graph explicitly
 * to make sure the graph is actually implemented.</p>
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public interface UpdateFunction extends TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * Returns the indexed lag graph, if one is available.
     */
    IndexedLagGraph getIndexedLagGraph();

    /**
     * Returns the value of the glass function for a given factor.
     *
     * @param factorIndex the index of the factor
     * @param history     the history array.
     * @return the value of the Glass function for this factor.
     */
    double getValue(int factorIndex, double[][] history);

    /**
     * Returns the number of factors in the history. This is used to set up the
     * initial history array.
     */
    int getNumFactors();

    /**
     * Returns the max lag of the gene history. This is used to set up the
     * initial history array.
     */
    int getMaxLag();
}






