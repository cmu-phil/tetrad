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

import java.util.Arrays;

/**
 * Wraps a chi square distribution for purposes of drawing random samples.
 * Methods are provided to allow parameters to be manipulated in an interface.
 * A value of n is returned if a number drawn uniformly from [0, 1] is less
 * than the n + 1th p value.
 *
 * @author Joseph Ramsey
 */
public class Discrete implements Distribution {
    static final long serialVersionUID = 23L;

    private final double[] p;

    /**
     * @return 0 with probably 1 - p and 1 with probability p. Each of the
     * supplied values must be in (0, 1), and each must be less than its
     * successor (if it has one).
     */
    public Discrete(double... p) {
        this.p = convert(p);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static Discrete serializableInstance() {
        return new Discrete(.1, .4, .9);
    }

    public int getNumParameters() {
        return p.length;
    }

    public String getName() {
        return "Discrete";
    }

    public void setParameter(int index, double value) {
        p[index] = value;
    }

    public double getParameter(int index) {
        return p[index];
    }

    public String getParameterName(int index) {
        return "Cut #" + (index + 1);
    }

    public double nextRandom() {
        double r = RandomUtil.getInstance().nextDouble();

        for (int i = 0; i < p.length; i++) {
            if (r < p[i]) return i;
        }

        throw new IllegalArgumentException();
    }

    public String toString() {
        return "Discrete(" + Arrays.toString(p) + ")";
    }

    //=============================PRIVATE METHODS=========================//

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



