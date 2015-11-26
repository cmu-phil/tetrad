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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Stores basic independence test parameters.
 *
 * @author Joseph Ramsey
 */
public final class PcIndTestParams implements IndTestParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Range [0, 1].
     */
    private double alpha = 0.05;

    /**
     * @serial Range [0, 1].
     */
    private double zeroEdgeP = 0.05;

    /**
     * @serial Range greater than -1.
     */
    private int depth = -1;

    /**
     * For Hbsms.
     */
    private int beamWidth = 5;

    /**
     * @serial
     * @deprecated
     */
    private boolean collidersOrientedLocally = false;

    //============================CONSTRUCTORS=========================//

    public PcIndTestParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static PcIndTestParams serializableInstance() {
        return new PcIndTestParams();
    }

    //============================PUBLIC METHODS=======================//

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }
        this.alpha = alpha;
    }

    public double getZeroEdgeP() {
        return zeroEdgeP;
    }

    public void setZeroEdgeP(double zeroEdgeP) {
        if (zeroEdgeP < 0.0 || zeroEdgeP > 1.0) {
            throw new IllegalArgumentException("P value out of range: " + zeroEdgeP);
        }
        this.zeroEdgeP = zeroEdgeP;
    }

    /**
     * Sets the depth for search algorithms that require it.
     *
     * @param depth
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }
        this.depth = depth;
    }

    /**
     * @return the depth of the search.
     *
     * @return depth
     */
    public int getDepth() {
        return this.depth;
    }

    public int getBeamWidth() {
        return beamWidth;
    }

    public void setBeamWidth(int beamWidth) {
        if (beamWidth < 1) throw new IllegalArgumentException("Beam width < 1: " + beamWidth);
        this.beamWidth = beamWidth;
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (alpha < 0.0 && alpha > 1.0) {
            throw new IllegalArgumentException(
                    "Alpha should be in [0, 1]: " + alpha);
        }

        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth should be >= -1: " + depth);
        }

        if (beamWidth == 0) beamWidth = 5;
    }
}





