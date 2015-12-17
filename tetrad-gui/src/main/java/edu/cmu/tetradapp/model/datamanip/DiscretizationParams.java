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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The parameters for the discretizer.
 *
 * @author Tyler Gibson
 */
public class DiscretizationParams implements Params {
    static final long serialVersionUID = 23L;

    /**
     * A map from node's to the continuous discretizations to use for them.
     *
     * @serial Not null.
     */
    private Map<Node, DiscretizationSpec> map = new HashMap<Node, DiscretizationSpec>();


    /**
     * Constructs the discretization params.
     */
    public DiscretizationParams() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DiscretizationParams serializableInstance() {
        return new DiscretizationParams();
    }


    /**
     * @return the specs.
     */
    public Map<Node, DiscretizationSpec> getSpecs() {
        return map;
    }


    /**
     * Sets the mapping.
     */
    public void setSpecs(Map<Node, DiscretizationSpec> map) {
        if (!(map instanceof Serializable)) {
           throw new IllegalArgumentException("The given map must be serializable");
        }
        this.map = map;
    }


    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (map == null) {
            throw new NullPointerException("Mapping must not be null");
        }
    }

}




