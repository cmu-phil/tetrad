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

/**
 * Implements a Beta distribution for purposes of drawing random numbers.
 * The parameters are alpha and beta. See Wikipedia.
 *
 * @author Joseph Ramsey
 */
public class Beta implements Distribution {
    static final long serialVersionUID = 23L;

    /**
     * Ibid.
     */
    private double alpha = 0.5;

    /**
     * Ibid.
     */
    private double beta = 0.5;

    /**
     * Ibid.
     */
    private Beta() {
        this.alpha = .5;
        this.beta = .5;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Beta serializableInstance() {
        return new Beta();
    }

    /**
     * See interface.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextBeta(alpha, beta);
    }

    /**
     * The order of parameters is alpha = 0, beta = 1.
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            alpha = value;
        } else if (index == 1 && value >= 0) {
            beta = value;
        } else {
            throw new IllegalArgumentException("Illegal value: " + value);
        }
    }

    /**
     * The order of parameters is alpha = 0, beta = 1.
     */
    public double getParameter(int index) {
        if (index == 0) {
            return alpha;
        } else if (index == 1) {
            return beta;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    /**
     * The order of parameters is alpha = 0, beta = 1.
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Alpha";
        } else if (index == 1) {
            return "Beta";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * Uh, there are 2 parameters...
     *
     * @return Ibid.
     */
    public int getNumParameters() {
        return 2;
    }

    /**
     * Please don't make me say it...
     */
    public String getName() {
        return "Beta";
    }

    /**
     * A string representation of the distribution.
     */
    public String toString() {
        return "B(" + alpha + ", " + beta + ")";
    }
}




