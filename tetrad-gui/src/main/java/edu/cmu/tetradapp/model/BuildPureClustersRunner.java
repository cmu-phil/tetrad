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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Bpc;
import edu.cmu.tetrad.search.utils.BpcAlgorithmType;
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.search.utils.MimUtils;
import edu.cmu.tetrad.search.work_in_progress.BpcTetradPurifyWashdown;
import edu.cmu.tetrad.search.work_in_progress.Washdown;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;

import java.io.Serial;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the BuildPureClusters algorithm.
 *
 * @author Ricardo Silva
 * @version $Id: $Id
 */
public class BuildPureClustersRunner extends AbstractMimRunner
        implements GraphSource, Unmarshallable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * To reidentify variables.
     */
    private SemIm semIm;

    /**
     * The true graph.
     */
    private Graph trueGraph;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper.
     *
     * @param dataWrapper        a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param pureClustersParams a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BuildPureClustersRunner(DataWrapper dataWrapper,
                                   Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);

    }

    /**
     * <p>Constructor for BuildPureClustersRunner.</p>
     *
     * @param dataWrapper        a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param semImWrapper       a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param pureClustersParams a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BuildPureClustersRunner(DataWrapper dataWrapper, SemImWrapper semImWrapper,
                                   Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);
        this.semIm = semImWrapper.getSemIm();
        this.trueGraph = this.semIm.getSemPm().getGraph();
    }

    /**
     * <p>Constructor for BuildPureClustersRunner.</p>
     *
     * @param dataWrapper        a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphWrapper       a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param pureClustersParams a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BuildPureClustersRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper,
                                   Parameters pureClustersParams) {
        super(dataWrapper, (Clusters) pureClustersParams.get("clusters", null), pureClustersParams);
        this.trueGraph = graphWrapper.getGraph();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */
    public void execute() {
        boolean rKey = getParams().getBoolean("BPCrDown", false);

        BpcAlgorithmType algorithm = (BpcAlgorithmType) getParams().get("bpcAlgorithmthmType", BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS);

        Graph searchGraph;

        if (rKey) {
            Washdown washdown;
            Object source = getData();

            if (source instanceof DataSet) {
                washdown = new Washdown((DataSet) source, getParams().getDouble("alpha", 0.001));
            } else {
                washdown = new Washdown((CovarianceMatrix) source, getParams().getDouble("alpha", 0.001));
            }

            searchGraph = washdown.search();
        } else {
            BpcTestType tetradTestType = (BpcTestType) getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART);

            if (algorithm == BpcAlgorithmType.TETRAD_PURIFY_WASHDOWN) {
                BpcTetradPurifyWashdown bpc;
                Object source = getData();

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
                Bpc bpc;
                DataModel source = getData();

                BpcTestType testType = (BpcTestType) getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART);

                if (source instanceof ICovarianceMatrix) {
                    bpc = new Bpc((ICovarianceMatrix) source,
                            getParams().getDouble("alpha", 0.001),
                            testType
                    );
                } else if (source instanceof DataSet) {
                    bpc = new Bpc(
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
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);

            List<String> variableNames = ReidentifyVariables.reidentifyVariables2(partition, this.trueGraph, (DataSet) getData());
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables2(searchGraph, semIm);
        } else if (this.trueGraph != null) {
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);
            List<String> variableNames = ReidentifyVariables.reidentifyVariables1(partition, this.trueGraph);
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables(searchGraph, trueGraph);
        }

        System.out.println("Search Graph " + searchGraph);

        try {
            Graph graph = new MarshalledObject<>(searchGraph).get();
            LayoutUtil.defaultLayout(graph);
            LayoutUtil.fruchtermanReingoldLayout(graph);
            setResultGraph(graph);
            setClusters(MimUtils.convertToClusters(graph, getData().getVariables()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void rename(Graph searchGraph, List<List<Node>> partition, List<String> variableNames) {
        for (Node node : searchGraph.getNodes()) {
            if (!(node.getNodeType() == NodeType.LATENT)) {
                continue;
            }

            List<Node> children = searchGraph.getChildren(node);
            ReidentifyVariables.getLatents(searchGraph).forEach(children::remove);

            for (int i = 0; i < partition.size(); i++) {
                if (new HashSet<>(partition.get(i)).equals(new HashSet<>(children))) {
                    node.setName(variableNames.get(i));
                }
            }
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public java.util.List<Node> getVariables() {
        List<Node> latents = new ArrayList<>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return ClusterUtils.generateLatentNames(partition.size());
    }
}





