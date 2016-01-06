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
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the
 * BuildPureClusters algorithm.
 *
 * @author Ricardo Silva
 */
public class BuildPureClustersRunner extends AbstractMimRunner
        implements GraphSource, KnowledgeBoxInput, Unmarshallable {
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

    public BuildPureClustersRunner(DataWrapper dataWrapper,
                                   BuildPureClustersParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);

    }

    public BuildPureClustersRunner(DataWrapper dataWrapper, SemImWrapper semImWrapper,
                                   BuildPureClustersParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);
        this.semIm = semImWrapper.getSemIm();
        this.trueGraph = semIm.getSemPm().getGraph();
    }

    public BuildPureClustersRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper,
                                   BuildPureClustersParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);
        this.trueGraph = graphWrapper.getGraph();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BuildPureClustersRunner serializableInstance() {
        return new BuildPureClustersRunner(DataWrapper.serializableInstance(),
                BuildPureClustersParams.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        boolean rKey = Preferences.userRoot().getBoolean("BPCrDown", false);

        BpcAlgorithmType algorithm = ((BuildPureClustersIndTestParams) getParams().getMimIndTestParams()).getAlgorithmType();

        Graph searchGraph;

        if (rKey) {
            Washdown washdown;
            Object source = getData();

            if (source instanceof DataSet) {
                washdown = new Washdown((DataSet) source, getParams().getAlpha());
            } else {
                washdown = new Washdown((CovarianceMatrix) source, getParams().getAlpha());
            }

            searchGraph = washdown.search();
        } else {
            TestType tetradTestType = getParams().getTetradTestType();

            if (algorithm == BpcAlgorithmType.TETRAD_PURIFY_WASHDOWN) {
                BpcTetradPurifyWashdown bpc;
                Object source = getData();

                if (source instanceof DataSet) {
                    bpc = new BpcTetradPurifyWashdown(
                            (DataSet) source,
                            tetradTestType,
                            getParams().getAlpha());

                } else {
                    bpc = new BpcTetradPurifyWashdown((ICovarianceMatrix) source,
                            tetradTestType, getParams().getAlpha());

                }

                searchGraph = bpc.search();
            } else if (algorithm == BpcAlgorithmType.BUILD_PURE_CLUSTERS) {
                BuildPureClusters bpc;
                DataModel source = getData();

                TestType testType = getParams().getTetradTestType();
                TestType purifyType = TestType.TETRAD_BASED;

                if (source instanceof ICovarianceMatrix) {
                    bpc = new BuildPureClusters((ICovarianceMatrix) source,
                            getParams().getAlpha(),
                            testType,
                            purifyType);
                } else if (source instanceof DataSet) {
                    bpc = new BuildPureClusters(
                            (DataSet) source, getParams().getAlpha(),
                            testType,
                            purifyType);
                } else {
                    throw new IllegalArgumentException();
                }

                searchGraph = bpc.search();

            }
//            else if (algorithm == BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS) {
////                FindOneFactorClusters bpc;
////                Object source = getData();
////
////                if (source instanceof DataSet) {
////                    bpc = new FindOneFactorClusters(
////                            (DataSet) source,
////                            tetradTestType,
////                            getParams().getAlpha());
////                } else {
////                    bpc = new FindOneFactorClusters((ICovarianceMatrix) source,
////                            tetradTestType, getParams().getAlpha());
////                }
////
////                searchGraph = bpc.search();
//
//                FindOneFactorClusters2 bpc;
//                Object source = getData();
//                FindOneFactorClusters2.Algorithm sag = FindOneFactorClusters2.Algorithm.SAG;
//
//                if (source instanceof DataSet) {
//                    bpc = new FindOneFactorClusters2(
//                            (DataSet) source,
//                            tetradTestType, sag,
//                            getParams().getAlpha());
//
////                    bpc = new FindTwoFactorClusters4(
////                            (DataSet) source,
////                            getParams().getAlpha());
//                } else {
//                    bpc = new FindOneFactorClusters2((ICovarianceMatrix) source,
//                            tetradTestType, sag, getParams().getAlpha());
////
////                    bpc = new FindTwoFactorClusters4((ICovarianceMatrix) source,
////                            getParams().getAlpha());
//                }
//
//                searchGraph = bpc.search();
//
//            }
//            else if (algorithm == BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS) {
//                FindTwoFactorClusters2 bpc;
//                Object source = getData();
//
//                if (source instanceof DataSet) {
//                    bpc = new FindTwoFactorClusters2(
//                            (DataSet) source,
//                            tetradTestType,
//                            getParams().getAlpha());
//
////                    bpc = new FindTwoFactorClusters4(
////                            (DataSet) source,
////                            getParams().getAlpha());
//                } else {
//                    bpc = new FindTwoFactorClusters2((ICovarianceMatrix) source,
//                            tetradTestType, getParams().getAlpha());
////
////                    bpc = new FindTwoFactorClusters4((ICovarianceMatrix) source,
////                            getParams().getAlpha());
//                }
//
//                searchGraph = bpc.search();
//            }
            else {
                throw new IllegalStateException();
            }
        }

        if (semIm != null) {
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);

            List<String> variableNames = ReidentifyVariables.reidentifyVariables2(partition, trueGraph, (DataSet) getData());
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables2(searchGraph, semIm);
        } else if (trueGraph != null) {
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);
            List<String> variableNames = ReidentifyVariables.reidentifyVariables1(partition, trueGraph);
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables(searchGraph, trueGraph);
        }

        System.out.println("Search Graph " + searchGraph);

