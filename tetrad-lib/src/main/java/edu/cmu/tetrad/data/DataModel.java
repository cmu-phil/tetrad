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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

/**
 * <p>
 * Interface implemented by classes, instantiations of which can serve as data models in Tetrad. Data models may be
 * named if desired; if provided, these names will be used for display purposes.
 * <p>
 * This interface is relatively free of methods, mainly because classes that can serve as data models in Tetrad are
 * diverse, including continuous and discrete data sets, covariance and correlation matrices, graphs, and lists of other
 * data models. So this is primarily a taqging interface.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface DataModel
        extends KnowledgeTransferable, VariableSource {

    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getName.</p>
     *
     * @return the name of the data model (may be null).
     */
    String getName();

    /**
     * Sets the name of the data model (may be null).
     *
     * @param name the name to set
     */
    void setName(String name);

    /**
     * Renders the data model as as String.
     *
     * @return a {@link java.lang.String} object
     */
    String toString();

    /**
     * <p>isContinuous.</p>
     *
     * @return true if the data model is continuous, false otherwise.
     */
    boolean isContinuous();

    /**
     * <p>isDiscrete.</p>
     *
     * @return true if the data model is discrete, false otherwise.
     */
    boolean isDiscrete();

    /**
     * <p>isMixed.</p>
     *
     * @return true if the data model is mixed continuous/discrete, false otherwise.
     */
    boolean isMixed();

    /**
     * <p>getVariable.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return the variable with the given name, or null if no such variable exists.
     */
    Node getVariable(String name);

    /**
     * <p>copy.</p>
     *
     * @return a copy of the data model.
     */
    DataModel copy();
}

