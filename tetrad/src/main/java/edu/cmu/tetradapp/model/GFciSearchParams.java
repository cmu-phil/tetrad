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
import java.util.ArrayList;
import java.util.List;


/**
 * Stores the parameters needed for the PC search and wizard.
 *
 * @author Raul Salinas
 * @author Joseph Ramsey
 */
public final class GFciSearchParams implements SearchParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * @serial Cannot be null. (?)
     */
    private List varNames;

    /**
     * @serial Cannot be null.
     */
    private GFciIndTestParams indTestParams2 = new GFciIndTestParams();

    /**
     * @serial Cannot be null.
     */
    private IndTestType testType = IndTestType.DEFAULT;

    /**
     * @serial May be null.
     */
    private Graph sourceGraph;

    //=============================CONSTUCTORS===========================//

    /**
     * Constructs a new parameter object. Must have a blank constructor.
     */
    public GFciSearchParams() {
        this.varNames = new ArrayList();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static GFciSearchParams serializableInstance() {
        return new GFciSearchParams();
    }

    //=============================PUBLIC METHODS========================//

    /**
     * Sets a new Knowledge2 for the algorithm.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set a null knowledge.");
        }

        this.knowledge = knowledge.copy();
    }

    public IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    public IndTestParams getIndTestParams() {
        return this.indTestParams2;
    }

    public void setIndTestParams2(IndTestParams indTestParams2) {
        if (indTestParams2 == null) {
            throw new NullPointerException();
        }
        this.indTestParams2 = (GFciIndTestParams) indTestParams2;
    }

    public List getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List varNames) {
        if (varNames == null) {
            throw new NullPointerException();
        }

        this.varNames = varNames;
    }

    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public void setSourceGraph(Graph graph) {
        this.sourceGraph = graph;
    }

    public void setIndTestType(IndTestType testType) {
        if (testType == null) {
            throw new NullPointerException();
        }

        this.testType = testType;
    }

    public IndTestType getIndTestType() {
        return this.testType;
    }

    @Override
    public void setIndependenceFacts(IndependenceFacts facts) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("BasicSearchParams:");
        buf.append("\n\tindTestParams = " + indTestParams2);

        return buf.toString();
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

        if (varNames == null) {
            throw new NullPointerException();
        }

        if (testType == null) {
            throw new NullPointerException();
        }
    }
}


