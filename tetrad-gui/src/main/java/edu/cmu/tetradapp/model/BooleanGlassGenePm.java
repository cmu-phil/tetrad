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

import edu.cmu.tetrad.study.gene.tetradapp.model.GenePm;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;

/**
 * Implements a parametric model for Boolean Glass gene PM's, which in this case just presents the underlying workbench.
 * There are no additional parameters to the PM.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BooleanGlassGenePm extends GenePm implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private String name;

    //============================CONSTRUCTORS===============================//

    /**
     * Construct a new gene pm, wrapping the given lag graph.
     *
     * @param lagGraph The lag graph to wrap.
     */
    public BooleanGlassGenePm(ManualActiveLagGraph lagGraph) {
        super(lagGraph);
    }

    /**
     * Construct a new gene pm, wrapping the given lag graph.
     *
     * @param lagGraph The lag graph to wrap.
     */
    public BooleanGlassGenePm(RandomActiveLagGraph lagGraph) {
        super(lagGraph);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return A simple exemplar of this class to test serialization.
     */
    public static BooleanGlassGenePm serializableInstance() {
        return new BooleanGlassGenePm(
                (ManualActiveLagGraph) ManualActiveLagGraph.serializableInstance());
    }

    /**
     * Returns the name of the model.
     *
     * @return the name of the model.
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the model.
     */
    public void setName(String name) {
        this.name = name;
    }
}