//        if (searchGraph == null) {
//
//            // The whole lotta hoopla below had to be done so that a message
//            // could be displayed to the user without blocking the thread.
//            // Please don't change this back to a JOptionPane, even if you
//            // really want to. jdramsey 8/15/2005
//            JFrame jFrame = (JFrame) SwingUtilities.getAncestorOfClass(
//                    JFrame.class, JOptionUtils.centeringComp());
//            final JDialog dialog = new JDialog(jFrame);
//
//            JPanel panel = new JPanel();
//            panel.setLayout(new BorderLayout());
//
//            panel.add(new JLabel("No model found."), BorderLayout.CENTER);
//
//            JButton button = new JButton("OK");
//
//            button.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    dialog.setVisible(false);
//                    dialog.dispose();
//                }
//            });
//
//            panel.add(button, BorderLayout.SOUTH);
//            dialog.getContentPane().add(panel, BorderLayout.CENTER);
//
//            dialog.setLocationRelativeTo(JOptionUtils.centeringComp());
//            dialog.pack();
//            dialog.setVisible(true);
//
//            setResultGraph(new EdgeListGraph());
//            setClusters(new Clusters());
//            return;
//        }

        try

        {
            Graph graph = new MarshalledObject<Graph>(searchGraph).get();
            GraphUtils.circleLayout(graph, 200, 200, 150);
            GraphUtils.fruchtermanReingoldLayout(graph);
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
            children.removeAll(ReidentifyVariables.getLatents(searchGraph));

            for (int i = 0; i < partition.size(); i++) {
                if (new HashSet<Node>(partition.get(i)).equals(new HashSet<Node>(children))) {
                    node.setName(variableNames.get(i));
                }
            }
        }
    }

//    // This reidentifies a variable if all of its members belong to one of the clusters
//    // in the original graph.
//    private Graph reidentifyVariables(Graph searchGraph, Graph trueGraph) {
//        if (trueGraph == null) {
//            return searchGraph;
//        }
//
//        Graph reidentifiedGraph = new EdgeListGraph();
////        Graph trueGraph = semIm.getSemPm().getGraph();
//
//        for (Node latent : searchGraph.getNodes()) {
//            if (latent.getNodeType() != NodeType.LATENT) {
//                continue;
//            }
//
//            boolean added = false;
//
//            List<Node> searchChildren = searchGraph.getChildren(latent);
//
//            for (Node _latent : trueGraph.getNodes()) {
//                if (_latent.getNodeType() != NodeType.LATENT) ;
//
//                List<Node> trueChildren = trueGraph.getChildren(_latent);
//
//                for (Node node2 : new ArrayList<Node>(trueChildren)) {
//                    if (node2.getNodeType() == NodeType.LATENT) {
//                        trueChildren.remove(node2);
//                    }
//                }
//
//                boolean containsAll = true;
//
//                for (Node child : searchChildren) {
//                    boolean contains = false;
//
//                    for (Node _child : trueChildren) {
//                        if (child.getName().equals(_child.getName())) {
//                            contains = true;
//                            break;
//                        }
//                    }
//
//                    if (!contains) {
//                        containsAll = false;
//                        break;
//                    }
//                }
//
//                if (containsAll) {
//                    reidentifiedGraph.addNode(_latent);
//
//                    for (Node child : searchChildren) {
//                        if (!reidentifiedGraph.containsNode(child)) {
//                            reidentifiedGraph.addNode(child);
//                        }
//
//                        reidentifiedGraph.addDirectedEdge(_latent, child);
//                    }
//
//                    added = true;
//                    break;
//                }
//            }
//
//            if (!added) {
//                reidentifiedGraph.addNode(latent);
//
//                for (Node child : searchChildren) {
//                    if (!reidentifiedGraph.containsNode(child)) {
//                        reidentifiedGraph.addNode(child);
//                    }
//
//                    reidentifiedGraph.addDirectedEdge(latent, child);
//                }
//            }
//        }
//
//        return reidentifiedGraph;
//    }

