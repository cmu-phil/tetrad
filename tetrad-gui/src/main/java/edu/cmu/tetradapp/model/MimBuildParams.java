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

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestMimBuild;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Stores the parameters needed for the MimBuild search and wizard.
 *
 * @author Ricardo Silva
 */
public final class MimBuildParams implements MimParams {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private MimBuildIndTestParams indTestParams;

    /**
     * @serial Cannot be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * @serial Cannot be null.
     */
    private Clusters clusters = new Clusters();

    /**
     * @serial Can be null.
     */
    private List varNames;

    /**
     * @serial Can be null.
     */
    private Graph sourceGraph;
    private double maxP = -1;
    private Graph maxGraph = null;
    private boolean showMaxPSelected;
    private boolean include3Clusters;
    private Graph maxFullGraph;
    private Clusters maxClusters;
    private double maxAlpha;


    //===========================CONSTRUCTORS=============================//

    public MimBuildParams() {
        indTestParams = new MimBuildIndTestParams(0.0001, 1,
                IndTestMimBuild.MIMBUILD_MLE, this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static MimBuildParams serializableInstance() {
        return new MimBuildParams();
    }

    //===========================PUBLIC METHODS==========================//

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public Clusters getClusters() {
        return this.clusters;
    }

    public void setClusters(Clusters clusters) {
        if (clusters == null) {
            throw new NullPointerException();
        }
        this.clusters = clusters;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set a null knowledge.");
        }

        this.knowledge = knowledge.copy();
    }

    public MimIndTestParams getMimIndTestParams() {
        return this.indTestParams;
    }

    public List<String> getVarNames() {
        return this.varNames;
    }

    public void setVarNames(List<String> varNames) {
        this.varNames = varNames;
//
//        System.out.println("MimBuildParams: Setting var names: " + varNames);
//
//        for (String var : knowledge.getVariables()) {
//            if (!varNames.contains(var)) {
//                knowledge.removeVariable(var);
//            }
//        }
//
//        for (String var : varNames) {
//            knowledge.addVariable(var);
//        }
    }

    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    public void setSourceGraph(Graph graph) {
        this.sourceGraph = graph;
    }

    public double getAlpha() {
        return indTestParams.getAlpha();
    }

    public void setAlpha(double alpha) {
        indTestParams.setAlpha(alpha);
    }

    public int getAlgorithmType() {
        return indTestParams.getAlgorithmType();
    }

    public void setAlgorithmType(int tt) {
        if (tt == IndTestMimBuild.MIMBUILD_GES_ABIC ||
                tt == IndTestMimBuild.MIMBUILD_GES_SBIC ||
                tt == IndTestMimBuild.MIMBUILD_PC) {
            indTestParams.setAlgorithmType(tt);
        }
    }

    @Override
    public double getMaxP() {
        return this.maxP;
    }

    @Override
    public void setMaxP(double p) {
        this.maxP = p;
    }


    public void setMaxStructureGraph(Graph maxGraph) {
        this.maxGraph = maxGraph;
    }

    public Graph getMaxStructureGraph() {
        return maxGraph;
    }

    @Override
    public boolean isShowMaxP() {
        return showMaxPSelected;
    }

    @Override
    public void setShowMaxP(boolean b) {
        this.showMaxPSelected = b;
    }

    @Override
    public boolean isInclude3Clusters() {
        return include3Clusters;
    }

    @Override
    public void setInclude3Clusters(boolean selected) {
        this.include3Clusters = selected;
    }

    public void setMaxFullGraph(Graph fullGraph) {
        this.maxFullGraph = fullGraph;
    }

    public Graph getMaxFullGraph() {
        return this.maxFullGraph;
    }

    @Override
    public void setMaxClusters(Clusters clusters) {
        this.maxClusters = clusters;
    }

    @Override
    public void setMaxAlpha(double alpha) {
        this.maxAlpha = alpha;
    }

    @Override
    public Clusters getMaxClusters() {
        return this.maxClusters;
    }

    @Override
    public double getMaxAlpha() {
        return this.maxAlpha;
    }

    public TestType getTetradTestType() {
        throw new UnsupportedOperationException();
    }

    public void setTetradTestType(TestType tetradTestType) {
        throw new UnsupportedOperationException();
    }

    public TestType getPurifyTestType() {
        throw new UnsupportedOperationException();
    }

    public void setPurifyTestType(TestType purifyTestType) {
        throw new UnsupportedOperationException();
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

        if (indTestParams == null) {
            throw new NullPointerException();
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (clusters == null) {
            throw new NullPointerException();
        }
    }
}





