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

import java.io.Serial;

/**
 * A pretend distribution that always returns the given value when nextRandom() is called.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SingleValue implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a single value in a statistical distribution.
     */
    private double value;

    //===========================CONSTRUCTORS===========================//

    /**
     * Constructs single value "distribution" using the given value.
     *
     * @param value A real number.
     */
    public SingleValue(double value) {
        this.value = value;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static SingleValue serializableInstance() {
        return new SingleValue(0.5);
    }

    /**
     * Sets the index'th parameter to the given value.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; number of parameters.
     * @param value The value to set.
     * @throws IllegalArgumentException If index is not a valid parameter index.
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.value = value;
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * Returns the index'th parameter.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; # parameters.
     * @return The value of the parameter.
     * @throws IllegalArgumentException If index is not a valid parameter index.
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.value;
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * Returns the name of the index'th parameter.
     *
     * @param index The index of the parameter. Must be &gt;= 0 and &lt; number of parameters.
     * @return The name of the parameter.
     * @throws IllegalArgumentException If index is not a valid parameter index.
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Value";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * Returns the number of parameters in the distribution.
     *
     * @return the number of parameters.
     */
    public int getNumParameters() {
        return 1;
    }

    /**
     * Returns the next random number from the distribution.
     *
     * @return A random number generated from the distribution.
     */
    public double nextRandom() {
        return getValue();
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representation of the object.
     */
    public String toString() {
        return "[" + getValue() + "]";
    }

    private double getValue() {
        return this.value;
    }


    /**
     * Returns the name of the distribution.
     *
     * @return the name.
     */
    public String getName() {
        return "Single Value";
    }
}





