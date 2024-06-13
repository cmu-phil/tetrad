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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Stores the freeParameters for an instance of a SemEstimatorGibbs.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public final class SemEstimatorGibbsParams implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The tolerance for convergence.
     */
    private final double tolerance;

    /**
     * The initial SEM.
     */
    private SemIm startIm;

    /**
     * Whether to use a flat prior.
     */
    private boolean flatPrior;

    /**
     * The number of iterations to run.
     */
    private int numIterations;

    /**
     * The stretch factor.
     */
    private double stretch;

    /**
     * <p>Constructor for SemEstimatorGibbsParams.</p>
     */
    private SemEstimatorGibbsParams(SemIm startIm) {

        // note that seed is never used... just as well to get rid of it?

        this.startIm = startIm;
        this.flatPrior = false;
        this.stretch = 0.0;
        this.numIterations = 1;

        this.tolerance = 0.0001;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemEstimatorGibbsParams} object
     */
    public static SemEstimatorGibbsParams serializableInstance() {
        SemGraph graph = new SemGraph();
        graph.addNode(new GraphNode("X"));
        return new SemEstimatorGibbsParams(new SemIm(new SemPm(graph))
        );
    }

    /**
     * <p>Getter for the field <code>startIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getStartIm() {
        return this.startIm;
    }

    /**
     * <p>Setter for the field <code>startIm</code>.</p>
     *
     * @param startIm a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public void setStartIm(SemIm startIm) {
        this.startIm = startIm;
    }

    /**
     * <p>Getter for the field <code>stretch</code>.</p>
     *
     * @return a double
     */
    public double getStretch() {
        return this.stretch;
    }

    /**
     * <p>Setter for the field <code>stretch</code>.</p>
     *
     * @param stretch a double
     */
    public void setStretch(double stretch) {
        this.stretch = stretch;
    }

    /**
     * <p>Getter for the field <code>tolerance</code>.</p>
     *
     * @return a double
     */
    public double getTolerance() {
        return this.tolerance;
    }

    /**
     * <p>Getter for the field <code>numIterations</code>.</p>
     *
     * @return a int
     */
    public int getNumIterations() {
        return this.numIterations;
    }

    /**
     * <p>Setter for the field <code>numIterations</code>.</p>
     *
     * @param numIterations a int
     */
    public void setNumIterations(int numIterations) {
        this.numIterations = numIterations;
    }

    /**
     * <p>isFlatPrior.</p>
     *
     * @return a boolean
     */
    public boolean isFlatPrior() {
        return this.flatPrior;
    }

    /**
     * <p>Setter for the field <code>flatPrior</code>.</p>
     *
     * @param flatPrior a boolean
     */
    public void setFlatPrior(boolean flatPrior) {
        this.flatPrior = flatPrior;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}


