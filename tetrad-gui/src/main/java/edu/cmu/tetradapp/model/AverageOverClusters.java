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
import edu.cmu.tetrad.util.Parameters;
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


    public AverageOverClusters(final DataWrapper dataWrapper, final MeasurementModelWrapper measurementModelWrapper,
                               final Parameters parameters) {
        final DataModel dataModel = calcAveragesOverClusters(dataWrapper.getSelectedDataModel(),
                measurementModelWrapper);

        setDataModel(dataModel);

        LogDataUtils.logDataModelList("Restruct parent data to nodes in the paraent graph only.", getDataModelList());
    }

    public AverageOverClusters(final DataWrapper dataWrapper, final MeasurementModelWrapper measurementModelWrapper,
                               final GraphWrapper trueGraphWrapper) {
        this.trueGraph = trueGraphWrapper.getGraph();

        final DataModel dataModel = calcAveragesOverClusters(dataWrapper.getSelectedDataModel(),
                measurementModelWrapper);

        setDataModel(dataModel);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    private DataModel calcAveragesOverClusters(final DataModel dataModel, final MeasurementModelWrapper measurementModelWrapper) {
        if (dataModel instanceof DataSet) {
            final DataSet data = (DataSet) dataModel;
            final Clusters clusters = measurementModelWrapper.getClusters();

            final List<Node> avgVars = new ArrayList<>();

            for (int j = 0; j < clusters.getNumClusters(); j++) {
                Node latent = null;

                if (this.trueGraph != null) {
                    final List<String> cluster = clusters.getCluster(j);

                    CLUSTER:
                    for (final String _var : cluster) {
                        final Node node = this.trueGraph.getNode(_var);
                        final List<Node> parents = this.trueGraph.getParents(node);

                        for (final Node parent : parents) {
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


            final DataSet avgData = new BoxDataSet(new DoubleDataBox(data.getNumRows(), avgVars.size()), avgVars);

            for (int i = 0; i < data.getNumRows(); i++) {
                for (int j = 0; j < clusters.getNumClusters(); j++) {
                    final List<String> cluster = clusters.getCluster(j);

                    double sum = 0.0;

                    for (final String _node : cluster) {
                        final Node node = data.getVariable(_node);
                        final double d = data.getDouble(i, data.getColumn(node));
                        sum += d;
                    }

                    final double avg = sum / cluster.size();
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
        } else {
            throw new IllegalStateException("Unexpected data type.");
        }


    }

    private Graph reidentifyVariables(final Graph searchGraph, final Graph trueGraph) {
        if (trueGraph == null) {
            return searchGraph;
        }

        final Graph reidentifiedGraph = new EdgeListGraph();
//        Graph trueGraph = semIm.getSemPm().getGraph();

        for (final Node latent : searchGraph.getNodes()) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            boolean added = false;

            final List<Node> searchChildren = searchGraph.getChildren(latent);

            for (final Node _latent : trueGraph.getNodes()) {
                if (_latent.getNodeType() != NodeType.LATENT) ;

                final List<Node> trueChildren = trueGraph.getChildren(_latent);

                for (final Node node2 : new ArrayList<>(trueChildren)) {
                    if (node2.getNodeType() == NodeType.LATENT) {
                        trueChildren.remove(node2);
                    }
                }

                boolean containsAll = true;

                for (final Node child : searchChildren) {
                    boolean contains = false;

                    for (final Node _child : trueChildren) {
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

                    for (final Node child : searchChildren) {
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

                for (final Node child : searchChildren) {
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



