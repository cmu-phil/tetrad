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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.search.Mimbuild2;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.*;

import static org.junit.Assert.assertEquals;


/**
 * @author Joseph Ramsey
 */
public class TestMimbuild2 {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(49283494L);

        for (int r = 0; r < 1; r++) {
            Graph mim = DataGraphUtils.randomSingleFactorModel(5, 5, 6, 0, 0, 0);

            Graph mimStructure = structure(mim);

            SemImInitializationParams params = new SemImInitializationParams();
            params.setCoefRange(.5, 1.5);

            SemPm pm = new SemPm(mim);
            SemIm im = new SemIm(pm, params);
            DataSet data = im.simulateData(300, false);

            String algorithm = "FOFC";
            Graph searchGraph;
            List<List<Node>> partition;

            if (algorithm.equals("FOFC")) {
                FindOneFactorClusters fofc = new FindOneFactorClusters(data, TestType.TETRAD_WISHART,
                        FindOneFactorClusters.Algorithm.GAP, 0.001);
                searchGraph = fofc.search();
                partition = fofc.getClusters();
            } else if (algorithm.equals("BPC")) {
                TestType testType = TestType.TETRAD_WISHART;
                TestType purifyType = TestType.TETRAD_BASED;

                BuildPureClusters bpc = new BuildPureClusters(
                        data, 0.001,
                        testType,
                        purifyType);
                searchGraph = bpc.search();

                partition = MimUtils.convertToClusters2(searchGraph);
            } else {
                throw new IllegalStateException();
            }

            List<String> latentVarList = reidentifyVariables(mim, data, partition, 2);

//            System.out.println(partition);
//            System.out.println(latentVarList);
//
//            System.out.println("True\n" + mimStructure);

            Graph mimbuildStructure;

            for (int mimbuildMethod : new int[]{2}) {
                if (mimbuildMethod == 1) {
//                    System.out.println("Mimbuild 1\n");
                    Clusters measurements = ClusterUtils.mimClusters(searchGraph);
                    IndTestMimBuild test = new IndTestMimBuild(data, 0.001, measurements);
                    MimBuild mimbuild = new MimBuild(test, new Knowledge2());
                    Graph full = mimbuild.search();
                    full = changeLatentNames(full, measurements, latentVarList);
                    mimbuildStructure = structure(full);
//                    System.out.println("SHD = " + SearchGraphUtils.structuralHammingDistance(mimStructure, mimbuildStructure));
//                    System.out.println("Estimated\n" + mimbuildStructure);
//                    System.out.println();
                }
                else if (mimbuildMethod == 2) {
//                    System.out.println("Mimbuild 2\n");
                    Mimbuild2 mimbuild = new Mimbuild2();
                    mimbuild.setAlpha(0.001);
                    mimbuild.setMinClusterSize(3);
                    mimbuildStructure = mimbuild.search(partition, latentVarList, new CovarianceMatrix(data));
                    ICovarianceMatrix latentcov = mimbuild.getLatentsCov();
//                    System.out.println("\nCovariance over the latents");
//                    System.out.println(latentcov);
//                    System.out.println("Estimated\n" + mimbuildStructure);
                    int shd = SearchGraphUtils.structuralHammingDistance(mimStructure, mimbuildStructure);
//                    System.out.println("SHD = " + shd);

                    assertEquals(7, shd);
//                    System.out.println();
                } else if (mimbuildMethod == 3) {
//                    System.out.println("Mimbuild Trek\n");
                    MimbuildTrek mimbuild = new MimbuildTrek();
                    mimbuild.setAlpha(0.1);
                    mimbuild.setMinClusterSize(3);
                    mimbuildStructure = mimbuild.search(partition, latentVarList, new CovarianceMatrix(data));
//                    ICovarianceMatrix latentcov = mimbuild.getLatentsCov();
//                    System.out.println("\nCovariance over the latents");
//                    System.out.println(latentcov);
//                    System.out.println("Estimated\n" + mimbuildStructure);
                    int shd = SearchGraphUtils.structuralHammingDistance(mimStructure, mimbuildStructure);
//                    System.out.println("SHD = " + shd);
//                    System.out.println();
                    assertEquals(3, shd);
                } else {
                    throw new IllegalStateException();
                }
            }

        }

    }

