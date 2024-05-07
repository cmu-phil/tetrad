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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.session.SessionModel;


/**
 * Tags classes that can yield graphs as input to knowledge.
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public interface KnowledgeBoxInput extends SessionModel, VariableSource {

    /**
     * The serialVersionUID is a special field that determines the version of the serialized object. It is used during
     * deserialization to verify that the sender and receiver of a serialized object have loaded classes for that object
     * that are compatible with respect to serialization. If the receiver has a different serialVersionUID than that of
     * the serialized object, then deserialization will result in an InvalidClassException. Additionally, serialization
     * will ignore any fields of a class that are declared as transient or static
     */
    long serialVersionUID = 23L;

    /**
     * Retrieves the source graph.
     *
     * @return the source graph for the knowledge box input
     */
    Graph getSourceGraph();

    /**
     * Retrieves the result graph.
     *
     * @return the result graph for the knowledge box input
     */
    Graph getResultGraph();
}



