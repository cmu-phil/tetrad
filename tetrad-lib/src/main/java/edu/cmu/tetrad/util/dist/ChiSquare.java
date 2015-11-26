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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.RandomUtil;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps a chi square distribution for purposes of drawing random samples.
 * Methods are provided to allow parameters to be manipulated in an interface.
 * See Wikipedia.
 *
 * @author Joseph Ramsey
 */
public class ChiSquare implements Distribution {
    static final long serialVersionUID = 23L;

    /**
     * The stored degees of freedom. Needed because the wrapped distribution
     * does not provide getters for its parameters.
     */
    private double df = 5.0;

    /**
     * Constructs a new Chi Square distribution.
     *
     * @param df The degrees of freedom.
     */
    public ChiSquare(double df) {
        this.df = df;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return the exemplar.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static ChiSquare serializableInstance() {
        return new ChiSquare(5.0);
    }

    public void setParameter(int index, double value) {
        if (index == 0 && value >= 0.0) {
            df = value;
        } else {
            throw new IllegalArgumentException("Illegal value: " + index + " = " + value);
        }
    }

    public double getParameter(int index) {
        if (index == 0) {
            return df;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    public String getParameterName(int index) {
        if (index == 0) {
            return "DF";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    public int getNumParameters() {
        return 1;
    }

    public String getName() {
        return "Chi Square";
    }

    public double nextRandom() {
        return RandomUtil.getInstance().nextChiSquare(df);
    }

    public String toString() {
        return "ChiSquare(" + df + ")";
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
     * @param s Ibid.
     * @throws java.io.IOException    If the stream cannot be read.
     * @throws ClassNotFoundException If the class of an object in the stream
     *                                is not in the project.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}