//    private Graph condense(Graph mimStructure, Graph mimbuildStructure) {
////        System.out.println("Uncondensed: " + mimbuildStructure);
//
//        Map<Node, Node> substitutions = new HashMap<Node, Node>();
//
//        for (Node node : mimbuildStructure.getNodes()) {
//            for (Node _node : mimStructure.getNodes()) {
//                if (node.getName().startsWith(_node.getName())) {
//                    substitutions.put(node, _node);
//                    break;
//                }
//
//                substitutions.put(node, node);
//            }
//        }
//
//        HashSet<Node> nodes = new HashSet<Node>(substitutions.values());
//        Graph graph = new EdgeListGraph(new ArrayList<Node>(nodes));
//
//        for (Edge edge : mimbuildStructure.getEdges()) {
//            Node node1 = substitutions.get(edge.getNode1());
//            Node node2 = substitutions.get(edge.getNode2());
//
//            if (node1 == node2) continue;
//
//            if (graph.isAdjacentTo(node1, node2)) continue;
//
//            graph.addEdge(new Edge(node1, node2, edge.getEndpoint1(), edge.getEndpoint2()));
//        }
//
////        System.out.println("Condensed: " + graph);
//
//        return graph;
//    }

//    public void rtest2() {
//        System.out.println("SHD\tP");
////        System.out.println("MB1\tMB2\tMB3\tMB4\tMB5\tMB6");
//
//        Graph mim = DataGraphUtils.randomSingleFactorModel(5, 5, 10, 0, 0, 0);
//
//        Graph mimStructure = structure(mim);
//
//        SemPm pm = new SemPm(mim);
//        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.5, 1.5);
//
//        NumberFormat nf = new DecimalFormat("0.0000");
//
//        int totalError = 0;
//        int errorCount = 0;
//
//        for (int r = 0; r < 1; r++) {
//            SemIm im = new SemIm(pm, params);
//
//            DataSet data = im.simulateData(300, false);
//
//            mim = GraphUtils.replaceNodes(mim, data.getVariables());
//            List<List<Node>> trueClusters = MimUtils.convertToClusters2(mim);
//
//            CovarianceMatrix _cov = new CovarianceMatrix(data);
//
//            ICovarianceMatrix cov = DataUtils.reorderColumns(_cov);
//
//            String algorithm = "FOFC";
//            Graph searchGraph;
//            List<List<Node>> partition;
//
//            if (algorithm.equals("FOFC")) {
//                FindOneFactorClusters fofc = new FindOneFactorClusters(cov, TestType.TETRAD_WISHART, 0.001);
//                searchGraph = fofc.search();
//                searchGraph = GraphUtils.replaceNodes(searchGraph, data.getVariables());
//                partition = MimUtils.convertToClusters2(searchGraph);
//            } else if (algorithm.equals("BPC")) {
//                TestType testType = TestType.TETRAD_WISHART;
//                TestType purifyType = TestType.TETRAD_BASED;
//
//                BuildPureClusters bpc = new BuildPureClusters(
//                        data, 0.001,
//                        testType,
//                        purifyType);
//                searchGraph = bpc.search();
//
//                partition = MimUtils.convertToClusters2(searchGraph);
//            } else {
//                throw new IllegalStateException();
//            }
//
//            mimStructure = GraphUtils.replaceNodes(mimStructure, data.getVariables());
//
//            List<String> latentVarList = reidentifyVariables(mim, data, partition, 2);
//
//            Graph mimbuildStructure;
//
//            Mimbuild2 mimbuild = new Mimbuild2();
//            mimbuild.setAlpha(0.001);
//            mimbuild.setMinClusterSize(4);
////            mimbuild.setFixOneLoadingPerCluster(true);
//
//            try {
//                mimbuildStructure = mimbuild.search(partition, latentVarList, cov);
//            } catch (Exception e) {
//                e.printStackTrace();
//                continue;
//            }
//
//            mimbuildStructure = GraphUtils.replaceNodes(mimbuildStructure, data.getVariables());
//
//            int shd = SearchGraphUtils.structuralHammingDistance(restrictToEmpiricalLatents(mimStructure, mimbuildStructure), mimbuildStructure);
//            boolean impureCluster = containsImpureCluster(partition, trueClusters);
//            double pValue = mimbuild.getpValue();
////            double pValue = pvalue(mimbuild.getClustering(), _cov);
//            boolean pBelow05 = pValue < 0.05;
//            boolean numClustersNe5 = partition.size() != 5;
//            boolean error = false;
//
////            boolean condition = impureCluster || numClustersNe5 || pBelow05;
////            boolean condition = numClustersNe5 || pBelow05;
////            boolean condition = numClustered(partition) == 40;
//            boolean condition = numClustersNe5;
//
//            if (!condition) {
//                totalError += shd;
//                errorCount++;
//            }
//
//            System.out.print(shd + "\t" + nf.format(pValue) + " "
////                            + (error ? 1 : 0) + " "
////                            + (pBelow05 ? "P < 0.05 " : "")
////                            + (impureCluster ? "Impure cluster " : "")
//                            + (numClustersNe5 ? "# Clusters = " + partition.size() + " " : "")
////                            + clusterSizes(partition, trueClusters)
////                            + numClustered(partition)
//                            + partition
//            );
//
//            System.out.println();
//        }
//
//        System.out.println("\nAvg SHD for not-flagged cases = " + (totalError / (double) errorCount));
//    }

