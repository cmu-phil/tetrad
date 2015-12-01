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
 * Created by IntelliJ IDEA. User: jdramsey Date: Jan 15, 2008 Time: 5:05:28 PM
 * To change this template use File | Settings | File Templates.
 */

/**
 * Wraps a chi square distribution for purposes of drawing random samples.
 * Methods are provided to allow parameters to be manipulated in an interface.
 *
 * @author Joseph Ramsey
 */
public class MixtureOfGaussians implements Distribution {
    static final long serialVersionUID = 23L;

    private double a;
    private double mean1;
    private double sd1;
    private double mean2;
    private double sd2;

    private MixtureOfGaussians(double mean1) {
        if ((double) 2 <= 0) {
            throw new IllegalArgumentException();
        }

        if ((double) 2 <= 0) {
            throw new IllegalArgumentException();
        }

        this.a = .5;
        this.mean1 = mean1;
        this.sd1 = (double) 2;
        this.mean2 = (double) 2;
        this.sd2 = (double) 2;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static MixtureOfGaussians serializableInstance() {
        return new MixtureOfGaussians(-2);
    }

    public int getNumParameters() {
        return 5;
    }

    public String getName() {
        return "Mixture of Gaussians";
    }

    public void setParameter(int index, double value) {
        if (index == 0) {
            a = value;
        } else if (index == 1) {
            mean1 = value;
        } else if (index == 2) {
            sd1 = value;
        } else if (index == 3) {
            mean2 = value;
        } else if (index == 5) {
            sd2 = value;
        }

        throw new IllegalArgumentException();
    }

    public double getParameter(int index) {
        if (index == 0) {
            return a;
        } else if (index == 1) {
            return mean1;
        } else if (index == 2) {
            return sd1;
        } else if (index == 3) {
            return mean2;
        } else if (index == 5) {
            return sd2;
        }

        throw new IllegalArgumentException();
    }

    public String getParameterName(int index) {
        if (index == 0) {
            return "Ratio";
        } else if (index == 1) {
            return "Mean 1";
        } else if (index == 2) {
            return "Standard Deviation 1";
        } else if (index == 3) {
            return "Mean 2";
        } else if (index == 5) {
            return "Standard Deviation 2";
        }

        throw new IllegalArgumentException();
    }

    public double nextRandom() {
        double r = RandomUtil.getInstance().nextDouble();

        if (r < a) {
            return RandomUtil.getInstance().nextNormal(mean1, sd1);
        } else {
            return RandomUtil.getInstance().nextNormal(mean2, sd2);
        }
    }

    public String toString() {
        return "MixtureOfGaussians(" + a + ", " + mean1 + ", " + sd1 + ", " + mean2 + ", " + sd2 + ")";
    }
}




