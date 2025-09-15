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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps a chi square distribution for purposes of drawing random samples. Methods are provided to allow parameters to
 * be manipulated in an interface. See Wikipedia.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ChiSquare implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The stored degrees of freedom. Needed because the wrapped distribution does not provide getters for its
     * parameters.
     */
    private double df;

    /**
     * Constructs a new Chi Square distribution.
     */
    private ChiSquare() {
        this.df = 5.0;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return the exemplar.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static ChiSquare serializableInstance() {
        return new ChiSquare();
    }

    /**
     * Sets the index'th parameter to the given value.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; # parameters.
     * @param value The value to set for the parameter.
     * @throws IllegalArgumentException If the index is invalid.
     */
    public void setParameter(int index, double value) {
        if (index == 0 && value >= 0.0) {
            this.df = value;
        } else {
            throw new IllegalArgumentException("Illegal value: " + index + " = " + value);
        }
    }

    /**
     * Returns the index'th parameter.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; # parameters.
     * @return The value of the parameter at the specified index.
     * @throws IllegalArgumentException If the index is invalid.
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.df;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    /**
     * Returns the name of the index'th parameter, for display purposes.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; # parameters.
     * @return The name of the parameter at the specified index.
     * @throws IllegalArgumentException If the index is invalid.
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "DF";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return 1;
    }

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Chi Square";
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextChiSquare(this.df);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "ChiSquare(" + this.df + ")";
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}




