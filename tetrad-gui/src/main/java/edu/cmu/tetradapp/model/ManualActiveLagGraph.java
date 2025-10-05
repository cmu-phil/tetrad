///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.study.gene.tetrad.gene.graph.ActiveLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;

/**
 * Constructs as a (manual) update graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ManualActiveLagGraph extends ActiveLagGraph implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the graph.
     */
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
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.graph.ActiveLagGraph} object
     */
    public static ActiveLagGraph serializableInstance() {
        return new ManualActiveLagGraph();
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }
}






