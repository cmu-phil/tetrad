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
import edu.cmu.tetrad.data.KnowledgeTransferable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * Stores the parameters needed for (a variety of) search algorithms.
 *
 * @author Ricardo Silva, Joseph Ramsey
 */
public interface MimParams extends Params, KnowledgeTransferable, TetradSerializable {

    /**
     * @return a copy of the knowledge for these params.
     */
    IKnowledge getKnowledge();

    /**
     * Sets knowledge to a copy of the given object.
     */
    void setKnowledge(IKnowledge knowledge);

    /**
     * @return the clusters to edit (for some algorithms).
     */
    Clusters getClusters();

    void setClusters(Clusters clusters);

    /**
     * @return the independence test parameters for this search.
     */
    MimIndTestParams getMimIndTestParams();

    /**
     * @return a copy of the latest workbench graph.
     */
    Graph getSourceGraph();

    /**
     * @return the list of variable names.
     */
    List<String> getVarNames();

    /**
     * Sets the list of variable names.
     */
    void setVarNames(List<String> varNames);

    /**
     * Sets the latest workbench graph.
     */
    void setSourceGraph(Graph graph);

    /**
     * @return the significance level.
     */
    double getAlpha();

    /**
     * Sets the significance level.
     */
    void setAlpha(double alpha);

    TestType getTetradTestType();

    void setTetradTestType(TestType tetradTestType);

    TestType getPurifyTestType();

    void setPurifyTestType(TestType purifyTestType);

    int getAlgorithmType();

    void setAlgorithmType(int tt);

    double getMaxP();

    void setMaxP(double p);

    void setMaxStructureGraph(Graph structureGraph);

    Graph getMaxStructureGraph();

    boolean isShowMaxP();

    void setShowMaxP(boolean b);

    boolean isInclude3Clusters();

    void setInclude3Clusters(boolean selected);

    void setMaxFullGraph(Graph fullGraph);

    Graph getMaxFullGraph();

    void setMaxClusters(Clusters clusters);

    void setMaxAlpha(double alpha);

    Clusters getMaxClusters();

    double getMaxAlpha();
}