//    private Graph restrictToEmpiricalLatents(Graph mimStructure, Graph mimbuildStructure) {
//        Graph _mim = new EdgeListGraph(mimStructure);
//
//        for (Node node : mimbuildStructure.getNodes()) {
//            if (!mimbuildStructure.containsNode(node)) {
//                _mim.removeNode(node);
//            }
//        }
//
//        return _mim;
//    }

//    private String clusterSizes(List<List<Node>> partition, List<List<Node>> trueClusters) {
//        String s = "";
//
//        FOR:
//        for (int i = 0; i < partition.size(); i++) {
//            List<Node> cluster = partition.get(i);
//            s += cluster.size();
//
//            for (List<Node> trueCluster : trueClusters) {
//                if (trueCluster.containsAll(cluster)) {
////                    Collections.sort(trueCluster);
////                    Collections.sort(cluster);
////                    System.out.println(trueCluster + " " + cluster);
//                    s += "p";
//
//                    if (i < partition.size() - 1) {
//                        s += ",";
//                    }
//
//                    continue FOR;
//                }
//            }
//
//            if (i < partition.size() - 1) {
//                s += ",";
//            }
//        }
//
//        return s;
//    }

//    private int numClustered(List<List<Node>> partition) {
//        int sum = 0;
//
//        for (int i = 0; i < partition.size(); i++) {
//            List<Node> cluster = partition.get(i);
//            sum += cluster.size();
//        }
//
//        return sum;
//    }


//    private boolean containsImpureCluster(List<List<Node>> partition, List<List<Node>> trueClusters) {
//
//        FOR:
//        for (int i = 0; i < partition.size(); i++) {
//            List<Node> cluster = partition.get(i);
//
//            for (List<Node> trueCluster : trueClusters) {
//                if (trueCluster.containsAll(cluster)) {
//                    continue FOR;
//                }
//            }
//
//            return true;
//        }
//
//        return false;
//    }