//    private Graph reidentifyVariables2(Graph searchGraph, SemIm semIm) {
//        if (semIm == null) {
//            return searchGraph;
//        }
//
//        Graph trueGraph = semIm.getSemPm().getGraph();
//        List<Node> trueLatents = new ArrayList<Node>();
//
//        for (Node node : trueGraph.getNodes()) {
//            if (node.getNodeType() == NodeType.LATENT) {
//                trueLatents.add(node);
//            }
//        }
//
//        List<Node> searchLatents = new ArrayList<Node>();
//
//        for (Node node : searchGraph.getNodes()) {
//            if (!getData().getVariables().contains(node)) {
////                if (node.getNodeType() == NodeType.LATENT) {
//                searchLatents.add(node);
//            }
//        }
//
//        Graph reidentifiedGraph = new EdgeListGraph();
//
//        Map<Node, Node> remap = new HashMap<Node, Node>();
//        Map<Node, Double> score = new HashMap<Node, Double>();
//
//        for (Node searchLatent : searchLatents) {
//            List<Node> searchChildren = searchGraph.getChildren(searchLatent);
//            searchChildren.removeAll(searchLatents);
//            double max = 0.0;
//            Node maxLatent = null;
//
//            for (Node trueLatent : trueLatents) {
//                List<Node> trueChildren = trueGraph.getChildren(trueLatent);
//                trueChildren.removeAll(trueLatents);
//
//                double sum = 0.0;
//
//                for (Node child : searchChildren) {
//                    double beta = getCoefficient(semIm, trueLatent, child);
//                    sum += Math.abs(beta);
//                }
//
//                if (sum > max) {
//                    max = sum;
//                    maxLatent = trueLatent;
//                }
//            }
//
//            remap.put(searchLatent, maxLatent);
//            score.put(searchLatent, max);
//        }
//
//        for (Node _trueLatent : trueLatents) {
//            Node trueLatent = new ContinuousVariable(_trueLatent.getName());
//
//            double max = 0.0;
//            Node maxNode = null;
//            List<Node> searchOthers = new ArrayList<Node>();
//
//            for (Node searchLatent : searchLatents) {
//                if (remap.get(searchLatent) == _trueLatent) {
//                    double _score = score.get(searchLatent);
//
//                    if (_score > max) {
//                        max = _score;
//                        maxNode = searchLatent;
//                        searchOthers.add(searchLatent);
//                    }
//                }
//            }
//
//            if (maxNode == null) continue;
//
//            searchOthers.remove(maxNode);
//
//            reidentifiedGraph.addNode(trueLatent);
//            List<Node> searchChildren = searchGraph.getChildren(maxNode);
//
//            for (Node node : searchChildren) {
//                reidentifiedGraph.addNode(node);
//                reidentifiedGraph.addDirectedEdge(trueLatent, node);
//            }
//
//            for (Node searchLatent : searchOthers) {
//                Node _latent = newLatent(reidentifiedGraph, "_L");
//                reidentifiedGraph.addNode(_latent);
//                List<Node> _searchChildren = searchGraph.getChildren(searchLatent);
//
//                for (Node node : _searchChildren) {
//                    reidentifiedGraph.addNode(node);
//                    reidentifiedGraph.addDirectedEdge(_latent, node);
//                }
//            }
//        }
//
//        return reidentifiedGraph;
//    }

//    private Node newLatent(Graph graph, String name) {
//        INDEX:
//        for (int i = 1; ; i++) {
//            for (Node node : graph.getNodes()) {
//                if (node.getName().equals(name + i)) {
//                    continue INDEX;
//                }
//            }
//
//            GraphNode latent = new GraphNode(name + i);
//            latent.setNodeType(NodeType.LATENT);
//            return latent;
//        }
//    }

//    private double getCoefficient(SemIm semIm, Node trueLatent, Node child) {
//        SemGraph graph = semIm.getSemPm().getGraph();
//
//        Node _node = graph.getNode(child.getName());
//        List<Node> parents = graph.getParents(_node);
//
//        int numLatents = 0;
//
//        for (Node parent : parents) {
//            if (parent.getNodeType() == NodeType.LATENT) {
//                numLatents++;
//            }
//        }
//
//        if (_node == null) {
//            return 0.0;
//        } else if (!graph.isParentOf(trueLatent, _node)) {
//            return 0.0;
//        } else if (numLatents > 1) {
//            return 0.0;
//        } else {
//            return semIm.getEdgeCoef(trueLatent, _node);
//        }
//    }

    public Graph getGraph() {
        return getResultGraph();
    }

    public java.util.List<Node> getVariables() {
        List<Node> latents = new ArrayList<Node>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return MimBuild.generateLatentNames(partition.size());
    }
}





