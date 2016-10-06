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

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes a graph by adding the previous time step only of each variable.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class PreviousStepOnly implements GraphInitializer {
    public PreviousStepOnly() {
    }

    /**
     * Randomizes the graph.
     */
    public void initialize(LagGraph lagGraph) {

        lagGraph.clearEdges();

        List<String> factors = new ArrayList<>(lagGraph.getFactors());

        // Add edges one time step back.
        for (String factor1 : factors) {
            LaggedFactor laggedFactor = new LaggedFactor(factor1, 1);
            lagGraph.addEdge(factor1, laggedFactor);
        }
    }
}





