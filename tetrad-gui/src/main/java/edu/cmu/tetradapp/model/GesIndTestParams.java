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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Ricardo Silva
 */
public class
GesIndTestParams implements IndTestParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Range (-inf, +inf?).
     */
    private double structurePrior = 0.1;

    /**
     * @serial Range (0, +inf).
     */
    private double samplePrior = 1;

    /**
     * @serial Range (-inf, +inf?)
     * @deprecated
     */
    private boolean uniformStructurePrior;

    /**
     * @serial Can't be null.
     */
    private GesParams originalParams;

    /**
     * @serial Range [0, 1].
     */
    private double alpha = 0.05;

    /**
     * @serial Range greater than or equal to -1.
     */
    private int depth = -1;

    /**
     * @deprecated Required for serialization.
     */
    private double minimalImprovement;

    /**
     * The penalty discount--the BIC penalty for continuous case is multiplied
     * by this.
     */
    private double penaltyDiscount = 2.0;

    /**
     * True iff the f cutoff should be used in place of the usual BIC delta
     * = 0 cutoff in the forward search.
     */
    private boolean useFCutoff = false;

    /**
     * The p value of the F cutoff, if used.
     */
    private double fCutoffP = 0.01;

    /**
     * The minimum jump in score for forward and backward searches.
     */
    private double minJump = 0.0;

    /**
     * @serial
     * @deprecated Use penaltyDiscount instead. But don't delete.
     */
    private double commplexityPenalty = 0;

    /**
     * The number of patterns to save.
     */
    private int numPatternsToSave = 1;

    /**
     * True if the first nontriangualar search result should be found.
     */
    private boolean firstNontriangular = false;

    //=========================CONSTRUCTORS==============================//

    public GesIndTestParams(GesParams originalParams) {
        if (originalParams == null) {
            throw new NullPointerException();
        }

        this.originalParams = originalParams;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GesIndTestParams serializableInstance() {
        return new GesIndTestParams(GesParams.serializableInstance());
    }

    //=========================PUBLIC METHODS===========================//

    public double getSamplePrior() {
        return this.samplePrior;
    }

    public void setSamplePrior(double samplePrior) {
        if (samplePrior < 0.0) {
            throw new IllegalArgumentException("Should be non-negative.");
        }
        this.samplePrior = samplePrior;
    }

    public double getStructurePrior() {
        return this.structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        if (structurePrior < 0.0) {
            throw new IllegalArgumentException("Should be non-negative.");
        }

        this.structurePrior = structurePrior;
    }

    public IKnowledge getKnowledge() {
        return originalParams.getKnowledge();
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }
        this.alpha = alpha;
    }

    public void setDepth(int depth) {
        if (!(depth >= -1)) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    public int getDepth() {
        return this.depth;
    }

    public int getNumLags() {
        throw new UnsupportedOperationException();
    }

    public void setNumLags(int numLags) {
        throw new UnsupportedOperationException();
    }

    public int getNumTimePoints() {
        throw new UnsupportedOperationException();
    }

    public void setNumTimePoints(int numTimePoints) {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public boolean isTimeSeries() {
        return false;
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

        if (originalParams == null) {
            throw new NullPointerException();
        }

        if (!(depth >= -1)) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("Alpha out of range: " + alpha);
        }

        if (!(depth >= -1)) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        if (penaltyDiscount == 0 && commplexityPenalty > 0) {
            penaltyDiscount = commplexityPenalty;
            commplexityPenalty = 0;
        }

        if (numPatternsToSave < 0) numPatternsToSave = 0;
    }

    public int getNumPatternsToSave() {
        return numPatternsToSave;
    }

    public void setNumPatternsToSave(int numPatternsToSave) {
        if (numPatternsToSave < 0) {
            throw new IllegalArgumentException("Must save at least 0 patterns: " + numPatternsToSave);
        }

        this.numPatternsToSave = numPatternsToSave;
    }

    public void setFirstNontriangular(boolean firstNontriangular) {
        this.firstNontriangular = firstNontriangular;
    }

    public boolean isFirstNontriangular() {
        return firstNontriangular;
    }

    public void setUseFCutoff(boolean useFCutoff) {
        this.useFCutoff = useFCutoff;
    }

    public void setMinJump(double minJump) {
        if (minJump < 0) throw new IllegalArgumentException();

        this.minJump = minJump;
    }

    public double getMinJump() {
        return minJump;
    }

    /**
     * The f discount p value, if used.
     */
    public double getfCutoffP() {
        return fCutoffP;
    }
}





