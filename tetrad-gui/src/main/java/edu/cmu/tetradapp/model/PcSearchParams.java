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
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.search.Lofs2;
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
public final class PcSearchParams implements MeekSearchParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * @serial Cannot be null. (?)
     */
    private List<String> varNames;

    /**
     * @serial
     * @deprecated
     */
    private IndTestParams indTestParams;

    /**
     * @serial Cannot be null.
     */
    private PcIndTestParams indTestParams2 = new PcIndTestParams();

    /**
     * @serial Cannot be null.
     */
    private IndTestType testType = IndTestType.DEFAULT;

    /**
     * @serial May be null.
     */
    private Graph sourceGraph;

    // for LFS
    private boolean r1Done = true;
    private boolean r2Done = false;
    private boolean r3Done = false;
    private Lofs2.Rule rule = Lofs2.Rule.R3;
    private boolean meekDone = false;

    /**
     * True if cycles are to be aggressively prevented. May be expensive
     * for large graphs (but also useful for large graphs).
     */
    private boolean aggressivelyPreventCycles = false;
    private boolean orientStrongerDirection = false;
    private boolean r2Orient2Cycles = false;
    private boolean meanCenterResiduals = false;
    private Lofs.Score score = Lofs.Score.andersonDarling;
    private double epsilon = 0.1;
    private double zeta = 1.0;
    private double selfLoopStrength = 0.0;
    private IndependenceFacts facts = null;

    //=============================CONSTUCTORS===========================//

    /**
     * Constructs a new parameter object. Must have a blank constructor.
     */
    public PcSearchParams() {
        this.varNames = new ArrayList<String>();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcSearchParams serializableInstance() {
        return new PcSearchParams();
    }

    //=============================PUBLIC METHODS========================//


    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

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

    public void setIndTestParams(IndTestParams indTestParams2) {
        if (indTestParams2 == null) {
            throw new NullPointerException();
        }
        this.indTestParams2 = (PcIndTestParams) indTestParams2;
    }

    public List<String> getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List<String> varNames) {
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
        this.facts = facts;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("BasicSearchParams:");
        buf.append("\n\tindTestParams = ").append(indTestParams2);

        return buf.toString();
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. strongerDirection readObject method of this form may be added to any
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

        if (varNames == null) {
            throw new NullPointerException();
        }

        if (indTestParams2 == null) {
            indTestParams2 = new PcIndTestParams();
//            throw new NullPointerException();
        }

        if (testType == null) {
            throw new NullPointerException();
        }
    }


    public Lofs2.Rule getRule() {
        return rule;
    }

    public boolean isR1Done() {
        return r1Done;
    }

    public void setR1Done(boolean r1Done) {
        this.r1Done = r1Done;
    }

    public boolean isR2Done() {
        return r2Done;
    }

    public void setR2Done(boolean r2Done) {
        this.r2Done = r2Done;
    }

    public boolean isMeekDone() {
        return meekDone;
    }

    public void setMeekDone(boolean meekDone) {
        this.meekDone = meekDone;
    }

    public void setOrientStrongerDirection(boolean orientStrongerDirection) {
        this.orientStrongerDirection = orientStrongerDirection;
    }

    public boolean isOrientStrongerDirection() {
        return orientStrongerDirection;
    }

    public void setR2Orient2Cycles(boolean r2Orient2Cycles) {
        this.r2Orient2Cycles = r2Orient2Cycles;
    }

    public boolean isR2Orient2Cycles() {
        return r2Orient2Cycles;
    }

    public void setMeanCenterResiduals(boolean meanCenterResiduals) {
        this.meanCenterResiduals = meanCenterResiduals;
    }

    public boolean isMeanCenterResiduals() {
        return meanCenterResiduals;
    }

    public void setScore(Lofs.Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
    }

    public Lofs.Score getScore() {
        return score;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public void setRule(Lofs2.Rule rule) {
        this.rule = rule;
    }

    public double getZeta() {
        return zeta;
    }

    public void setZeta(double zeta) {
        this.zeta = zeta;
    }

    public double getSelfLoopStrength() {
        return selfLoopStrength;
    }

    public void setSelfLoopStrength(double selfLoopStrength) {
        this.selfLoopStrength = selfLoopStrength;
    }

    public IndependenceFacts getFacts() {
        return facts;
    }

    public void setFacts(IndependenceFacts facts) {
        this.facts = facts;
    }
}






