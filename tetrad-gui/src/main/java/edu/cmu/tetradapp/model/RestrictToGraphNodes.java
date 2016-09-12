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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author Joseph Ramsey
 */
public class RestrictToGraphNodes extends DataWrapper {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//


    public RestrictToGraphNodes(DataWrapper dataWrapper, GraphSource graphSource) {
        DataModel dataModel = restrictToGraphNodes(dataWrapper.getSelectedDataModel(),
                graphSource.getGraph());

        setDataModel(dataModel);
        setSourceGraph(graphSource.getGraph());

        LogDataUtils.logDataModelList("Restruct parent data to nodes in the paraent graph only.", getDataModelList());
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        DataWrapper wrapper =
                new DataWrapper(DataUtils.continuousSerializableInstance());
        return new CorrMatrixConverter(wrapper, new Parameters());
    }

    private DataModel restrictToGraphNodes(DataModel dataModel, Graph graph) {

        if (dataModel instanceof DataSet) {
            DataSet data = (DataSet) dataModel;

            List<Node> dataNodes = new ArrayList<>();
            List<Node> graphNodes = graph.getNodes();

            for (Node graphNode : graphNodes) {
                Node variable = data.getVariable(graphNode.getName());

                if (variable != null) {
                    dataNodes.add(variable);
                }
            }

            DataSet dataSubset = data.subsetColumns(dataNodes);

            return dataSubset;
        } else if (dataModel instanceof ICovarianceMatrix) {
            ICovarianceMatrix cov = (ICovarianceMatrix) dataModel;
            List<String> dataNames = new ArrayList<>();
            List<Node> graphNodes = graph.getNodes();

            for (Node graphNode : graphNodes) {
                Node variable = cov.getVariable(graphNode.getName());

                if (variable != null) {
                    dataNames.add(variable.getName());
                }
            }

            String[] _dataNames = dataNames.toArray(new String[dataNames.size()]);
            ICovarianceMatrix dataSubset = cov.getSubmatrix(_dataNames);

            LogDataUtils.logDataModelList("Parent data restricted to variable in the given graph.", getDataModelList());
            return dataSubset;
        }
        else {
            throw new IllegalStateException("Unexpected data type.");
        }


    }
}



