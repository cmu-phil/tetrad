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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author Joseph Ramsey
 */
public class AverageOverClusters extends DataWrapper {
    static final long serialVersionUID = 23L;

    private Graph trueGraph = null;

    //=============================CONSTRUCTORS==============================//


    public AverageOverClusters(DataWrapper dataWrapper, MeasurementModelWrapper measurementModelWrapper) {
        DataModel dataModel = calcAveragesOverClusters(dataWrapper.getSelectedDataModel(),
                measurementModelWrapper);

        setDataModel(dataModel);

        LogDataUtils.logDataModelList("Restruct parent data to nodes in the paraent graph only.", getDataModelList());
    }

    public AverageOverClusters(DataWrapper dataWrapper, MeasurementModelWrapper measurementModelWrapper,
                               GraphWrapper trueGraphWrapper) {
        this.trueGraph = trueGraphWrapper.getGraph();

        DataModel dataModel = calcAveragesOverClusters(dataWrapper.getSelectedDataModel(),
                measurementModelWrapper);

        setDataModel(dataModel);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        DataWrapper wrapper =
                new DataWrapper(DataUtils.continuousSerializableInstance());
        return new CorrMatrixConverter(wrapper);
    }

    public DataModel calcAveragesOverClusters(DataModel dataModel, MeasurementModelWrapper measurementModelWrapper) {
        if (dataModel instanceof DataSet) {
            DataSet data = (DataSet) dataModel;
            Clusters clusters = measurementModelWrapper.getClusters();

            List<Node> avgVars = new ArrayList<Node>();

            for (int j = 0; j < clusters.getNumClusters(); j++) {
                Node latent = null;

                if (trueGraph != null) {
                    List<String> cluster = clusters.getCluster(j);

                    CLUSTER:
                    for (String _var : cluster) {
                        Node node = trueGraph.getNode(_var);
                        List<Node> parents = trueGraph.getParents(node);

                        for (Node parent : parents) {
                            if (parent.getNodeType() == NodeType.LATENT) {
                                if (latent == null) {
                                    latent = parent;
                                } else if (latent != parent) {
                                    break CLUSTER;
                                }
                            }
                        }
                    }
                }

                if (latent != null) {
                    avgVars.add(new ContinuousVariable(latent.getName()));
                } else {
                    avgVars.add(new ContinuousVariable("Avg" + (j + 1)));
                }
            }


            DataSet avgData = new ColtDataSet(data.getNumRows(), avgVars);

            for (int i = 0; i < data.getNumRows(); i++) {
                for (int j = 0; j < clusters.getNumClusters(); j++) {
                    List<String> cluster = clusters.getCluster(j);

                    double sum = 0.0;

                    for (String _node : cluster) {
                        Node node = data.getVariable(_node);
                        double d = data.getDouble(i, data.getColumn(node));
                        sum += d;
                    }

                    double avg = sum / cluster.size();
                    avgData.setDouble(i, j, avg);
                }
            }

            // This concatenates in a certain way.
//            DataSet avgData = new ColtDataSet(4 * data.getNumRows(), avgVars);
//
//            int k = 0;
//
//            for (int i = 0; i < data.getNumRows(); i++) {
//                for (int j = 0; j < clusters.getNumClusters(); j++) {
//                    List<String> cluster = clusters.getCluster(j);
//
//                    int m = -1;
//
//                    for (String _node : cluster) {
//                        m++;
//                        if (m > 4) continue;
//
//                        Node node = data.getVariable(_node);
//                        avgData.setDouble(k + m, j, data.getDouble(i, data.getColumn(node)));
//                    }
//                }
//
//                k += 4;
//            }

            return avgData;
        } else

        {
            throw new IllegalStateException("Unexpected data type.");
        }


    }

    private Graph reidentifyVariables(Graph searchGraph, Graph trueGraph) {
        if (trueGraph == null) {
            return searchGraph;
        }

        Graph reidentifiedGraph = new EdgeListGraph();
//        Graph trueGraph = semIm.getSemPm().getGraph();

        for (Node latent : searchGraph.getNodes()) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            boolean added = false;

            List<Node> searchChildren = searchGraph.getChildren(latent);

            for (Node _latent : trueGraph.getNodes()) {
                if (_latent.getNodeType() != NodeType.LATENT) ;

                List<Node> trueChildren = trueGraph.getChildren(_latent);

                for (Node node2 : new ArrayList<Node>(trueChildren)) {
                    if (node2.getNodeType() == NodeType.LATENT) {
                        trueChildren.remove(node2);
                    }
                }

                boolean containsAll = true;

                for (Node child : searchChildren) {
                    boolean contains = false;

                    for (Node _child : trueChildren) {
                        if (child.getName().equals(_child.getName())) {
                            contains = true;
                            break;
                        }
                    }

                    if (!contains) {
                        containsAll = false;
                        break;
                    }
                }

                if (containsAll) {
                    reidentifiedGraph.addNode(_latent);

                    for (Node child : searchChildren) {
                        if (!reidentifiedGraph.containsNode(child)) {
                            reidentifiedGraph.addNode(child);
                        }

                        reidentifiedGraph.addDirectedEdge(_latent, child);
                    }

                    added = true;
                    break;
                }
            }

            if (!added) {
                reidentifiedGraph.addNode(latent);

                for (Node child : searchChildren) {
                    if (!reidentifiedGraph.containsNode(child)) {
                        reidentifiedGraph.addNode(child);
                    }

                    reidentifiedGraph.addDirectedEdge(latent, child);
                }
            }
        }

        return reidentifiedGraph;
    }
}



