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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.study.gene.tetrad.gene.graph.ActiveLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.LagGraphParams;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.SimpleRandomizer;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;

/**
 * Constructs as a randomized update workbench.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomActiveLagGraph extends ActiveLagGraph implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the graph.
     */
    private String name;

    //===========================CONSTRUCTORS===========================//

    /**
     * Using the given parameters, constructs an BasicLagGraph that is randomized upon construction.
     *
     * @param params an LagGraphParams object.
     */
    public RandomActiveLagGraph(LagGraphParams params) {

        addFactors("Gene", params.getVarsPerInd());

        int indegreeType;

        switch (params.getIndegreeType()) {
            case LagGraphParams.CONSTANT:
                indegreeType = SimpleRandomizer.CONSTANT;
                break;

            case LagGraphParams.MAX:
                indegreeType = SimpleRandomizer.MAX;
                break;

            case LagGraphParams.MEAN:
                indegreeType = SimpleRandomizer.MEAN;
                break;

            default:
                throw new IllegalArgumentException();
        }

        setMaxLagAllowable(params.getMlag());

        SimpleRandomizer randomizer = new SimpleRandomizer(params.getIndegree(),
                indegreeType, params.getMlag(), params.getPercentUnregulated());

        randomizer.initialize(this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.graph.ActiveLagGraph} object
     */
    public static ActiveLagGraph serializableInstance() {
        return new RandomActiveLagGraph(LagGraphParams.serializableInstance());
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