//    public void rtest3() {
//        Node x = new GraphNode("X");
//        Node y = new GraphNode("Y");
//        Node z = new GraphNode("Z");
//        Node w = new GraphNode("W");
//
//        List<Node> nodes = new ArrayList<Node>();
//        nodes.add(x);
//        nodes.add(y);
//        nodes.add(z);
//        nodes.add(w);
//
//        Graph g = new EdgeListGraph(nodes);
//        g.addDirectedEdge(x, y);
//        g.addDirectedEdge(x, z);
//        g.addDirectedEdge(y, w);
//        g.addDirectedEdge(z, w);
//
//        Graph maxGraph = null;
//        double maxPValue = -1.0;
//        ICovarianceMatrix maxLatentCov = null;
//
//        Graph mim = DataGraphUtils.randomMim(g, 8, 0, 0, 0, true);
////        Graph mim = DataGraphUtils.randomSingleFactorModel(5, 5, 8, 0, 0, 0);
//        Graph mimStructure = structure(mim);
//        SemPm pm = new SemPm(mim);
//
//        System.out.println("\n\nTrue graph:");
//        System.out.println(mimStructure);
//
//        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.5, 1.5);
//
//        SemIm im = new SemIm(pm, params);
//
//        int N = 1000;
//
//        DataSet data = im.simulateData(N, false);
//
//        CovarianceMatrix cov = new CovarianceMatrix(data);
//
//        for (int i = 0; i < 1; i++) {
//
//            ICovarianceMatrix _cov = DataUtils.reorderColumns(cov);
//            List<List<Node>> partition;
//
//            FindOneFactorClusters fofc = new FindOneFactorClusters(_cov, TestType.TETRAD_WISHART, .001);
//            fofc.search();
//            partition = fofc.getClusters();
//            System.out.println(partition);
//
//            List<String> latentVarList = reidentifyVariables(mim, data, partition, 2);
//
//            Mimbuild2 mimbuild = new Mimbuild2();
//
//            mimbuild.setAlpha(0.001);
////            mimbuild.setMinimumSize(5);
//
//            // To test knowledge.
////            Knowledge knowledge = new Knowledge2();
////            knowledge.setEdgeForbidden("L.Y", "L.W", true);
////            knowledge.setEdgeRequired("L.Y", "L.Z", true);
////            mimbuild.setKnowledge(knowledge);
//
//            Graph mimbuildStructure = mimbuild.search(partition, latentVarList, _cov);
//
//            double pValue = mimbuild.getpValue();
//            System.out.println(mimbuildStructure);
//            System.out.println("P = " + pValue);
//            System.out.println("Latent Cov = " + mimbuild.getLatentsCov());
//
//            if (pValue > maxPValue) {
//                maxPValue = pValue;
//                maxGraph = new EdgeListGraph(mimbuildStructure);
//                maxLatentCov = mimbuild.getLatentsCov();
//            }
//        }
//
//        System.out.println("\n\nTrue graph:");
//        System.out.println(mimStructure);
//        System.out.println("\nBest graph:");
//        System.out.println(maxGraph);
//        System.out.println("P = " + maxPValue);
//        System.out.println("Latent Cov = " + maxLatentCov);
//        System.out.println();
//    }

