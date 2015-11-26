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
 * Stores the parameters needed for Markov blank searches.
 *
 * @author Frank Wimberly, Joseph Ramsey
 */
public final class MbSearchParams implements MeekSearchParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * @serial Cannot be null.
     */
    private IndTestParams indTestParams = new BasicIndTestParams();

    /**
     * @serial Can be null.
     */
    private String targetName;

    /**
     * @serial Range greater than or equal to -1.
     */
    private int depth = -1;

    /**
     * @serial Can be null.
     */
    private List<String> varNames;

    /**
     * @serial Can be null.
     */
    private IndTestType testType = IndTestType.DEFAULT;

    /**
     * @serial Can be null.
     */
    private Graph sourceGraph;


    /**
     * True if cycles are to be aggressively prevented. May be expensive
     * for large graphs (but also useful for large graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    //=================================CONSTRUCTORS=======================//

    /**
     * Constructs a new Markov blank paraneter object. Must be a blank
     * constructor.
     */
    public MbSearchParams() {
        this.indTestParams = new BasicIndTestParams();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static MbSearchParams serializableInstance() {
        return new MbSearchParams();
    }

    //===============================PUBLIC METHODS=======================//


    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public IndTestParams getIndTestParams() {
        return indTestParams;
    }

    public void setIndTestParams2(IndTestParams indTestParams) {
        if (indTestParams == null) {
            throw new NullPointerException();
        }
        this.indTestParams = indTestParams;
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

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set a null knowledge.");
        }

        this.knowledge = knowledge.copy();
    }

    public IKnowledge getKnowledge() {
        return knowledge.copy();
    }

    /**
     * Sets the target variable for the search.
     */
    public void setTargetName(String targetName) {
        if (targetName == null) {
            throw new NullPointerException();
        }
        this.targetName = targetName;
    }

    /*
     * @return the target variable for the search.
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the significance level for the search.
     */
    public void setAlpha(double alpha) {
        getIndTestParams().setAlpha(alpha);
    }

    /**
     * @return the significance level for the search.
     */
    public double getAlpha() {
        return getIndTestParams().getAlpha();
    }

    /**
     * Sets the depth of the associated search.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 or an integer >= 0.");
        }
        this.depth = depth;
    }

    /**
     * @return the int containing the depth of the associated search.
     */
    public int getDepth() {
        return depth;
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

        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (indTestParams == null) {
            throw new NullPointerException();
        }

        if (depth < -1) {
            throw new IllegalArgumentException("Depth out of range: " + depth);
        }
    }


}





