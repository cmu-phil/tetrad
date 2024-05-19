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
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * The FaskForbiddenGraphModel class is a subclass of KnowledgeBoxModel and represents a model for a graph with
 * forbidden edges. It creates a graph to which the forbidden edges are added based on the given data set and
 * parameters.
 */
public class FaskForbiddenGraphModel extends KnowledgeBoxModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph to which the forbidden edges are to be added.
     */
    private Graph resultGraph = new EdgeListGraph();

    private double[][] data;

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link DataWrapper} object
     * @param params  a {@link Parameters} object
     */
    public FaskForbiddenGraphModel(DataWrapper wrapper, Parameters params) {
        super(params);
        createKnowledge((DataSet) wrapper.getSelectedDataModel(), params);
    }

    private void createKnowledge(DataSet dataSet, Parameters params) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("FaskForbiddenGraphModel only works with continuous data.");
        }

        data = dataSet.getDoubleData().transpose().toArray();

        Knowledge knowledge = getKnowledge();
        if (knowledge == null) {
            return;
        }

        knowledge.clear();

        Score score = new SemBicScore(new CovarianceMatrix(dataSet));

        Fask fask = new Fask(dataSet, score);
        Graph graph = fask.search();

        List<Node> nodes = dataSet.getVariables();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);

                if (Fask.leftRightV2(data[i], data[j])) {
                    knowledge.setForbidden(node1.getName(), node2.getName());
                } else {
                    knowledge.setForbidden(node2.getName(), node1.getName());
                }
            }
        }

        resultGraph = graph;
    }

    /**
     * <p>Getter for the field <code>resultGraph</code>.</p>
     *
     * @return a {@link Graph} object
     */
    public Graph getResultGraph() {
        return this.resultGraph;
    }

}
