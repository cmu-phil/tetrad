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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FindOneFactorClusters;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joseph Ramsey
 */
public class FofcIndTestParams implements MimIndTestParams {
    static final long serialVersionUID = 23L;
    private TestType tetradTestType;
    private TestType purifyTestType;
    private Clusters clusters;
    private IKnowledge knowledge;
    private Graph sourceGraph;

    public TestType getTetradTestType() {
        return tetradTestType;
    }

    public void setTetradTestType(TestType tetradTestType) {
        this.tetradTestType = tetradTestType;
    }

    public TestType getPurifyTestType() {
        return purifyTestType;
    }

    public void setPurifyTestType(TestType purifyTestType) {
        this.purifyTestType = purifyTestType;
    }

    public void setSourceGraph(Graph sourceGraph) {
        this.sourceGraph = sourceGraph;
    }

    public void setClusters(Clusters clusters) {
        this.clusters = clusters;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    private double alpha = 0.001;

    private List varNames;

    private FindOneFactorClusters.Algorithm algorithm = FindOneFactorClusters.Algorithm.GAP;

    private List<String> latentVarNames = new ArrayList<>();

    //=============================CONSTRUCTORS==========================//

    public FofcIndTestParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static FofcIndTestParams serializableInstance() {
        return new FofcIndTestParams();
    }

    /**
     * @serial Range [0, 1].
     */
    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * @serial Can be null.
     */
    public List getVarNames() {
        return varNames;
    }

    @Override
    public List<String> getLatentVarNames() {
        return this.latentVarNames;
    }

    @Override
    public void setLatentVarNames(List<String> latentVarNames) {
        this.latentVarNames = latentVarNames;
    }

    @Override
    public Graph getSourceGraph() {
        return sourceGraph;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public Clusters getClusters() {
        return this.clusters;
    }

    public void setVarNames(List varNames) {
        this.varNames = varNames;
    }

    /**
     * @serial
     */
    public FindOneFactorClusters.Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(FindOneFactorClusters.Algorithm algorithm) {
        this.algorithm = algorithm;
    }

}





