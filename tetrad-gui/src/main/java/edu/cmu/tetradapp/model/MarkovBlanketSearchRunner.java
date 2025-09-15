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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.Executable;

import java.util.List;

/**
 * Represents a runner for a Markov blanket search.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface MarkovBlanketSearchRunner extends Executable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getParams.</p>
     *
     * @return the search params.
     */
    Parameters getParams();


    /**
     * <p>getSource.</p>
     *
     * @return the source for the search.
     */
    DataSet getSource();


    /**
     * <p>getDataModelForMarkovBlanket.</p>
     *
     * @return the data model for the variables in the markov blanket.
     */
    DataSet getDataModelForMarkovBlanket();


    /**
     * <p>getMarkovBlanket.</p>
     *
     * @return the variables in the markov blanket.
     */
    List<Node> getMarkovBlanket();


    /**
     * <p>getSearchName.</p>
     *
     * @return the name of the search.
     */
    String getSearchName();


    /**
     * Sets the search name.
     *
     * @param n the name of the search.
     */
    void setSearchName(String n);


}




