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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.TetradMetadataIndirectRef;
import edu.cmu.tetrad.util.Version;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;

/**
 * Represents the Tetrad session that is serialized oud loaded back in.
 * Basically, it's a SessionWrapper with metadata. These fields have been
 * encapsulated into their own class so that more metadata fields can be added
 * in the future without disturbing serialization. It must be the case that
 * metadata can load even if the SessionWrapper cannot load.
 *
 * @author Joseph Ramsey
 */
public class TetradMetadata
        implements TetradSerializable, TetradMetadataIndirectRef {
    static final long serialVersionUID = 23L;

    /**
     * The version of Tetrad that saved this session out.
     *
     * @serial Cannot be null.
     */
    private Version version;

    /**
     * The date and time this Tetrad session was created.
     *
     * @serial Cannot be null.
     */
    private final Date date;

    //===========================CONSTRUCTORS=============================//

    /**
     */
    public TetradMetadata() {
        try {
            version = Version.currentViewableVersion();
        }
        catch (Exception e) {
            version = new Version("4.3.1-5");
        }

        date = new Date();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static TetradMetadata serializableInstance() {
        return new TetradMetadata();
    }

    //==========================PUBLIC METHODS===========================//

    public Version getVersion() {
        return version;
    }

    public Date getDate() {
        return date;
    }

    //============================PRIVATE METHODS=======================//

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

        if (version == null) {
            throw new NullPointerException();
        }

        if (date == null) {
            throw new NullPointerException();
        }
    }
}





