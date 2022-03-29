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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the
 * BuildPureClusters algorithm.
 *
 * @author Ricardo Silva
 */
public class BuildPureClustersRunner extends AbstractMimRunner
        implements GraphSource, Unmarshallable {
    static final long serialVersionUID = 23L;

    /**
     * To reidentify variables.
     */
    private SemIm semIm;
    private Graph trueGraph;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper.
     */

    public BuildPureClustersRunner(final DataWrapper dataWrapper,
                                   final Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);

    }

    public BuildPureClustersRunner(final DataWrapper dataWrapper, final SemImWrapper semImWrapper,
                                   final Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);
        this.semIm = semImWrapper.getSemIm();
        this.trueGraph = this.semIm.getSemPm().getGraph();
    }

    public BuildPureClustersRunner(final DataWrapper dataWrapper, final GraphWrapper graphWrapper,
                                   final Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);
        this.trueGraph = graphWrapper.getGraph();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        final boolean rKey = getParams().getBoolean("BPCrDown", false);

        final BpcAlgorithmType algorithm = (BpcAlgorithmType) getParams().get("bpcAlgorithmthmType", BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS);

        final Graph searchGraph;

        if (rKey) {
            final Washdown washdown;
            final Object source = getData();

            if (source instanceof DataSet) {
                washdown = new Washdown((DataSet) source, getParams().getDouble("alpha", 0.001));
            } else {
                washdown = new Washdown((CovarianceMatrix) source, getParams().getDouble("alpha", 0.001));
            }

            searchGraph = washdown.search();
        } else {
            final TestType tetradTestType = (TestType) getParams().get("tetradTestType", TestType.TETRAD_WISHART);

            if (algorithm == BpcAlgorithmType.TETRAD_PURIFY_WASHDOWN) {
                final BpcTetradPurifyWashdown bpc;
                final Object source = getData();

                if (source instanceof DataSet) {
                    bpc = new BpcTetradPurifyWashdown(
                            (DataSet) source,
                            tetradTestType,
                            getParams().getDouble("alpha", 0.001));

                } else {
                    bpc = new BpcTetradPurifyWashdown((ICovarianceMatrix) source,
                            tetradTestType, getParams().getDouble("alpha", 0.001));

                }

                searchGraph = bpc.search();
            } else if (algorithm == BpcAlgorithmType.BUILD_PURE_CLUSTERS) {
                final BuildPureClusters bpc;
                final DataModel source = getData();

                final TestType testType = (TestType) getParams().get("tetradTestType", TestType.TETRAD_WISHART);

                if (source instanceof ICovarianceMatrix) {
                    bpc = new BuildPureClusters((ICovarianceMatrix) source,
                            getParams().getDouble("alpha", 0.001),
                            testType
                    );
                } else if (source instanceof DataSet) {
                    bpc = new BuildPureClusters(
                            (DataSet) source, getParams().getDouble("alpha", 0.001),
                            testType
                    );
                } else {
                    throw new IllegalArgumentException();
                }

                searchGraph = bpc.search();

            }
//            else if (algorithm == BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS) {
////                FindOneFactorClusters bpc;
////                Object source = getContinuousData();
////
////                if (source instanceof DataSet) {
////                    bpc = new FindOneFactorClusters(
////                            (DataSet) source,
////                            tetradTestType,
////                            getParameters().getAlternativePenalty());
////                } else {
////                    bpc = new FindOneFactorClusters((ICovarianceMatrix) source,
////                            tetradTestType, getParameters().getAlternativePenalty());
////                }
////
////                searchGraph = bpc.search();
//
//                FindOneFactorClusters2 bpc;
//                Object source = getContinuousData();
//                FindOneFactorClusters2.Algorithm sag = FindOneFactorClusters2.Algorithm.SAG;
//
//                if (source instanceof DataSet) {
//                    bpc = new FindOneFactorClusters2(
//                            (DataSet) source,
//                            tetradTestType, sag,
//                            getParameters().getAlternativePenalty());
//
////                    bpc = new FindTwoFactorClusters4(
////                            (DataSet) source,
////                            getParameters().getAlternativePenalty());
//                } else {
//                    bpc = new FindOneFactorClusters2((ICovarianceMatrix) source,
//                            tetradTestType, sag, getParameters().getAlternativePenalty());
////
////                    bpc = new FindTwoFactorClusters4((ICovarianceMatrix) source,
////                            getParameters().getAlternativePenalty());
//                }
//
//                searchGraph = bpc.search();
//
//            }
//            else if (algorithm == BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS) {
//                FindTwoFactorClusters2 bpc;
//                Object source = getContinuousData();
//
//                if (source instanceof DataSet) {
//                    bpc = new FindTwoFactorClusters2(
//                            (DataSet) source,
//                            tetradTestType,
//                            getParameters().getAlternativePenalty());
//
////                    bpc = new FindTwoFactorClusters4(
////                            (DataSet) source,
////                            getParameters().getAlternativePenalty());
//                } else {
//                    bpc = new FindTwoFactorClusters2((ICovarianceMatrix) source,
//                            tetradTestType, getParameters().getAlternativePenalty());
////
////                    bpc = new FindTwoFactorClusters4((ICovarianceMatrix) source,
////                            getParameters().getAlternativePenalty());
//                }
//
//                searchGraph = bpc.search();
//            }
            else {
                throw new IllegalStateException();
            }
        }

        if (this.semIm != null) {
            final List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);

            final List<String> variableNames = ReidentifyVariables.reidentifyVariables2(partition, this.trueGraph, (DataSet) getData());
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables2(searchGraph, semIm);
        } else if (this.trueGraph != null) {
            final List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);
            final List<String> variableNames = ReidentifyVariables.reidentifyVariables1(partition, this.trueGraph);
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables(searchGraph, trueGraph);
        }

        System.out.println("Search Graph " + searchGraph);

        try {
            final Graph graph = new MarshalledObject<>(searchGraph).get();
            GraphUtils.circleLayout(graph, 200, 200, 150);
            GraphUtils.fruchtermanReingoldLayout(graph);
            setResultGraph(graph);
            setClusters(MimUtils.convertToClusters(graph, getData().getVariables()));
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void rename(final Graph searchGraph, final List<List<Node>> partition, final List<String> variableNames) {
        for (final Node node : searchGraph.getNodes()) {
            if (!(node.getNodeType() == NodeType.LATENT)) {
                continue;
            }

            final List<Node> children = searchGraph.getChildren(node);
            children.removeAll(ReidentifyVariables.getLatents(searchGraph));

            for (int i = 0; i < partition.size(); i++) {
                if (new HashSet<>(partition.get(i)).equals(new HashSet<>(children))) {
                    node.setName(variableNames.get(i));
                }
            }
        }
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    public java.util.List<Node> getVariables() {
        final List<Node> latents = new ArrayList<>();

        for (final String name : getVariableNames()) {
            final Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    public List<String> getVariableNames() {
        final List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return ClusterUtils.generateLatentNames(partition.size());
    }
}





