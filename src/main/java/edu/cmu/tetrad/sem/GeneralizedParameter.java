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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;


/**
 * Stores information about the identity of an NLNG SEM parameter--its name,
 * which is not checked for form, and its type, one of the NingParamType types.
 * In addition to not checking parameter names for form, no attempt is made
 * in this class to notice when a parameter is defined using the same name
 * as another parameter, the problem being that the same parameter may be
 * used in two different NLNG SEMs. This checking must take place at a
 * higher level, somewhere, since freeParameters by the same name are identified
 * within each NLNG SEM, and they must have the same type.
 *
 * @author Joseph Ramsey
 */
public final class GeneralizedParameter implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The name of the parameter.
     *
     * @serial Cannot be null.
     */
    private String name;

    //================================CONSTRUCTORS=======================//

    /**
     * @param name  The name of the parameter.
     */
    public GeneralizedParameter(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        this.name = name;
    }

    /**
     * @return a simple exemplar of this class to test serialization.
     */
    public static GeneralizedParameter serializableInstance() {
        return new GeneralizedParameter("b1");
    }

    //================================PUBLIC METHODS===================//

    /**
     * @return the name of the parameter.
     */
    public String getName() {
        return name;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof GeneralizedParameter)) {
            return false;
        }

        GeneralizedParameter _parameter = (GeneralizedParameter) o;

        return this.name.equals(_parameter.name);
    }

    /**
     * @return a string representation for this parameter.
     */
    public String toString() {
        return "<" + this.name +  ">";
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
     * @param s The input stream from which this object is to be read.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}


