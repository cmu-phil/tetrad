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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Node;

import java.util.Set;

/**
 * Gives an interface for Markov blanket searches.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IMbSearch {

    /**
     * Given the target, this returns all the nodes in the Markov Blanket.
     *
     * @param target a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     * @throws InterruptedException if any
     */
    Set<Node> findMb(Node target) throws InterruptedException;

    /**
     * The name of the algorithm.
     *
     * @return a {@link java.lang.String} object
     */
    String getAlgorithmName();

    /**
     * Number of independent tests.
     *
     * @return a int
     */
    int getNumIndependenceTests();
}



