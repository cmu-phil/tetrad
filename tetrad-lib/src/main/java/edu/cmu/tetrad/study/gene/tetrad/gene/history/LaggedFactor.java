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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Identifies a particular factor (by name) at a particular lag (integer).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LaggedFactor implements Comparable, TetradSerializable {
    private static final long serialVersionUID = 23L;
    /**
     * The number of time steps back for the lagged factor.
     *
     * @serial
     */
    private final int lag;
    /**
     * The name of the factor.
     *
     * @serial
     */
    private String factor;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new lagged factor with a given name and time lag.
     *
     * @param factor the name of the factor.
     * @param lag    the time lag of the factor.
     */
    public LaggedFactor(String factor, int lag) {
        if (factor == null) {
            throw new NullPointerException("Factor name must not be null");
        }

        if (lag < 0) {
            throw new IllegalArgumentException("Lag must be >= 0");
        }

        this.factor = factor;
        this.lag = lag;
    }

    /**
     * Copy constructor- creates a new object with the same properties as the original
     *
     * @param orig a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     */
    public LaggedFactor(LaggedFactor orig) {
        this.factor = orig.factor;
        this.lag = orig.lag;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     */
    public static LaggedFactor serializableInstance() {
        return new LaggedFactor("X", 1);
    }

    //=================================PUBLIC METHODS======================//

    /**
     * {@inheritDoc}
     *
     * Determines whether the given lagged factor is temporally prior to this lagged factor.
     */
    public int compareTo(Object o) {

        if (o instanceof LaggedFactor) {
            LaggedFactor f = (LaggedFactor) o;
            int n = this.factor.compareTo(f.getFactor());

            if (n != 0) {
                return n;
            } else {
                return this.lag - f.getLag();
            }
        } else {
            return 0;
        }
    }

    /**
     * Returns the name of the lagged factor.
     *
     * @return this name.
     */
    public String getFactor() {
        return this.factor;
    }

    /**
     * Sets the name of the lagged factor
     *
     * @param factor a {@link java.lang.String} object
     */
    public void setFactor(String factor) {
        this.factor = factor;
    }

    /**
     * Returns the number of time steps back for this lagged factor.
     *
     * @return the lag.
     */
    public int getLag() {
        return this.lag;
    }

    /**
     * Probably should recheck this later.
     *
     * @return a int
     */
    public int hashCode() {
        return 127 * this.factor.hashCode() + this.lag;
    }

    /**
     * {@inheritDoc}
     *
     * Two lagged factors are equals just in case their factors are equals and their lags are equal.
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LaggedFactor)) {
            return false;
        }
        LaggedFactor c = (LaggedFactor) o;
        return c.getFactor().equals(this.getFactor()) &&
                c.getLag() == this.getLag();
    }

    /**
     * Returns a string representing this lagged factor.
     *
     * @return this string.
     */
    public String toString() {
        return this.factor + ":" + this.lag;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.factor == null) {
            throw new NullPointerException();
        }

        if (this.lag < 0) {
            throw new IllegalStateException();
        }

    }
}





