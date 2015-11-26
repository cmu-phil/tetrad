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
public final class GlassoSearchParams implements SearchParams {
    static final long serialVersionUID = 23L;

    private int maxit = 10000;

    private boolean ia = false;

    private boolean is = false;

    private boolean itr = false;

    private boolean ipen = false;

    private double thr = 1.0e-4;

    private List<String> varNames;

    private Graph sourceGraph;


    //=============================CONSTUCTORS===========================//

    /**
     * Constructs a new parameter object. Must have a blank constructor.
     */
    public GlassoSearchParams() {
        this.varNames = new ArrayList<String>();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static GlassoSearchParams serializableInstance() {
        return new GlassoSearchParams();
    }

    //=============================PUBLIC METHODS========================//

    @Override
    public IKnowledge getKnowledge() {
        return new Knowledge2();
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public IndTestParams getIndTestParams() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setIndTestParams2(IndTestParams params) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * @serial Cannot be null. (?)
     */
    public List<String> getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List<String> varNames) {
        if (varNames == null) {
            throw new NullPointerException();
        }

        this.varNames = varNames;
    }

    /**
     * @serial May be null.
     */
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public void setSourceGraph(Graph graph) {
        this.sourceGraph = graph;
    }

    @Override
    public void setIndTestType(IndTestType testType) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public IndTestType getIndTestType() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setIndependenceFacts(IndependenceFacts facts) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();

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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (varNames == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Maximum number of iterations (no effect if ia = true).
     */
    public int getMaxit() {
        return maxit;
    }

    public void setMaxit(int maxit) {
        if (maxit < 1) throw new IllegalArgumentException();
        this.maxit = maxit;
    }

    /**
     * Approximation flag. False if exact solution, true if Meinhausen-Buhlman approximation.
     * False by default.
     */
    public boolean isIa() {
        return ia;
    }

    public void setIa(boolean ia) {
        this.ia = ia;
    }

    /**
     * Initialization flag. false if cold start, initialize using ss. True if warm start, initialize with
     * previous solution stored in ww and wwi. False by default.
     */
    public boolean isIs() {
        return is;
    }

    public void setIs(boolean is) {
        this.is = is;
    }

    /**
     * Trace flag. True if trace information printed. False if trace information not printed.
     * False by default.
     */
    public boolean isItr() {
        return itr;
    }

    public void setItr(boolean itr) {
        this.itr = itr;
    }

    /**
     * Diagonal penalty flag. True if diagonal is penalized. False if diagonal is not penalized.
     * False by default.
     */
    public boolean isIpen() {
        return ipen;
    }

    public void setIpen(boolean ipen) {
        this.ipen = ipen;
    }

    /**
     * Convergence threshold: Iterations stop when absolute average parameter change is less than
     * thr * avg(abs(offdiagonal(ss)). (Suggested default 1.0e-4.)
     */
    public double getThr() {
        return thr;
    }

    public void setThr(double thr) {
        if (thr < 0) throw new IllegalArgumentException();
        this.thr = thr;
    }
}





