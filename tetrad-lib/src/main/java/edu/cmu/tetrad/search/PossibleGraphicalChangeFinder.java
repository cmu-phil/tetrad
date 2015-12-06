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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds the minimal graphical changes that block a collection of d-connecting undirectedPaths.
 *
 * @author Tyler Gibson
 */
class PossibleGraphicalChangeFinder {


    /**
     * The pag that the undirectedPaths are relative to.
     */
    private Graph pag;


    /**
     * The separation sets.
     */
    private List<List<Node>> separations;

    /**
     * Constructs the finder given the undirectedPaths that must be blocked.
     */
    public PossibleGraphicalChangeFinder(Graph pag, Collection<Collection<Node>> separations) {
        if (pag == null) {
            throw new NullPointerException("The given pag must not be null.");
        }
        if (separations == null) {
            throw new NullPointerException("The given separation sets must not be null.");
        }
        this.pag = pag;
        this.separations = new ArrayList<List<Node>>();
        for (Collection<Node> sep : separations) {
            this.separations.add(new ArrayList<Node>(sep));
        }
    }
}



