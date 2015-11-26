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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Stores the freeParameters for an instance of a SemEstimatorGibbs.
 *
 * @author Frank Wimberly
 */
public final class SemEstimatorGibbsParams implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private SemIm startIm;
    private boolean flatPrior;
    private int numIterations;
    private double stretch;

    private double tolerance;

    //=============================CONSTRUCTORS============================//

    /**
     *
     */
    public SemEstimatorGibbsParams(SemIm startIm, boolean flatPrior,
            double stretch, int numIterations) {

        // note that seed is never used... just as well to get rid of it?

        this.startIm = startIm;
        this.flatPrior = flatPrior;
        this.stretch = stretch;
        this.numIterations = numIterations;

        this.tolerance = 0.0001;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemEstimatorGibbsParams serializableInstance() {
        SemGraph graph = new SemGraph();
        graph.addNode(new GraphNode("X"));
        return new SemEstimatorGibbsParams(new SemIm(new SemPm(graph)), false,
                0.0d, 1);
    }

    public SemIm getStartIm() {
        return startIm;
    }

    public void setStartIm(SemIm startIm) {
        this.startIm = startIm;
    }

    public double getStretch() {
        return stretch;
    }

    public void setStretch(double stretch) {
        this.stretch = stretch;
    }

    public double getTolerance() {
        return tolerance;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public void setNumIterations(int numIterations) {
        this.numIterations = numIterations;
    }

    public void setFlatPrior(boolean flatPrior) {
        this.flatPrior = flatPrior;
    }

    public boolean isFlatPrior() {
        return flatPrior;
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
    }
}


