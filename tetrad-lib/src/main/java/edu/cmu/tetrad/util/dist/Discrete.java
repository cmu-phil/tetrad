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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.Arrays;

/**
 * Wraps a chi square distribution for purposes of drawing random samples. Methods are provided to allow parameters to
 * be manipulated in an interface. A value of n is returned if a number drawn uniformly from [0, 1] is less than the n +
 * 1th p value.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Discrete implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The probabilities of the different values.
     */
    private final double[] p;

    /**
     * A discrete distribution with 0 with probability 1 - p and 1 with probability p. Each of the supplied values must
     * be in (0, 1), and each must be less than its successor (if it has one).
     */
    private Discrete(double... p) {
        this.p = convert(p);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static Discrete serializableInstance() {
        return new Discrete(.1, .4, .9);
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return this.p.length;
    }

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Discrete";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        this.p[index] = value;
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        return this.p[index];
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        return "Cut #" + (index + 1);
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        double r = RandomUtil.getInstance().nextDouble();

        for (int i = 0; i < this.p.length; i++) {
            if (r < this.p[i]) return i;
        }

        throw new IllegalArgumentException();
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Discrete(" + Arrays.toString(this.p) + ")";
    }

    private double[] convert(double... p) {
        for (double _p : p) {
            if (_p < 0) throw new IllegalArgumentException("All arguments must be >= 0: " + _p);
        }

        double sum = 0.0;

        for (double _p : p) {
            sum += _p;
        }

        for (int i = 0; i < p.length; i++) {
            p[i] = p[i] /= sum;
        }

        for (int i = 1; i < p.length; i++) {
            p[i] = p[i - 1] + p[i];
        }

        return p;
    }
}



