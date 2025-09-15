/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndependenceTest;

/**
 * Gives an interface for a search method that searches and returns a graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IGraphSearch {

    /**
     * Runs the search and returns a graph.
     *
     * @return The discovered graph.
     * @throws InterruptedException if any.
     */
    Graph search() throws InterruptedException;

    /**
     * Gets the test used by the search.
     *
     * @return The test used by the search.
     * @throws IllegalStateException if the search does not support getting the test.
     */
    default IndependenceTest getTest() {
        throw new IllegalStateException("This algorithm does not support getting the test.");
    }

    /**
     * Sets the test to be used by the search. The list of variables of the new proposed test must be equal to the list
     * of variables of the existing test.
     *
     * @param test The test to be used by the search.
     * @throws IllegalArgumentException if the list of variables of the new proposed test is not equal to the list of
     *                                  variables of the existing test.
     * @throws IllegalStateException    if the search does not support setting the test.
     */
    default void setTest(IndependenceTest test) {
        throw new IllegalStateException("This algorithm does not support setting the test.");
    }
}



