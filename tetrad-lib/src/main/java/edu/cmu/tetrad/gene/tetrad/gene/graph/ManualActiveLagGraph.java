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

package edu.cmu.tetrad.gene.tetrad.gene.graph;

import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.gene.tetrad.gene.history.LaggedFactor;

/**
 * Constructs as a (manual) update graph.
 */
public class ManualActiveLagGraph extends ActiveLagGraph implements SessionModel {
    static final long serialVersionUID = 23L;
    private String name;

    //=========================CONSTRUCTORS===========================//

    /**
     * Using the given parameters, constructs an BasicLagGraph.
     */
    public ManualActiveLagGraph() {
        addFactors("Gene", 1);
        setMaxLagAllowable(3);

        // Add edges one time step back.
        for (String s : getFactors()) {
            LaggedFactor laggedFactor = new LaggedFactor(s, 1);
            addEdge(s, laggedFactor);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ActiveLagGraph serializableInstance() {
        return new ManualActiveLagGraph();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}





