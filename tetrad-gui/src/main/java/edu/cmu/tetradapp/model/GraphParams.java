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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Stores parameter values for generating random tetrad-style graphs.
 */
public class GraphParams implements Params {
    static final long serialVersionUID = 23L;

    /**
     * The initialization mode in which probability values in tables are
     * retained where possible and otherwise filled in manually.
     */
    public static final int MANUAL = 0;

    /**
     * The initialization mode in which probability values in tables are
     * retained where possible and otherwise filled in manually.
     */
    public static final int RANDOM = 1;

    /**
     * The initialization mode, either MANUAL or AUTOMATIC.
     *
     * @serial MANUAL or AUTOMATIC.
     */
    private int initializationMode = MANUAL;

    //==============================CONSTRUCTOR=========================//

    /**
     * Blank constructor--no parents are permitted.
     */
    public GraphParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphParams serializableInstance() {
        return new GraphParams();
    }

    //===============================PUBLIC METHODS=====================//


//    public void setMaxEdges(int numEdges) {
//        if (isConnected() && numEdges < getNumNodes()) {
//            throw new IllegalArgumentException("When assuming connectedness, " +
//                    "the number of edges must be at least the number of nodes.");
//        }
//
//        if (!isConnected() && numEdges < 0) {
//            throw new IllegalArgumentException(
//                    "Number of edges Must be greater than or equal to 0: " + numEdges);
//        }
//
//        int maxNumEdges = getNumNodes() * (getNumNodes() - 1) / 2;
//
//        if (numEdges > maxNumEdges) {
//            numEdges = maxNumEdges;
//        }
//
//        Preferences.userRoot().putInt("newGraphNumEdges", numEdges);
//    }

    //    public void setMaxDegree(int maxDegree) {
//        if (!isConnected() && maxDegree < 1) {
//            Preferences.userRoot().putInt("randomGraphMaxDegree", 1);
//            return;
//        }
//
//        if (isConnected() && maxDegree < 3) {
//            Preferences.userRoot().putInt("randomGraphMaxDegree", 3);
//            return;
//        }
//
//        Preferences.userRoot().putInt("randomGraphMaxDegree", maxDegree);
//    }

    //    public void setMaxIndegree(int maxIndegree) {
//        if (!isConnected() && maxIndegree < 1) {
//            Preferences.userRoot().putInt("randomGraphMaxIndegree", 1);
//            return;
//        }
//
//        if (isConnected() && maxIndegree < 2) {
//            Preferences.userRoot().putInt("randomGraphMaxIndegree", 2);
//            return;
//        }
//
//        Preferences.userRoot().putInt("randomGraphMaxIndegree", maxIndegree);
//    }

    //    public void setMaxOutdegree(int maxOutDegree) {
//        if (!isConnected() && maxOutDegree < 1) {
//            Preferences.userRoot().putInt("randomGraphMaxOutdegree", 1);
//            return;
//        }
//
//        if (isConnected() && maxOutDegree < 2) {
//            Preferences.userRoot().putInt("randomGraphMaxOutdegree", 2);
//            return;
//        }
//
//        Preferences.userRoot().putInt("randomGraphMaxOutdegree", maxOutDegree);
//    }

//    public void setConnected(boolean connected) {
//        Preferences.userRoot().putBoolean("randomGraphConnected", connected);
//
//        if (connected) {
//            if (getMaxIndegree() < 2) {
//                setMaxIndegree(2);
//            }
//
//            if (getMaxOutdegree() < 2) {
//                setMaxOutdegree(2);
//            }
//
//            if (getMaxDegree() < 3) {
//                setMaxDegree(3);
//            }
//
//            if (getMaxEdges() < getNumNodes()) {
//                setMaxEdges(getNumNodes());
//            }
//        }
//    }

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

        switch (initializationMode) {
            case MANUAL:
                // Falls through.
            case RANDOM:
                break;
            default:
                throw new IllegalStateException(
                        "Illegal initialization mode: " + initializationMode);
        }
    }
}





