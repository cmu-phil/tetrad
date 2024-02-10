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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.ObjectStreamException;
import java.io.Serial;

/**
 * A typesafe enum of the types of the types of freeParameters for SEM models (COEF, MEAN, VAR, COVAR). COEF
 * freeParameters are edge coefficients in the linear SEM model; VAR parmaeters are variances among the error terms;
 * COVAR freeParameters are (non-variance) covariances among the error terms.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ParamType implements TetradSerializable {
    /**
     * A coefficient parameter.
     */
    public static final ParamType COEF = new ParamType("Linear Coefficient");
    /**
     * A mean parameter.
     */
    public static final ParamType MEAN = new ParamType("Variable Mean");
    /**
     * A variance parameter.
     */
    public static final ParamType VAR = new ParamType("Error Variance");
    /**
     * A covariance parameter. (Does not include variance freeParameters; these are indicated using VAR.)
     */
    public static final ParamType COVAR = new ParamType("Error Covariance");
    private static final long serialVersionUID = 23L;
    /**
     * A parameter of a distribution.
     */
    private static final ParamType DIST = new ParamType("Distribution Parameter");
    private static final ParamType[] TYPES = {ParamType.COEF, ParamType.MEAN, ParamType.VAR, ParamType.COVAR, ParamType.DIST};
    // Declarations required for serialization.
    private static int NEXT_ORDINAL;
    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * The ordinal of this type.
     */
    private final int ordinal = ParamType.NEXT_ORDINAL++;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    private ParamType(String name) {
        this.name = name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.ParamType} object
     */
    public static ParamType serializableInstance() {
        return ParamType.COEF;
    }

    /**
     * Prints out the name of the type.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.name;
    }

    /**
     * Returns the type of the parameter.
     *
     * @return a {@link edu.cmu.tetrad.sem.ParamType} object
     * @throws java.io.ObjectStreamException if any.
     */
    @Serial
    Object readResolve() throws ObjectStreamException {
        return ParamType.TYPES[this.ordinal]; // Canonicalize.
    }
}





