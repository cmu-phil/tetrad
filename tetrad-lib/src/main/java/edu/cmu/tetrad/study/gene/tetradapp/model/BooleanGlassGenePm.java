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

package edu.cmu.tetrad.study.gene.tetradapp.model;

import edu.cmu.tetrad.study.gene.tetrad.gene.graph.ManualActiveLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.RandomActiveLagGraph;
import edu.cmu.tetrad.session.SessionModel;

/**
 * Implements a parametric model for Boolean Glass gene PM's, which in this case
 * just presents the underlying workbench. There are no additional parameters to
 * the PM.
 *
 * @author Joseph Ramsey
 */
public class BooleanGlassGenePm extends GenePm implements SessionModel {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.n
     */
    private String name;

    //============================CONSTRUCTORS===============================//

    public BooleanGlassGenePm(ManualActiveLagGraph lagGraph) {
        super(lagGraph);
    }

    public BooleanGlassGenePm(RandomActiveLagGraph lagGraph) {
        super(lagGraph);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BooleanGlassGenePm serializableInstance() {
        return new BooleanGlassGenePm(
                (ManualActiveLagGraph) ManualActiveLagGraph.serializableInstance());
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}




