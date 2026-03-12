/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.LatentParentRecovery;
import edu.cmu.tetrad.search.LatentParentRecoveryRobust;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;

import java.util.ArrayList;

/**
 * <p>PagFromDagGraphWrapper class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class LatentParentRecoveryGraphWrapper extends GraphWrapper implements DoNotAddOldModel {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for PagFromDagGraphWrapper.</p>
     *
     * @param source     a {@link GraphSource} object
     * @param parameters a {@link Parameters} object
     */
    public LatentParentRecoveryGraphWrapper(GraphSource source, DataWrapper dataWrapper, Parameters parameters) {
        this(source.getGraph(), (DataSet) dataWrapper.getSelectedDataModel(), parameters);
    }


    /**
     * <p>Constructor for PagFromDagGraphWrapper.</p>
     *
     * @param graph a {@link Graph} object
     */
    public LatentParentRecoveryGraphWrapper(Graph graph, DataSet data, Parameters parameters) {
        super(graph);

        graph = GraphUtils.replaceNodes(graph, data.getVariables());

        for (Node node : data.getVariables()) {
            if (graph.getNode(node.getName()) == null) {
                graph.addNode(node);
            }
        }

        LatentParentRecoveryRobust latentParentRecovery = new LatentParentRecoveryRobust(data, graph);
        setGraph(latentParentRecovery.search());

        TetradLogger.getInstance().log("\nLatent Parent Recovery.");
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link LatentParentRecoveryGraphWrapper} object
     */
    public static LatentParentRecoveryGraphWrapper serializableInstance() {
        EdgeListGraph edgeListGraph = EdgeListGraph.serializableInstance();
        DoubleDataBox doubleDataBox = new DoubleDataBox(0, 0);
        BoxDataSet boxDataSet = new BoxDataSet(doubleDataBox, new ArrayList<>());
        return new LatentParentRecoveryGraphWrapper(edgeListGraph, boxDataSet, new Parameters());
    }

    //======================== Private Method ======================//


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}