//    public void rtest4() {
//        System.out.println("SHD\tP");
////        System.out.println("MB1\tMB2\tMB3\tMB4\tMB5\tMB6");
//
//        Graph mim = DataGraphUtils.randomSingleFactorModel(5, 5, 8, 0, 0, 0);
//
//        Graph mimStructure = structure(mim);
//
//        SemPm pm = new SemPm(mim);
//        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.5, 1.5);
//
//        NumberFormat nf = new DecimalFormat("0.0000");
//
//        int totalError = 0;
//        int errorCount = 0;
//
//        int maxScore = 0;
//        int maxNumMeasures = 0;
//        double maxP = 0.0;
//
//        for (int r = 0; r < 1; r++) {
//            SemIm im = new SemIm(pm, params);
//
//            DataSet data = im.simulateData(1000, false);
//
//            mim = GraphUtils.replaceNodes(mim, data.getVariables());
//            List<List<Node>> trueClusters = MimUtils.convertToClusters2(mim);
//
//            CovarianceMatrix _cov = new CovarianceMatrix(data);
//
//            ICovarianceMatrix cov = DataUtils.reorderColumns(_cov);
//
//            String algorithm = "FOFC";
//            Graph searchGraph;
//            List<List<Node>> partition;
//
//            if (algorithm.equals("FOFC")) {
//                FindOneFactorClusters fofc = new FindOneFactorClusters(cov, TestType.TETRAD_WISHART, 0.001f);
//                searchGraph = fofc.search();
//                searchGraph = GraphUtils.replaceNodes(searchGraph, data.getVariables());
//                partition = MimUtils.convertToClusters2(searchGraph);
//            } else if (algorithm.equals("BPC")) {
//                TestType testType = TestType.TETRAD_WISHART;
//                TestType purifyType = TestType.TETRAD_BASED;
//
//                BuildPureClusters bpc = new BuildPureClusters(
//                        data, 0.001,
//                        testType,
//                        purifyType);
//                searchGraph = bpc.search();
//
//                partition = MimUtils.convertToClusters2(searchGraph);
//            } else {
//                throw new IllegalStateException();
//            }
//
//            mimStructure = GraphUtils.replaceNodes(mimStructure, data.getVariables());
//
//            List<String> latentVarList = reidentifyVariables(mim, data, partition, 2);
//
//            Graph mimbuildStructure;
//
//            Mimbuild2 mimbuild = new Mimbuild2();
//            mimbuild.setAlpha(0.001);
//            mimbuild.setMinClusterSize(3);
//
//            try {
//                mimbuildStructure = mimbuild.search(partition, latentVarList, cov);
//            } catch (Exception e) {
//                e.printStackTrace();
//                continue;
//            }
//
//            mimbuildStructure = GraphUtils.replaceNodes(mimbuildStructure, data.getVariables());
//            mimbuildStructure = condense(mimStructure, mimbuildStructure);
//
//            int shd = SearchGraphUtils.structuralHammingDistance(mimStructure, mimbuildStructure);
//            boolean impureCluster = containsImpureCluster(partition, trueClusters);
//            double pValue = mimbuild.getpValue();
//            boolean pBelow05 = pValue < 0.05;
//            boolean numClustersGreaterThan5 = partition.size() != 5;
//            boolean error = false;
//
////            boolean condition = impureCluster || numClustersGreaterThan5 || pBelow05;
////            boolean condition = numClustersGreaterThan5 || pBelow05;
//            boolean condition = numClustered(partition) == 40;
//
//            if (!condition && (shd > 5)) {
//                error = true;
//            }
//
//            if (!condition) {
//                totalError += shd;
//                errorCount++;
//
//            }
//
//            if (pValue > maxP) {
//                maxScore = shd;
//                maxP = mimbuild.getpValue();
//                maxNumMeasures = numClustered(partition);
//                System.out.println("maxNumMeasures = " + maxNumMeasures);
//                System.out.println("maxScore = " + maxScore);
//                System.out.println("maxP = " + maxP);
//                System.out.println("clusters = " + clusterSizes(partition, trueClusters));
//            }
//
//            System.out.print(shd + "\t" + nf.format(pValue) + " "
//                            + numClustered(partition)
//            );
//
//            System.out.println();
//        }
//
//        System.out.println("\nAvg SHD for not-flagged cases = " + (totalError / (double) errorCount));
//
//        System.out.println("maxNumMeasures = " + maxNumMeasures);
//        System.out.println("maxScore = " + maxScore);
//        System.out.println("maxP = " + maxP);
//    }


    private Graph changeLatentNames(Graph full, Clusters measurements, List<String> latentVarList) {
        Graph g2 = null;

        try {
            g2 = (Graph) new MarshalledObject(full).get();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < measurements.getNumClusters(); i++) {
            List<String> d = measurements.getCluster(i);
            String latentName = latentVarList.get(i);

            for (Node node : full.getNodes()) {
                if (!(node.getNodeType() == NodeType.LATENT)) {
                    continue;
                }

                List<Node> _children = full.getChildren(node);

                _children.removeAll(ReidentifyVariables.getLatents(full));

                List<String> childNames = getNames(_children);

                if (new HashSet<String>(childNames).equals(new HashSet<String>(d))) {
                    g2.getNode(node.getName()).setName(latentName);
                }
            }
        }

        return g2;
    }

    private List<String> getNames(List<Node> nodes) {
        List<String> names = new ArrayList<String>();
        for (Node node : nodes) {
            names.add(node.getName());
        }
        return names;
    }


    private List<String> reidentifyVariables(Graph mim, DataSet data, List<List<Node>> partition, int method) {
        List<String> latentVarList = null;

        if (method == 1) {
            latentVarList = ReidentifyVariables.reidentifyVariables1(partition, mim);
        } else if (method == 2) {
            latentVarList = ReidentifyVariables.reidentifyVariables2(partition, mim, data);
        } else {
            throw new IllegalStateException();
        }

        return latentVarList;
    }

    private Graph structure(Graph mim) {
        List<Node> latents = new ArrayList<Node>();

        for (Node node : mim.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        return mim.subgraph(latents);
    }
}


