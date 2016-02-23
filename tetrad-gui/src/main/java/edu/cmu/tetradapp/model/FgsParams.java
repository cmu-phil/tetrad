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
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Stores the parameters needed for the GES search and wizard.
 *
 * @author Ricardo Silva
 */
public final class FgsParams implements MeekSearchParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can't be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * @serial Can't be null.
     */
    private FgsIndTestParams indTestParams;

    /**
     * @serial Can be null.
     */
    private List<String> varNames;

    /**
     * @serial Can be null.
     */
    private IndTestType testType;

    /**
     * @serial Can be null.
     */
    private Graph sourceGraph;

    /**
     * True if cycles are to be aggressively prevented. May be expensive
     * for large graphs (but also useful for large graphs).
     */
    private boolean aggressivelyPreventCycles = false;
    private double complexityPenalty;
    private boolean faithfulnessAssumed = false;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new parameter object. Must have a blank constructor.
     */
    public FgsParams() {
        this.indTestParams = new FgsIndTestParams(this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static FgsParams serializableInstance() {
        return new FgsParams();
    }

    //============================PUBLIC METHODS==========================//

    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public IndTestParams getIndTestParams() {
        return this.indTestParams;
    }

    public void setIndTestParams(IndTestParams indTestParams) {
        if (!(indTestParams instanceof FgsIndTestParams)) {
            throw new IllegalArgumentException(
                    "Illegal IndTestParams " + "in GesIndTestParams");
        }
        this.indTestParams = (FgsIndTestParams) indTestParams;
    }

    public List<String> getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List<String> varNames) {
        this.varNames = varNames;
    }

    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public void setSourceGraph(Graph graph) {
        this.sourceGraph = graph;
    }

    public void setIndTestType(IndTestType testType) {
        this.testType = testType;
    }

    public IndTestType getIndTestType() {
        return this.testType;
    }

    @Override
    public void setIndependenceFacts(IndependenceFacts facts) {
        throw new UnsupportedOperationException();
    }

    //  Wrapping GesIndTestParams...

    public double getSamplePrior() {
        return indTestParams.getSamplePrior();
    }

    public void setCellPrior(double cellPrior) {
        indTestParams.setSamplePrior(cellPrior);
    }

    public double getStructurePrior() {
        return indTestParams.getStructurePrior();
    }

    public void setStructurePrior(double structurePrior) {
        indTestParams.setStructurePrior(structurePrior);
    }

    public double getComplexityPenalty() {
        return indTestParams.getPenaltyDiscount();
    }

    public void setComplexityPenalty(double complexityPenalty) {
        indTestParams.setPenaltyDiscount(complexityPenalty);
    }

    public IKnowledge getKnowledge() {
        return knowledge.copy();
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set a null knowledge.");
        }

        this.knowledge = knowledge.copy();
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
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (indTestParams == null) {
            throw new NullPointerException();
        }
    }

    public boolean isFaithfulnessAssumed() {
        return faithfulnessAssumed;
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }
}





