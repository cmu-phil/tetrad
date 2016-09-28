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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Stores a file for reading in a lag graph from a file.
 *
 * @author Joseph Ramsey
 */
public class StoredLagGraphParams implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The filename of the stored lag graph.
     *
     * @serial
     */
    private String filename = null;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new parameters object. Must be a blank constructor.
     */
    public StoredLagGraphParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static StoredLagGraphParams serializableInstance() {
        return new StoredLagGraphParams();
    }

    //==============================PUBLIC METHODS=======================//

    /**
     * Returns the stored file.
     */
    public String getFilename() {
        return this.filename;
    }

    /**
     * Sets the stored file.
     */
    public void setFilename(String filename) {
        if (filename == null) {
            throw new NullPointerException("Filename must not be null.");
        }

        this.filename = filename;
    }
}





