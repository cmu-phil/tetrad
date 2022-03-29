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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;

import java.util.*;

/**
 * Created by IntelliJ IDEA. User: josephramsey Date: May 17, 2010 Time: 6:17:53 PM To change this template use File |
 * Settings | File Templates.
 */
public class PurifyScoreBased implements IPurify {
    private final boolean outputMessage = true;
    private final TetradTest tetradTest;
    private final int numVars;
    private List forbiddenList;

//     SCORE-BASED PURIFY </p> - using BIC score function and Structural EM for
//     search. Probabilistic model is Gaussian. - search operator consists only
//     of adding a bi-directed edge between pairs of error variables - after
//     such pairs are found, an heuristic is applied to eliminate one member of
//     each pair - this methods tends to be much slower than the "tetradBased"
//     ones.

    double[][] Cyy, Cyz, Czz, bestCyy, bestCyz, bestCzz;
    double[][] covErrors, oldCovErrors, sampleCovErrors, betas, oldBetas;
    double[][] betasLat;
    double[] varErrorLatent;
    double[][] omega;
    double[] omegaI;
    double[][][] parentsResidualsCovar;
    double[] iResidualsCovar;
    double[][][] selectedInverseOmega;
    double[][][] auxInverseOmega;
    int[][] spouses;
    int[] nSpouses;
    int[][] parents;
    int[][] parentsLat;
    double[][][] parentsCov;
    double[][] parentsChildCov;
    double[][][] parentsLatCov;
    double[][] parentsChildLatCov;
    double[][][] pseudoParentsCov;
    double[][] pseudoParentsChildCov;
    boolean[][] parentsL;

    int numObserved;
    int numLatent;
    int[] clusterId;
    Hashtable observableNames, latentNames;
    SemGraph purePartitionGraph;
    Graph basicGraph;
    ICovarianceMatrix covarianceMatrix;
    boolean[][] correlatedErrors, latentParent, observedParent;
    List latentNodes, measuredNodes;
    SemIm currentSemIm;
    boolean modifiedGraph;


    boolean extraDebugPrint;

    public PurifyScoreBased(final TetradTest tetradTest) {
        this.tetradTest = tetradTest;
        this.numVars = tetradTest.getVarNames().length;
    }

    public List<List<Node>> purify(final List<List<Node>> partition) {
        System.out.println("*** " + partition);
        final List<int[]> _partition = convertListToInt(partition);

        printIntPartition(_partition);

        final SemGraph graph = scoreBasedPurify(_partition);
        final Graph _graph = convertSearchGraph(graph);

        final Clusters clusters = MimUtils.convertToClusters(_graph);

        final List<int[]> _partition1 = new ArrayList<>();
        final List<Node> nodes = this.tetradTest.getVariables();

        for (int i = 0; i < clusters.getNumClusters(); i++) {
            final List<String> cluster = clusters.getCluster(i);
            final int[] _cluster = new int[cluster.size()];
            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    final Node node = nodes.get(k);
                    if (node.getName().equals(cluster.get(j))) {
                        _cluster[j] = k;
                        break;
                    }
                }
            }
            _partition1.add(_cluster);
        }

        final List<int[]> _partition2 = _partition1;

        printClustering(_partition2);

        return convertIntToList(_partition2);
    }

    public void setTrueGraph(final Graph mim) {
        throw new UnsupportedOperationException();
    }

    private void printIntPartition(final List<int[]> partition) {
        for (int i = 0; i < partition.size(); i++) {
            final int[] cluster = partition.get(i);
            System.out.print(i + ": ");
            for (int j = 0; j < cluster.length; j++) {
                System.out.print(cluster[j] + " ");
            }

            System.out.println();
        }

        System.out.println();
    }

    private List<int[]> convertListToInt(final List<List<Node>> partition) {
        final List<Node> nodes = this.tetradTest.getVariables();
        final List<int[]> _partition = new ArrayList<>();

        for (int i = 0; i < partition.size(); i++) {
            final List<Node> cluster = partition.get(i);
            final int[] _cluster = new int[cluster.size()];

            for (int j = 0; j < cluster.size(); j++) {
                for (int k = 0; k < nodes.size(); k++) {
                    if (nodes.get(k).getName().equals(cluster.get(j).getName())) {
                        _cluster[j] = k;
                    }
                }
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    private List<List<Node>> convertIntToList(final List<int[]> partition) {
        final List<Node> nodes = this.tetradTest.getVariables();
        final List<List<Node>> _partition = new ArrayList<>();

        for (int i = 0; i < partition.size(); i++) {
            final int[] cluster = partition.get(i);
            final List<Node> _cluster = new ArrayList<>();

            for (int j = 0; j < cluster.length; j++) {
                _cluster.add(nodes.get(cluster[j]));
            }

            _partition.add(_cluster);
        }

        return _partition;
    }

    private Graph convertSearchGraph(final SemGraph input) {
        if (input == null) {
            final List nodes = new ArrayList();
            nodes.add(new GraphNode("No_model."));
            return new EdgeListGraph(nodes);
        }
        final List inputIndicators = new ArrayList();
        final List inputLatents = new ArrayList();
        final Iterator it = input.getNodes().iterator();
        while (it.hasNext()) {
            final Node next = (Node) it.next();
            if (next.getNodeType() == NodeType.MEASURED) {
                inputIndicators.add(next);
            } else if (next.getNodeType() == NodeType.LATENT) {
                inputLatents.add(next);
            }

        }
        final List allNodes = new ArrayList(inputIndicators);
        allNodes.addAll(inputLatents);
        final Graph output = new EdgeListGraph(allNodes);

        final Iterator nit1 = input.getNodes().iterator();
        while (nit1.hasNext()) {
            final Node node1 = (Node) nit1.next();
            final Iterator nit2 = input.getNodes().iterator();
            while (nit2.hasNext()) {
                final Node node2 = (Node) nit2.next();
                final Edge edge = input.getEdge(node1, node2);
                if (edge != null) {
                    if (node1.getNodeType() == NodeType.ERROR &&
                            node2.getNodeType() == NodeType.ERROR) {
                        Iterator ci = input.getChildren(node1).iterator();
                        final Node indicator1 =
                                (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...
                        ci = input.getChildren(node2).iterator();
                        final Node indicator2 =
                                (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...
                        if (indicator1.getNodeType() != NodeType.LATENT) {
                            output.setEndpoint(indicator1, indicator2,
                                    Endpoint.ARROW);
                            output.setEndpoint(indicator2, indicator1,
                                    Endpoint.ARROW);
                        }
                    } else if ((node1.getNodeType() != NodeType.LATENT ||
                            node2.getNodeType() != NodeType.LATENT) &&
                            node1.getNodeType() != NodeType.ERROR &&
                            node2.getNodeType() != NodeType.ERROR) {
                        output.setEndpoint(edge.getNode1(), edge.getNode2(),
                                Endpoint.ARROW);
                        output.setEndpoint(edge.getNode2(), edge.getNode1(),
                                Endpoint.TAIL);
                    }
                }
            }
        }

        for (int i = 0; i < inputLatents.size() - 1; i++) {
            for (int j = i + 1; j < inputLatents.size(); j++) {
                output.setEndpoint((Node) inputLatents.get(i),
                        (Node) inputLatents.get(j), Endpoint.TAIL);
                output.setEndpoint((Node) inputLatents.get(j),
                        (Node) inputLatents.get(i), Endpoint.TAIL);
            }
        }

        return output;
    }

    private SemGraph scoreBasedPurify(final List partition) {
        structuralEmInitialization(partition);
        SemGraph bestGraph = this.purePartitionGraph;
        System.out.println(">>>> Structural EM: initial round");
        //gaussianEM(bestGraph, null);
        for (int i = 0; i < this.correlatedErrors.length; i++) {
            for (int j = 0; j < this.correlatedErrors.length; j++) {
                this.correlatedErrors[i][j] = false;
            }
        }
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numLatent; j++) {
                final Node latentNode = this.purePartitionGraph.getNode(
                        this.latentNodes.get(j).toString());
                final Node measuredNode = this.purePartitionGraph.getNode(
                        this.measuredNodes.get(i).toString());
                this.latentParent[i][j] =
                        this.purePartitionGraph.isParentOf(latentNode, measuredNode);
            }
            for (int j = i; j < this.numObserved; j++) {
                this.observedParent[i][j] = this.observedParent[j][i] = false;
            }
        }

        do {
            this.modifiedGraph = false;
            final double score = gaussianEM(bestGraph, null);
            printlnMessage("Initial score" + score);
            impurityScoreSearch(score);
            if (this.modifiedGraph) {
                printlnMessage(">>>> Structural EM: starting a new round");
                bestGraph = updatedGraph();
                //SemIm nextSemIm = getNextSemIm(bestGraph);
                //gaussianEM(bestGraph, nextSemIm);
            }
        } while (this.modifiedGraph);
        final boolean[][] impurities = new boolean[this.numObserved][this.numObserved];
        for (int i = 0; i < this.numObserved; i++) {
            final List parents = bestGraph.getParents(
                    bestGraph.getNode(this.measuredNodes.get(i).toString()));
            if (parents.size() > 1) {
                boolean latent_found = false;
                for (final Iterator it = parents.iterator(); it.hasNext(); ) {
                    final Node parent = (Node) it.next();
                    if (parent.getNodeType() == NodeType.LATENT) {
                        if (latent_found) {
                            impurities[i][i] = true;
                            break;
                        } else {
                            latent_found = true;
                        }
                    }
                }
            } else {
                impurities[i][i] = false;
            }
            for (int j = i + 1; j < this.numObserved; j++) {
                impurities[i][j] = this.correlatedErrors[i][j] ||
                        this.observedParent[i][j] || this.observedParent[j][i];
                impurities[j][i] = impurities[i][j];
            }
        }
        if (((ContinuousTetradTest) this.tetradTest).getTestType() ==
                TestType.GAUSSIAN_SCORE) {
            bestGraph = removeMarkedImpurities(bestGraph, impurities);
        }
        return bestGraph;
    }

    private void structuralEmInitialization(final List partition) {
        // Initialize semGraph
        this.observableNames = new Hashtable();
        this.latentNames = new Hashtable();
        this.numObserved = 0;
        this.numLatent = 0;
        this.latentNodes = new ArrayList();
        this.measuredNodes = new ArrayList();
        this.basicGraph = new EdgeListGraph();
        for (int p = 0; p < partition.size(); p++) {
            final int[] next = (int[]) partition.get(p);
            final Node newLatent = new GraphNode("_L" + p);
            newLatent.setNodeType(NodeType.LATENT);
            this.basicGraph.addNode(newLatent);
            final Iterator it = this.latentNodes.iterator();
            while (it.hasNext()) {
                final Node previousLatent = (Node) it.next();
                this.basicGraph.addDirectedEdge(previousLatent, newLatent);
            }
            this.latentNodes.add(newLatent);
            this.latentNames.put(newLatent.toString(), this.numLatent);
            this.numLatent++;
            for (int i = 0; i < next.length; i++) {
                final Node newNode = new GraphNode(this.tetradTest.getVarNames()[next[i]]);
                this.basicGraph.addNode(newNode);
                this.basicGraph.addDirectedEdge(newLatent, newNode);
                this.observableNames.put(newNode.toString(), this.numObserved);
                this.measuredNodes.add(newNode);
                this.numObserved++;
            }
        }

        if (this.numLatent + this.numObserved < 1) {
            throw new IllegalArgumentException(
                    "Input clusters must contain at least one variable.");
        }

        this.clusterId = new int[this.numObserved];
        int count = 0;
        for (int p = 0; p < partition.size(); p++) {
            final int[] next = (int[]) partition.get(p);
            for (int i = 0; i < next.length; i++) {
                this.clusterId[count++] = p;
            }
        }
        this.purePartitionGraph = new SemGraph(this.basicGraph);
        this.purePartitionGraph.setShowErrorTerms(true);

        if (((ContinuousTetradTest) this.tetradTest).getTestType() ==
                TestType.NONE) {
            return;
        }

        //Information for graph modification
        this.correlatedErrors = new boolean[this.numObserved][this.numObserved];
        this.latentParent = new boolean[this.numObserved][this.numLatent];
        this.observedParent = new boolean[this.numObserved][this.numObserved];

        //Information for MAG expectation
        this.Cyy = new double[this.numObserved][this.numObserved];
        this.bestCyy = new double[this.numObserved][this.numObserved];
        this.bestCyz = new double[this.numObserved][this.numLatent];
        this.bestCzz = new double[this.numLatent][this.numLatent];
        this.covarianceMatrix =
                this.tetradTest.getCovMatrix();
        final String[] varNames =
                this.covarianceMatrix.getVariableNames().toArray(new String[0]);
        final double[][] cov = this.covarianceMatrix.getMatrix().toArray();
        for (int i = 0; i < cov.length; i++) {
            for (int j = 0; j < cov.length; j++) {
                if (this.observableNames.get(varNames[i]) != null &&
                        this.observableNames.get(varNames[j]) != null) {
                    this.Cyy[((Integer) this.observableNames.get(
                            varNames[i]))][((Integer) this.observableNames
                            .get(varNames[j]))] = cov[i][j];
                }
            }
        }

        //Information for MAG maximization
        this.parents = new int[this.numObserved][];
        this.spouses = new int[this.numObserved][];
        this.nSpouses = new int[this.numObserved];
        this.parentsLat = new int[this.numLatent][];
        this.parentsL = new boolean[this.numObserved][];
        this.parentsCov = new double[this.numObserved][][];
        this.parentsChildCov = new double[this.numObserved][];
        this.parentsLatCov = new double[this.numLatent][][];
        this.parentsChildLatCov = new double[this.numLatent][];
        this.pseudoParentsCov = new double[this.numObserved][][];
        this.pseudoParentsChildCov = new double[this.numObserved][];
        this.covErrors = new double[this.numObserved][this.numObserved];
        this.oldCovErrors = new double[this.numObserved][this.numObserved];
        this.sampleCovErrors = new double[this.numObserved][this.numObserved];
        this.varErrorLatent = new double[this.numLatent];
        this.omega = new double[this.numLatent + this.numObserved - 1][
                this.numLatent + this.numObserved - 1];
        this.omegaI = new double[this.numLatent + this.numObserved - 1];
        this.selectedInverseOmega = new double[this.numObserved][][];
        this.auxInverseOmega = new double[this.numObserved][][];
        this.parentsResidualsCovar = new double[this.numObserved][][];
        this.iResidualsCovar =
                new double[this.numObserved + this.numLatent - 1];
        this.betas =
                new double[this.numObserved][this.numObserved + this.numLatent];
        this.oldBetas =
                new double[this.numObserved][this.numObserved + this.numLatent];
        this.betasLat = new double[this.numLatent][this.numLatent];
    }

    private void printMessage(final String message) {
        if (this.outputMessage) {
            System.out.print(message);
        }
    }

    private void printlnMessage(final String message) {
        if (this.outputMessage) {
            System.out.println(message);
        }
    }

    private void printlnMessage() {
        if (this.outputMessage) {
            System.out.println();
        }
    }

    private void printClustering(final List clustering) {
        final Iterator it = clustering.iterator();
        while (it.hasNext()) {
            final int[] c = (int[]) it.next();
            printCluster(c);
        }
    }

    private void printCluster(final int[] c) {
        final String[] sorted = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            sorted[i] = this.tetradTest.getVarNames()[c[i]];
        }
        for (int i = 0; i < sorted.length - 1; i++) {
            String min = sorted[i];
            int min_idx = i;
            for (int j = i + 1; j < sorted.length; j++) {
                if (sorted[j].compareTo(min) < 0) {
                    min = sorted[j];
                    min_idx = j;
                }
            }
            final String temp = sorted[i];
            sorted[i] = min;
            sorted[min_idx] = temp;
        }
        for (int i = 0; i < sorted.length; i++) {
            printMessage(sorted[i] + " ");
        }
        printlnMessage();
    }

    private double gaussianEM(final SemGraph semdag, final SemIm initialSemIm) {
        double score, newScore = -Double.MAX_VALUE, bestScore =
                -Double.MAX_VALUE;
        final SemPm semPm = new SemPm(semdag);
        semdag.setShowErrorTerms(true);
        for (int p = 0; p < this.numObserved; p++) {
            for (int q = 0; q < this.numObserved; q++) {
                this.bestCyy[p][q] = this.Cyy[p][q];
            }
            if (this.Cyz != null) {
                for (int q = 0; q < this.numLatent; q++) {
                    this.bestCyz[p][q] = this.Cyz[p][q];
                }
            }
        }
        if (this.Czz != null) {
            for (int p = 0; p < this.numLatent; p++) {
                for (int q = 0; q < this.numLatent; q++) {
                    this.bestCzz[p][q] = this.Czz[p][q];
                }
            }
        }


        initializeGaussianEM(semdag);

        for (int i = 0; i < 3; i++) {
            System.out.println("--Trial " + i);
            final SemIm semIm;
            if (i == 0 && initialSemIm != null) {
                semIm = initialSemIm;
            } else {
                semIm = new SemIm(semPm);
                semIm.setCovMatrix(this.covarianceMatrix);
            }
            do {
                score = newScore;
                gaussianExpectation(semIm);
                newScore = gaussianMaximization(semIm);
                if (newScore == -Double.MAX_VALUE) {
                    break;
                }
            } while (Math.abs(score - newScore) > 1.E-3);
            System.out.println(newScore);
            if (newScore > bestScore && !Double.isInfinite(newScore)) {
                bestScore = newScore;
                for (int p = 0; p < this.numObserved; p++) {
                    for (int q = 0; q < this.numObserved; q++) {
                        this.bestCyy[p][q] = this.Cyy[p][q];
                    }
                    for (int q = 0; q < this.numLatent; q++) {
                        this.bestCyz[p][q] = this.Cyz[p][q];
                    }
                }
                for (int p = 0; p < this.numLatent; p++) {
                    for (int q = 0; q < this.numLatent; q++) {
                        this.bestCzz[p][q] = this.Czz[p][q];
                    }
                }
            }
        }
        for (int p = 0; p < this.numObserved; p++) {
            for (int q = 0; q < this.numObserved; q++) {
                this.Cyy[p][q] = this.bestCyy[p][q];
            }
            for (int q = 0; q < this.numLatent; q++) {
                this.Cyz[p][q] = this.bestCyz[p][q];
            }
        }
        for (int p = 0; p < this.numLatent; p++) {
            for (int q = 0; q < this.numLatent; q++) {
                this.Czz[p][q] = this.bestCzz[p][q];
            }
        }
        if (Double.isInfinite(bestScore)) {
            System.out.println("* * Warning: Heywood case in this step");
            return -Double.MAX_VALUE;
        }
        //System.exit(0);
        return bestScore;
    }

    private void initializeGaussianEM(final SemGraph semMag) {
        //Build parents and spouses indices
        for (int i = 0; i < this.numLatent; i++) {
            final Node node = (Node) this.latentNodes.get(i);
            if (semMag.getParents(node).size() > 0) {
                this.parentsLat[i] =
                        new int[semMag.getParents(node).size() - 1];
                int count = 0;
                for (final Iterator it =
                     semMag.getParents(node).iterator(); it.hasNext(); ) {
                    final Node parent = (Node) it.next();
                    if (parent.getNodeType() == NodeType.LATENT) {
                        this.parentsLat[i][count++] =
                                ((Integer) this.latentNames.get(
                                        parent.getName()));
                    }
                }
                this.parentsLatCov[i] =
                        new double[this.parentsLat[i].length][this.parentsLat[i].length];
                this.parentsChildLatCov[i] =
                        new double[this.parentsLat[i].length];
            }
        }

        final boolean[][] correlatedErrors =
                new boolean[this.numObserved][this.numObserved];
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numObserved; j++) {
                correlatedErrors[i][j] = false;
            }
        }
        for (final Iterator it = semMag.getEdges().iterator(); it.hasNext(); ) {
            final Edge nextEdge = (Edge) it.next();
            if (nextEdge.getEndpoint1() == Endpoint.ARROW &&
                    nextEdge.getEndpoint2() == Endpoint.ARROW) {
                //By construction, getNode1() and getNode2() are error nodes. They have only one child each.
                final Iterator it1 = semMag.getChildren(nextEdge.getNode1())
                        .iterator();
                final Node measure1 = (Node) it1.next();
                final Iterator it2 = semMag.getChildren(nextEdge.getNode2())
                        .iterator();
                final Node measure2 = (Node) it2.next();
                correlatedErrors[((Integer) this.observableNames.get(
                        measure1.getName()))][((Integer) this.observableNames
                        .get(measure2.getName()))] = true;
                correlatedErrors[((Integer) this.observableNames.get(
                        measure2.getName()))][((Integer) this.observableNames.get(measure1.getName()))] = true;
            }
        }

        for (int i = 0; i < this.numObserved; i++) {
            final Node node = (Node) this.measuredNodes.get(i);
            this.parents[i] = new int[semMag.getParents(node).size() - 1];
            this.parentsL[i] = new boolean[semMag.getParents(node).size() - 1];
            int count = 0;
            for (final Iterator it =
                 semMag.getParents(node).iterator(); it.hasNext(); ) {
                final Node parent = (Node) it.next();
                if (parent.getNodeType() == NodeType.LATENT) {
                    this.parents[i][count] =
                            ((Integer) this.latentNames.get(parent.getName()));
                    this.parentsL[i][count++] = true;
                } else if (parent.getNodeType() == NodeType.MEASURED) {
                    this.parents[i][count] =
                            ((Integer) this.observableNames.get(
                                    parent.getName()));
                    this.parentsL[i][count++] = false;
                }
            }

            int numCovar = 0;
            for (int j = 0; j < correlatedErrors.length; j++) {
                if (i != j && correlatedErrors[i][j]) {
                    numCovar++;
                }
            }
            if (numCovar > 0) {
                this.spouses[i] = new int[numCovar];
                int countS = 0;
                for (int j = 0; j < this.numObserved; j++) {
                    if (i == j) {
                        continue;
                    }
                    if (correlatedErrors[i][j]) {
                        this.spouses[i][countS++] = j;
                    }
                }
                this.nSpouses[i] = countS;
            } else {
                this.spouses[i] = null;
                this.nSpouses[i] = 0;
            }
            this.parentsCov[i] =
                    new double[this.parents[i].length][this.parents[i].length];
            this.parentsChildCov[i] = new double[this.parents[i].length];
            this.pseudoParentsCov[i] =
                    new double[this.parents[i].length + this.nSpouses[i]][
                            this.parents[i].length + this.nSpouses[i]];
            this.pseudoParentsChildCov[i] =
                    new double[this.parents[i].length + this.nSpouses[i]];

            this.parentsResidualsCovar[i] = new double[this.parents[i].length][
                    this.numLatent + this.numObserved - 1];
            this.selectedInverseOmega[i] = new double[this.nSpouses[i]][
                    this.numLatent + this.numObserved - 1];
            this.auxInverseOmega[i] = new double[this.nSpouses[i]][
                    this.numLatent + this.numObserved - 1];
        }
    }

    /**
     * The expectation step for the structural EM algorithm. This is heavily based on "EM Algorithms for ML Factor
     * Analysis", by Rubin and Thayer (Psychometrika, 1982)
     */

    private void gaussianExpectation(final SemIm semIm) {
        //Get the parameters
        final double[][] beta =
                new double[this.numLatent][this.numLatent];        //latent-to-latent coefficients
        final double[][] fi =
                new double[this.numLatent][this.numLatent];          //latent error terms covariance
        final double[][] lambdaI =
                new double[this.numObserved][this.numObserved]; //observed-to-indicatorcoefficients
        final double[][] lambdaL =
                new double[this.numObserved][this.numLatent];   //latent-to-indicatorcoefficients
        final double[][] tau =
                new double[this.numObserved][this.numObserved];     //measurement error variance
        //Note: error covariance matrix tau is usually *not* diagonal, unlike the implementation of other
        //structural EM algorithm such as in MimBuildScoreSearch.
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = 0; j < this.numLatent; j++) {
                beta[i][j] = 0.;
                fi[i][j] = 0.;
            }
        }
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numLatent; j++) {
                lambdaL[i][j] = 0.;
            }
        }
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numObserved; j++) {
                tau[i][j] = 0.;
                lambdaI[i][j] = 0.;
            }
        }
        final List parameters = semIm.getFreeParameters();
        final double[] paramValues = semIm.getFreeParamValues();
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = (Parameter) parameters.get(i);
            if (parameter.getType() == ParamType.COEF) {
                final Node from = parameter.getNodeA();
                final Node to = parameter.getNodeB();
                if (to.getNodeType() == NodeType.MEASURED &&
                        from.getNodeType() == NodeType.LATENT) {
                    //latent-to-indicator edge
                    final int position1 = (Integer) this.latentNames.get(from.getName());
                    final int position2 = (Integer) this.observableNames.get(to.getName());
                    lambdaL[position2][position1] = paramValues[i];
                } else if (to.getNodeType() == NodeType.MEASURED &&
                        from.getNodeType() == NodeType.MEASURED) {
                    //indicator-to-indicator edge
                    final int position1 =
                            (Integer) this.observableNames.get(from.getName());
                    final int position2 = (Integer) this.observableNames.get(to.getName());
                    lambdaI[position2][position1] = paramValues[i];
                } else if (to.getNodeType() == NodeType.LATENT) {
                    //latent-to-latent edge
                    final int position1 = (Integer) this.latentNames.get(from.getName());
                    final int position2 = (Integer) this.latentNames.get(to.getName());
                    beta[position2][position1] = paramValues[i];
                }
            } else if (parameter.getType() == ParamType.VAR) {
                Node exo = parameter.getNodeA();
                if (exo.getNodeType() == NodeType.ERROR) {
                    final Iterator ci = semIm.getSemPm().getGraph().getChildren(exo)
                            .iterator();
                    exo =
                            (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...
                }
                if (exo.getNodeType() == NodeType.LATENT) {
                    fi[((Integer) this.latentNames.get(
                            exo.getName()))][((Integer) this.latentNames
                            .get(exo.getName()))] = paramValues[i];
                } else {
                    tau[((Integer) this.observableNames.get(
                            exo.getName()))][((Integer) this.observableNames
                            .get(exo.getName()))] = paramValues[i];
                }
            } else if (parameter.getType() == ParamType.COVAR) {
                Node exo1 = parameter.getNodeA();
                Node exo2 = parameter.getNodeB();
                //exo1.getNodeType and exo1.getNodeType *should* be error terms of measured variables
                //We will change the pointers to point to their respective indicators
//                Iterator ci = semIm.getEstIm().getGraph().getChildren(exo1)
//                        .iterator();
//                exo1 =
//                        (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...
//                ci = semIm.getEstIm().getGraph().getChildren(exo2).iterator();
//                exo2 =
//                        (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...

                exo1 = semIm.getSemPm().getGraph().getVarNode(exo1);
                exo2 = semIm.getSemPm().getGraph().getVarNode(exo2);

                tau[((Integer) this.observableNames.get(
                        exo1.getName()))][((Integer) this.observableNames
                        .get(exo2.getName()))] = tau[((Integer) this.observableNames
                        .get(exo2.getName()))][((Integer) this.observableNames
                        .get(exo1.getName()))] = paramValues[i];
            }
        }

        //Fill expected sufficiente statistics accordingly to the order of
        //the variables table
        final double[][] identity = new double[this.numLatent][this.numLatent];
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = 0; j < this.numLatent; j++) {
                if (i == j) {
                    identity[i][j] = 1.;
                } else {
                    identity[i][j] = 0.;
                }
            }
        }
        final double[][] identityI = new double[this.numObserved][this.numObserved];
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numObserved; j++) {
                if (i == j) {
                    identityI[i][j] = 1.;
                } else {
                    identityI[i][j] = 0.;
                }
            }
        }
        final double[][] iMinusB =
                MatrixUtils.inverse(MatrixUtils.subtract(identity, beta));
        final double[][] latentImpliedCovar = MatrixUtils.product(iMinusB,
                MatrixUtils.product(fi, MatrixUtils.transpose(iMinusB)));
        final double[][] iMinusI =
                MatrixUtils.inverse(MatrixUtils.subtract(identityI, lambdaI));
        final double[][] indImpliedCovar = MatrixUtils.product(MatrixUtils.product(
                        iMinusI, MatrixUtils.sum(MatrixUtils.product(
                                MatrixUtils.product(lambdaL, latentImpliedCovar),
                                MatrixUtils.transpose(lambdaL)), tau)),
                MatrixUtils.transpose(iMinusI));
        final double[][] loadingLatentCovar = MatrixUtils.product(iMinusI,
                MatrixUtils.product(lambdaL, latentImpliedCovar));
        final double[][] smallDelta = MatrixUtils.product(
                MatrixUtils.inverse(indImpliedCovar), loadingLatentCovar);
        final double[][] bigDelta = MatrixUtils.subtract(latentImpliedCovar,
                MatrixUtils.product(MatrixUtils.transpose(loadingLatentCovar),
                        smallDelta));
        this.Cyz = MatrixUtils.product(this.Cyy, smallDelta);
        this.Czz = MatrixUtils.sum(
                MatrixUtils.product(MatrixUtils.transpose(smallDelta), this.Cyz),
                bigDelta);
    }

    /*private SemIm getDummyExample()
    {
        this.numObserved = 13;
        this.numLatent = 4;

        ProtoSemGraph newGraph = new ProtoSemGraph();
        Node v[] = new Node[17];
        for (int i = 0; i < 17; i++) {
            if (i < 4)
                v[i] = new GraphNode("L" + (i + 1));
            else
                v[i] = new GraphNode("v" + (i - 3));
            newGraph.addNode(v[i]);
        }
        for (int l = 0; l < numLatent; l++) {
            for (int l2 = l + 1; l2 < numLatent; l2++)
                newGraph.addDirectedEdge(v[l], v[l2]);
            for (int i = 0; i < 3; i++)
                newGraph.addDirectedEdge(v[l], v[l * 3 + i + 4]);
        }
        newGraph.addDirectedEdge(v[3], v[16]);
        newGraph.addDirectedEdge(v[4], v[6]);
        newGraph.addDirectedEdge(v[14], v[15]);
        newGraph.addBidirectedEdge(v[9], v[10]);
        newGraph.addBidirectedEdge(v[7], v[12]);
        newGraph.addBidirectedEdge(v[12], v[16]);

        SemIm realIm = SemIm.newInstance(new SemPm(newGraph));

        DataSet data = new DataSet(realIm.simulateData(1000));
        System.out.println(data.toString());
        System.out.println(new CovarianceMatrix(data));
        System.out.println();

        this.latentNames = new Hashtable();
        this.latentNodes = new ArrayList();
        for (int i = 0; i < numLatent; i++) {
            latentNames.put(v[i].getNode(), new Integer(i));
            latentNodes.add(v[i]);
        }
        this.observableNames = new Hashtable();
        this.measuredNodes = new ArrayList();
        for (int i = numLatent; i < numLatent + numObserved; i++) {
            observableNames.put(v[i].getNode(), new Integer(i - numLatent));
            measuredNodes.add(v[i]);
        }

        double temp[][] = (new CovarianceMatrix(data)).getMatrix();
        Cyy = new double[numObserved][numObserved];
        Czz = new double[numLatent][numLatent];
        Cyz = new double[numObserved][numLatent];
        for (int i = 0; i < numLatent; i++)
            for (int j = 0; j < numLatent; j++)
                Czz[i][j] = temp[i][j];
        for (int i = 0; i < numObserved; i++) {
            for (int j = 0; j < numLatent; j++)
                 Cyz[i][j] = temp[i + numLatent][j];
             for (int j = 0; j < numObserved; j++)
                 Cyy[i][j] = temp[i + numLatent][j + numLatent];
        }


        this.correlatedErrors = new boolean[numObserved][numObserved];
        this.latentParent = new boolean[numObserved][numLatent];
        this.observedParent = new boolean[numObserved][numObserved];

        CovarianceMatrix = new CovarianceMatrix(data);

        //Information for MAG maximization
        this.parents = new int[this.numObserved][];
        this.spouses = new int[this.numObserved][];
        this.nSpouses = new int[this.numObserved];
        this.parentsLat = new int[this.numLatent][];
        this.parentsL = new boolean[this.numObserved][];
        this.parentsCov = new double[this.numObserved][][];
        this.parentsChildCov = new double[this.numObserved][];
        this.parentsLatCov = new double[this.numLatent][][];
        this.parentsChildLatCov = new double[this.numLatent][];
        this.pseudoParentsCov = new double[this.numObserved][][];
        this.pseudoParentsChildCov = new double[this.numObserved][];
        this.covErrors = new double[this.numObserved][this.numObserved];
        this.oldCovErrors = new double[this.numObserved][this.numObserved];
        this.sampleCovErrors = new double[this.numObserved][this.numObserved];
        this.varErrorLatent = new double[this.numLatent];
        this.omega = new double[this.numLatent + this.numObserved - 1][this.numLatent + this.numObserved - 1];
        this.omegaI = new double[this.numLatent + this.numObserved - 1];
        this.selectedInverseOmega = new double[this.numObserved][][];
        this.auxInverseOmega = new double[this.numObserved][][];
        this.parentsResidualsCovar = new double[this.numObserved][][];
        this.iResidualsCovar = new double[this.numObserved + this.numLatent - 1];
        this.betas = new double[this.numObserved][this.numObserved + this.numLatent];
        this.oldBetas = new double[this.numObserved][this.numObserved + this.numLatent];
        this.betasLat = new double[this.numLatent][this.numLatent];

        for (int i = 0; i < numLatent; i++)
             v[i].setNodeType(NodeType.LATENT);
        SemGraph2 semGraph = new SemGraph2(newGraph);
        initializeGaussianEM(semGraph);

        return realIm;
    }*/

    private double gaussianMaximization(final SemIm semIm) {
        semIm.getSemPm().getGraph().setShowErrorTerms(true);
        //SemIm realIm = getDummyExample();
        //semIm = SemIm.newInstance(realIm.getEstIm());

        //Fill matrices with semIm parameters
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numObserved + this.numLatent; j++) {
                this.betas[i][j] = 0.;
            }
        }
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = 0; j < this.numLatent; j++) {
                this.betasLat[i][j] = 0.;
            }
        }
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.numObserved; j++) {
                this.covErrors[i][j] = 0.;
            }
        }
        for (final Iterator it = semIm.getFreeParameters().iterator(); it.hasNext(); ) {
            final Parameter nextP = (Parameter) it.next();
            if (nextP.getType() == ParamType.COEF) {
                final Node node1 = nextP.getNodeA();
                final Node node2 = nextP.getNodeB();
                if (node1.getNodeType() == NodeType.LATENT &&
                        node2.getNodeType() == NodeType.LATENT) {
                    continue;
                }
                Node latent = null, observed = null;
                if (node1.getNodeType() == NodeType.LATENT) {
                    latent = node1;
                    observed = node2;
                } else if (node2.getNodeType() == NodeType.LATENT) {
                    latent = node2;
                    observed = node1;
                }
                if (latent != null) {
                    final int index1 =
                            (Integer) this.latentNames.get(latent.getName());
                    final int index2 = (Integer) this.observableNames.get(
                            observed.getName());
                    this.betas[index2][index1] = semIm.getParamValue(nextP);
                } else {
                    final int index1 =
                            (Integer) this.observableNames.get(node1.getName());
                    final int index2 =
                            (Integer) this.observableNames.get(node2.getName());
                    if (semIm.getSemPm().getGraph().isParentOf(node1, node2)) {
                        this.betas[index2][this.numLatent + index1] =
                                semIm.getParamValue(nextP);
                    } else {
                        this.betas[index1][this.numLatent + index2] =
                                semIm.getParamValue(nextP);
                    }
                }
            } else if (nextP.getType() == ParamType.COVAR) {
                Node exo1 = nextP.getNodeA();
                Node exo2 = nextP.getNodeB();
                //exo1.getNodeType and exo1.getNodeType *should* be error terms of measured variables
                //We will change the pointers to point to their respective indicators
//                Iterator ci = semIm.getEstIm().getGraph().getChildren(exo1)
//                        .iterator();
//                exo1 =
//                        (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...
//                ci = semIm.getEstIm().getGraph().getChildren(exo2).iterator();
//                exo2 =
//                        (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...

                exo1 = semIm.getSemPm().getGraph().getVarNode(exo1);
                exo2 = semIm.getSemPm().getGraph().getVarNode(exo2);

                final int index1 = (Integer) this.observableNames.get(exo1.getName());
                final int index2 = (Integer) this.observableNames.get(exo2.getName());
                this.covErrors[index1][index2] =
                        this.covErrors[index2][index1] =
                                semIm.getParamValue(nextP);
            } else if (nextP.getType() == ParamType.VAR) {
                Node exo = nextP.getNodeA();
                if (exo.getNodeType() == NodeType.LATENT) {
                    continue;
                }
//                Iterator ci = semIm.getEstIm().getGraph().getChildren(exo)
//                        .iterator();
//                exo =
//                        (Node) ci.next(); //Assuming error nodes have only one children in SemGraphs...

                exo = semIm.getSemPm().getGraph().getVarNode(exo);

                if (exo.getNodeType() == NodeType.MEASURED) {
                    final int index =
                            (Integer) this.observableNames.get(exo.getName());
                    this.covErrors[index][index] = semIm.getParamValue(nextP);
                }
            }
        }

        //Find estimates for the latent->latent edges and latent variances
        //Assuming latents[0] is always the exogenous node in the latent layer
        this.varErrorLatent[0] = this.Czz[0][0];
        for (int i = 1; i < this.numLatent; i++) {
            for (int j = 0; j < this.parentsLat[i].length; j++) {
                this.parentsChildLatCov[i][j] =
                        this.Czz[i][this.parentsLat[i][j]];
                for (int k = j; k < this.parentsLat[i].length; k++) {
                    this.parentsLatCov[i][j][k] =
                            this.Czz[this.parentsLat[i][j]][this.parentsLat[i][k]];
                    this.parentsLatCov[i][k][j] = this.parentsLatCov[i][j][k];
                }
            }
            final double[] betaL = MatrixUtils.product(
                    MatrixUtils.inverse(this.parentsLatCov[i]),
                    this.parentsChildLatCov[i]);
            this.varErrorLatent[i] = this.Czz[i][i] -
                    MatrixUtils.innerProduct(this.parentsChildLatCov[i], betaL);
            for (int j = 0; j < this.parentsLat[i].length; j++) {
                this.betasLat[i][this.parentsLat[i][j]] = betaL[j];
            }
        }

        //Initialize the covariance matrix for the parents of every observed node
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = 0; j < this.parents[i].length; j++) {
                if (this.parentsL[i][j]) {
                    this.parentsChildCov[i][j] =
                            this.Cyz[i][this.parents[i][j]];
                } else {
                    this.parentsChildCov[i][j] =
                            this.Cyy[i][this.parents[i][j]];
                }
                for (int k = j; k < this.parents[i].length; k++) {
                    if (this.parentsL[i][j] && this.parentsL[i][k]) {
                        this.parentsCov[i][j][k] =
                                this.Czz[this.parents[i][j]][this.parents[i][k]];
                    } else if (!this.parentsL[i][j] && this.parentsL[i][k]) {
                        this.parentsCov[i][j][k] =
                                this.Cyz[this.parents[i][j]][this.parents[i][k]];
                    } else if (this.parentsL[i][j] && !this.parentsL[i][k]) {
                        this.parentsCov[i][j][k] =
                                this.Cyz[this.parents[i][k]][this.parents[i][j]];
                    } else {
                        this.parentsCov[i][j][k] =
                                this.Cyy[this.parents[i][j]][this.parents[i][k]];
                    }
                    this.parentsCov[i][k][j] = this.parentsCov[i][j][k];
                }
            }
        }

        //ICF algorithm of Drton and Richardson to find estimates for the other edges and variances/covariances
        double change;
        int iter = 0;
        do {
            for (int i = 0; i < this.covErrors.length; i++) {
                for (int j = 0; j < this.covErrors.length; j++) {
                    this.oldCovErrors[i][j] = this.covErrors[i][j];
                }
            }
            for (int i = 0; i < this.numObserved; i++) {
                for (int j = 0; j < this.betas[i].length; j++) {
                    this.oldBetas[i][j] = this.betas[i][j];
                }
            }

            for (int i = 0; i < this.numObserved; i++) {

                //Build matrix Omega_{-i,-i} as defined in Drton and Richardson (2003)
                for (int ii = 0; ii < this.omega.length; ii++) {
                    for (int j = 0; j < this.omega.length; j++) {
                        this.omega[ii][j] = 0.;
                    }
                }
                for (int ii = 0; ii < this.numLatent; ii++) {
                    this.omegaI[ii] = 0.;
                    this.omega[ii][ii] = this.varErrorLatent[ii];
                }
                for (int ii = 0; ii < this.numObserved; ii++) {
                    if (ii > i) {
                        this.omegaI[this.numLatent + ii - 1] =
                                this.covErrors[i][ii];
                        this.omega[this.numLatent + ii - 1][
                                this.numLatent + ii - 1] =
                                this.covErrors[ii][ii];
                    } else if (ii < i) {
                        this.omegaI[this.numLatent + ii] =
                                this.covErrors[i][ii];
                        this.omega[this.numLatent + ii][this.numLatent + ii] =
                                this.covErrors[ii][ii];
                    }
                }
                for (int ii = 0; ii < this.numObserved; ii++) {
                    final int index_ii;
                    if (ii > i) {
                        index_ii = this.numLatent + ii - 1;
                    } else if (ii < i) {
                        index_ii = this.numLatent + ii;
                    } else {
                        continue;
                    }
                    for (int j = 0; j < this.nSpouses[ii]; j++) {
                        if (this.spouses[ii][j] > i) {
                            this.omega[index_ii][
                                    this.numLatent + this.spouses[ii][j] - 1] =
                                    this.covErrors[ii][this.spouses[ii][j]];
                        } else if (this.spouses[ii][j] < i) {
                            this.omega[index_ii][this.numLatent +
                                    this.spouses[ii][j]] =
                                    this.covErrors[ii][this.spouses[ii][j]];
                        }
                    }
                }

                /*int tspouses = 0;
                for (int s = 0; s < numObserved; s++)
                    tspouses += nSpouses[s];
                if (tspouses > 0 && iter == 0) {
                    System.out.println();
                    System.out.println("OMEGA: Trial " + iter);
                    for (int v = 0; v < this.numLatent + this.numObserved - 1; v++) {
                       for (int k = 0; k < this.numLatent + this.numObserved - 1; k++)
                           System.out.print(this.omega[v][k] + " ");
                        System.out.println();
                    }
                    System.out.println();
                }*/

                //Find new residuals covariance matrix for every ii != i
                for (int ii = 0; ii < this.numObserved; ii++) {
                    if (ii == i) {
                        continue;
                    }
                    for (int j = ii; j < this.numObserved; j++) {
                        if (j == i) {
                            continue;
                        }
                        this.sampleCovErrors[ii][j] = this.Cyy[ii][j];
                        for (int p = 0; p < this.parents[ii].length; p++) {
                            if (this.parentsL[ii][p]) {
                                this.sampleCovErrors[ii][j] -=
                                        this.betas[ii][this.parents[ii][p]] *
                                                this.Cyz[j][this.parents[ii][p]];
                            } else {
                                this.sampleCovErrors[ii][j] -= this.betas[ii][
                                        this.numLatent + this.parents[ii][p]] *
                                        this.Cyy[j][this.parents[ii][p]];
                            }
                        }
                        for (int p = 0; p < this.parents[j].length; p++) {
                            if (this.parentsL[j][p]) {
                                this.sampleCovErrors[ii][j] -=
                                        this.betas[j][this.parents[j][p]] *
                                                this.Cyz[ii][this.parents[j][p]];
                            } else {
                                this.sampleCovErrors[ii][j] -= this.betas[j][
                                        this.numLatent + this.parents[j][p]] *
                                        this.Cyy[ii][this.parents[j][p]];
                            }
                        }
                        for (int p1 = 0; p1 < this.parents[ii].length; p1++) {
                            for (int p2 = 0; p2 < this.parents[j].length; p2++) {
                                if (this.parentsL[ii][p1] &&
                                        this.parentsL[j][p2]) {
                                    this.sampleCovErrors[ii][j] +=
                                            this.betas[ii][this.parents[ii][p1]] *
                                                    this.betas[j][this.parents[j][p2]] *
                                                    this.Czz[this.parents[ii][p1]][this.parents[j][p2]];
                                } else if (this.parentsL[ii][p1] &&
                                        !this.parentsL[j][p2]) {
                                    this.sampleCovErrors[ii][j] +=
                                            this.betas[ii][this.parents[ii][p1]] *
                                                    this.betas[j][this.numLatent +
                                                            this.parents[j][p2]] *
                                                    this.Cyz[this.parents[j][p2]][this.parents[ii][p1]];
                                } else if (!this.parentsL[ii][p1] &&
                                        this.parentsL[j][p2]) {
                                    this.sampleCovErrors[ii][j] +=
                                            this.betas[ii][this.numLatent +
                                                    this.parents[ii][p1]] *
                                                    this.betas[j][this.parents[j][p2]] *
                                                    this.Cyz[this.parents[ii][p1]][this.parents[j][p2]];
                                } else {
                                    this.sampleCovErrors[ii][j] +=
                                            this.betas[ii][this.numLatent +
                                                    this.parents[ii][p1]] *
                                                    this.betas[j][this.numLatent +
                                                            this.parents[j][p2]] *
                                                    this.Cyy[this.parents[ii][p1]][this.parents[j][p2]];
                                }
                            }
                        }
                        this.sampleCovErrors[j][ii] =
                                this.sampleCovErrors[ii][j];
                    }
                }

                //First, find the covariance of the parents of i and the residuals \epsilon_{-i}
                for (int ii = 0; ii < this.parents[i].length; ii++) {
                    //covariance of the parent wrt every residual of latents
                    if (this.parentsL[i][ii]) {
                        this.parentsResidualsCovar[i][ii][0] =
                                this.Czz[this.parents[i][ii]][0];
                    } else {
                        this.parentsResidualsCovar[i][ii][0] =
                                this.Cyz[this.parents[i][ii]][0];
                    }
                    for (int j = 1; j < this.numLatent; j++) {
                        if (this.parentsL[i][ii]) {
                            this.parentsResidualsCovar[i][ii][j] =
                                    this.Czz[this.parents[i][ii]][j];
                            for (int p = 0; p < this.parentsLat[j].length; p++) {
                                this.parentsResidualsCovar[i][ii][j] -=
                                        this.betasLat[j][this.parentsLat[j][p]] *
                                                this.Czz[this.parents[i][ii]][this.parentsLat[j][p]];
                            }
                        } else {
                            this.parentsResidualsCovar[i][ii][j] =
                                    this.Cyz[this.parents[i][ii]][j];
                            for (int p = 0; p < this.parentsLat[j].length; p++) {
                                this.parentsResidualsCovar[i][ii][j] -=
                                        this.betasLat[j][this.parentsLat[j][p]] *
                                                this.Cyz[this.parents[i][ii]][this.parentsLat[j][p]];
                            }
                        }
                    }
                    //covariance of the parent wrt every residual of observables (except for i)
                    for (int j = 0; j < this.numObserved; j++) {
                        final int index_j;
                        if (j < i) {
                            index_j = this.numLatent + j;
                        } else if (j > i) {
                            index_j = this.numLatent + j - 1;
                        } else {
                            continue;
                        }
                        if (this.parentsL[i][ii]) {
                            this.parentsResidualsCovar[i][ii][index_j] =
                                    this.Cyz[j][this.parents[i][ii]];
                            for (int p = 0; p < this.parents[j].length; p++) {
                                if (this.parentsL[j][p]) {
                                    this.parentsResidualsCovar[i][ii][index_j] -=
                                            this.betas[j][this.parents[j][p]] *
                                                    this.Czz[this.parents[i][ii]][this.parents[j][p]];
                                } else {
                                    this.parentsResidualsCovar[i][ii][index_j] -=
                                            this.betas[j][this.numLatent +
                                                    this.parents[j][p]] *
                                                    this.Cyz[this.parents[j][p]][this.parents[i][ii]];
                                }
                            }
                        } else {
                            this.parentsResidualsCovar[i][ii][index_j] =
                                    this.Cyy[j][this.parents[i][ii]];
                            for (int p = 0; p < this.parents[j].length; p++) {
                                if (this.parentsL[j][p]) {
                                    this.parentsResidualsCovar[i][ii][index_j] -=
                                            this.betas[j][this.parents[j][p]] *
                                                    this.Cyz[this.parents[i][ii]][this.parents[j][p]];
                                } else {
                                    this.parentsResidualsCovar[i][ii][index_j] -=
                                            this.betas[j][this.numLatent +
                                                    this.parents[j][p]] *
                                                    this.Cyy[this.parents[j][p]][this.parents[i][ii]];
                                }
                            }
                        }
                    }
                }
                //Now, find the covariance of Y_i with respect to everybody else's residuals
                this.iResidualsCovar[0] =
                        this.Cyz[i][0]; //the first latent is exogenous
                for (int j = 1; j < this.numLatent; j++) {
                    this.iResidualsCovar[j] = this.Cyz[i][j];
                    for (int p = 0; p < this.parentsLat[j].length; p++) {
                        this.iResidualsCovar[j] -=
                                this.betasLat[j][this.parentsLat[j][p]] *
                                        this.Cyz[i][this.parentsLat[j][p]];
                    }
                }
                for (int j = 0; j < this.numObserved; j++) {
                    final int index_j;
                    if (j < i) {
                        index_j = this.numLatent + j;
                    } else if (j > i) {
                        index_j = this.numLatent + j - 1;
                    } else {
                        continue;
                    }
                    this.iResidualsCovar[index_j] = this.Cyy[i][j];
                    for (int p = 0; p < this.parents[j].length; p++) {
                        if (this.parentsL[j][p]) {
                            this.iResidualsCovar[index_j] -=
                                    this.betas[j][this.parents[j][p]] *
                                            this.Cyz[i][this.parents[j][p]];
                        } else {
                            this.iResidualsCovar[index_j] -= this.betas[j][this.numLatent + this.parents[j][p]] *
                                    this.Cyy[i][this.parents[j][p]];
                        }
                    }
                }
                //Transform it to get the covariance of parents of i and pseudo-variables Z_sp(i)
                final double[][] inverseOmega = MatrixUtils.inverse(this.omega);
                for (int ii = 0; ii < this.nSpouses[i]; ii++) {
                    final int sp_index;
                    if (this.spouses[i][ii] > i) {
                        sp_index = this.numLatent + this.spouses[i][ii] - 1;
                    } else {
                        sp_index = this.numLatent + this.spouses[i][ii];
                    }
                    for (int j = 0;
                         j < this.numLatent + this.numObserved - 1; j++) {
                        this.selectedInverseOmega[i][ii][j] =
                                inverseOmega[sp_index][j];
                    }
                }
                for (int ii = 0; ii < this.nSpouses[i]; ii++) {
                    for (int j = 0; j < this.numLatent; j++) {
                        this.auxInverseOmega[i][ii][j] =
                                this.selectedInverseOmega[i][ii][j] *
                                        this.varErrorLatent[j];
                    }
                    for (int j = 0; j < this.numObserved; j++) {
                        final int index_j;
                        if (j > i) {
                            index_j = this.numLatent + j - 1;
                        } else if (j < i) {
                            index_j = this.numLatent + j;
                        } else {
                            continue;
                        }
                        this.auxInverseOmega[i][ii][index_j] = 0;
                        for (int k = 0; k < this.numObserved; k++) {
                            final int index_k;
                            if (k > i) {
                                index_k = this.numLatent + k - 1;
                            } else if (k < i) {
                                index_k = this.numLatent + k;
                            } else {
                                continue;
                            }
                            this.auxInverseOmega[i][ii][index_j] +=
                                    this.selectedInverseOmega[i][ii][index_k] *
                                            this.sampleCovErrors[k][j];
                        }
                    }
                }

                for (int ii = 0; ii < this.parents[i].length; ii++) {
                    for (int j = ii; j < this.parents[i].length; j++) {
                        this.pseudoParentsCov[i][ii][j] =
                                this.pseudoParentsCov[i][j][ii] =
                                        this.parentsCov[i][ii][j];
                    }
                }
                for (int ii = 0; ii < this.parents[i].length; ii++) {
                    for (int j = 0; j < this.nSpouses[i]; j++) {
                        this.pseudoParentsCov[i][ii][this.parents[i].length +
                                j] = 0.;
                        for (int k = 0;
                             k < this.numLatent + this.numObserved - 1; k++) {
                            this.pseudoParentsCov[i][ii][this.parents[i]
                                    .length + j] +=
                                    this.parentsResidualsCovar[i][ii][k] *
                                            this.selectedInverseOmega[i][j][k];
                        }
                        this.pseudoParentsCov[i][this.parents[i].length +
                                j][ii] = this.pseudoParentsCov[i][ii][
                                this.parents[i].length + j];
                    }
                }
                for (int ii = 0; ii < this.nSpouses[i]; ii++) {
                    for (int j = ii; j < this.nSpouses[i]; j++) {
                        this.pseudoParentsCov[i][this.parents[i].length + ii][
                                this.parents[i].length + j] = 0;
                        for (int k = 0;
                             k < this.numLatent + this.numObserved - 1; k++) {
                            this.pseudoParentsCov[i][this.parents[i].length +
                                    ii][this.parents[i].length + j] +=
                                    this.auxInverseOmega[i][ii][k] *
                                            this.selectedInverseOmega[i][j][k];
                        }
                        this.pseudoParentsCov[i][this.parents[i].length + j][
                                this.parents[i].length + ii] =
                                this.pseudoParentsCov[i][this.parents[i]
                                        .length + ii][this.parents[i].length +
                                        j];
                        if (this.pseudoParentsCov[i][this.parents[i].length +
                                j][this.parents[i].length + ii] == 0.) {
                            System.out.println("Zero here... Iter = " + iter);
                            /*for (int k = 0; k < this.numLatent + this.numObserved - 1; k++)
                                System.out.println(this.auxInverseOmega[i][ii][k] + " " +  this.selectedInverseOmega[i][j][k]);
                            System.out.println();
                            for (int v = 0; v < this.numLatent + this.numObserved - 1; v++) {
                               for (int k = 0; k < this.numLatent + this.numObserved - 1; k++)
                                   System.out.print(this.omega[v][k] + " ");
                                System.out.println();
                            }
                            System.out.println(semIm.getEstIm().getDag());
                            System.out.println("PARENTS");
                            for (int n = 0; n < this.numObserved; n++) {
                                System.out.print(this.measuredNodes.get(n).toString() + " - ");
                                for (int p = 0; p < this.parents[n].length; p++) {
                                    if (parentsL[n][p])
                                       System.out.print(this.latentNodes.get(parents[n][p]).toString() + " ");
                                    else
                                       System.out.print(this.measuredNodes.get(parents[n][p]).toString() + " ");
                                }
                                System.out.println();
                            }

                            System.out.println("SPOUSES");
                            for (int n = 0; n < this.numObserved; n++) {
                                System.out.print(this.measuredNodes.get(n).toString() + " - ");
                                for (int p = 0; p < this.nSpouses[n]; p++) {
                                    System.out.print(this.measuredNodes.get(spouses[n][p]).toString() + " ");
                                }
                                System.out.println();
                            }
                            System.exit(0);*/
                            iter = 1000;
                            break;
                        }
                    }
                }
                //Get the covariance of parents of i and pseudo-variables Z_sp(i) with respect to i
                for (int ii = 0; ii < this.parents[i].length; ii++) {
                    this.pseudoParentsChildCov[i][ii] =
                            this.parentsChildCov[i][ii];
                }
                for (int j = 0; j < this.nSpouses[i]; j++) {
                    this.pseudoParentsChildCov[i][this.parents[i].length + j] =
                            0;
                    for (int k = 0;
                         k < this.numLatent + this.numObserved - 1; k++) {
                        this.pseudoParentsChildCov[i][this.parents[i].length +
                                j] += this.selectedInverseOmega[i][j][k] *
                                this.iResidualsCovar[k];
                    }
                }

                //Finally, regress Y_i on {parents} union {Z_i}
                //thisI = i;
                final double[] params = MatrixUtils.product(
                        MatrixUtils.inverse(this.pseudoParentsCov[i]),
                        this.pseudoParentsChildCov[i]);
                //Update betas and omegas (entries in covErrors)
                for (int j = 0; j < this.parents[i].length; j++) {
                    if (this.parentsL[i][j]) {
                        this.betas[i][this.parents[i][j]] = params[j];
                    } else {
                        this.betas[i][this.numLatent + this.parents[i][j]] =
                                params[j];
                    }
                }
                for (int j = 0; j < this.nSpouses[i]; j++) {
                    this.covErrors[i][this.spouses[i][j]] =
                            this.covErrors[this.spouses[i][j]][i] =
                                    params[this.parents[i].length + j];
                    if (this.spouses[i][j] > i) {
                        this.omegaI[this.numLatent + this.spouses[i][j] - 1] =
                                params[this.parents[i].length + j];
                    } else {
                        this.omegaI[this.numLatent + this.spouses[i][j]] =
                                params[this.parents[i].length + j];
                    }
                }
                final double conditionalVar = this.Cyy[i][i] -
                        MatrixUtils.innerProduct(this.pseudoParentsChildCov[i],
                                params);
                this.covErrors[i][i] = conditionalVar +
                        MatrixUtils.innerProduct(
                                MatrixUtils.product(this.omegaI, inverseOmega),
                                this.omegaI);
            }
            change = 0.;
            for (int i = 0; i < this.covErrors.length; i++) {
                for (int j = i; j < this.covErrors.length; j++) {
                    change += Math.abs(
                            this.oldCovErrors[i][j] - this.covErrors[i][j]);
                }
            }
            for (int i = 0; i < this.numObserved; i++) {
                for (int j = 0; j < this.betas[i].length; j++) {
                    change += Math.abs(this.oldBetas[i][j] - this.betas[i][j]);
                }
            }
            iter++;
            //System.out.println("Iteration = " + iter + ", change = " + change);
        } while (iter < 200 && change > 0.01);
        //Now, copy updated parameters back to semIm
        try {
            for (int i = 0; i < this.numObserved; i++) {
                final Node node = semIm.getSemPm().getGraph().getNode(
                        this.measuredNodes.get(i).toString());
//                Node nodeErrorTerm = null;
//                for (Node parent : semIm.getEstIm().getGraph().getParents(node)) {
//                    if (parent.getNodeType() == NodeType.ERROR) {
//                        semIm.setParamValue(parent, parent,
//                                this.covErrors[i][i]);
//                        nodeErrorTerm = parent;
//                        break;
//                    }
//                }

                final Node nodeErrorTerm = semIm.getSemPm().getGraph().getExogenous(node);

                for (int j = 0; j < this.parents[i].length; j++) {
                    final Node parent;
                    if (this.parentsL[i][j]) {
                        parent = semIm.getSemPm().getGraph().getNode(
                                this.latentNodes.get(this.parents[i][j])
                                        .toString());
                    } else {
                        parent = semIm.getSemPm().getGraph().getNode(
                                this.measuredNodes.get(this.parents[i][j])
                                        .toString());
                    }
                    if (this.parentsL[i][j]) {
                        semIm.setParamValue(parent, node,
                                this.betas[i][this.parents[i][j]]);
                    } else {
                        semIm.setParamValue(parent, node, this.betas[i][this.numLatent + this.parents[i][j]]);
                    }
                }
                for (int j = 0; j < this.nSpouses[i]; j++) {
                    if (this.spouses[i][j] > i) {
                        final Node spouse = semIm.getSemPm().getGraph().getNode(
                                this.measuredNodes.get(this.spouses[i][j])
                                        .toString());
//                        Node spouseErrorTerm = null;
//                        for (Iterator it = semIm.getEstIm().getGraph()
//                                .getParents(spouse).iterator(); it.hasNext();) {
//                            Node nextParent = (Node) it.next();
//                            if (nextParent.getNodeType() == NodeType.ERROR) {
//                                spouseErrorTerm = nextParent;
//                                break;
//                            }
//                        }

                        final Node spouseErrorTerm = semIm.getSemPm().getGraph().getExogenous(spouse);

                        semIm.setParamValue(nodeErrorTerm, spouseErrorTerm,
                                this.covErrors[i][this.spouses[i][j]]);
                    }
                }
            }
            for (int i = 0; i < this.numLatent; i++) {
                final Node node = semIm.getSemPm().getGraph().getNode(
                        this.latentNodes.get(i).toString());
                if (semIm.getSemPm().getGraph().getParents(node).size() == 0) {
                    semIm.setParamValue(node, node, this.varErrorLatent[i]);
                } else {
                    for (final Iterator it =
                         semIm.getSemPm().getGraph().getParents(node)
                                 .iterator(); it.hasNext(); ) {
                        final Node nextParent = (Node) it.next();
                        if (nextParent.getNodeType() == NodeType.ERROR) {
                            semIm.setParamValue(nextParent, nextParent,
                                    this.varErrorLatent[i]);
                            break;
                        }
                    }
                    for (int j = 0; j < this.parentsLat[i].length; j++) {
                        final Node parent = semIm.getSemPm().getGraph().getNode(
                                this.latentNodes.get(this.parentsLat[i][j])
                                        .toString());
                        semIm.setParamValue(parent, node,
                                this.betasLat[i][this.parentsLat[i][j]]);
                    }
                }
            }
            /*if (true)  {
                System.out.println("******* REAL IM"); System.out.println();
                System.out.println(realIm.toString());
                System.out.println("******* ESTIMATED IM"); System.out.println();
                System.out.println(semIm.toString());
                System.exit(0);
            }*/

            return -semIm.getTruncLL() - 0.5 * semIm.getNumFreeParams() *
                    Math.log(this.covarianceMatrix.getSampleSize());
        } catch (final java.lang.IllegalArgumentException e) {
            System.out.println("** Warning: " + e.toString());
            return -Double.MAX_VALUE;
        }
    }

    private SemGraph removeMarkedImpurities(final SemGraph graph,
                                            final boolean[][] impurities) {
        printlnMessage();
        printlnMessage("** PURIFY: using marked impure pairs");
        final List latents = new ArrayList();
        final List partition = new ArrayList();
        for (int i = 0; i < graph.getNodes().size(); i++) {
            final Node nextLatent = graph.getNodes().get(i);
            if (nextLatent.getNodeType() != NodeType.LATENT) {
                continue;
            }
            latents.add(graph.getNodes().get(i));
            final Iterator cit = graph.getChildren(nextLatent).iterator();
            final List children = new ArrayList();
            while (cit.hasNext()) {
                final Node cnext = (Node) cit.next();
                if (cnext.getNodeType() == NodeType.MEASURED) {
                    children.add(cnext);
                }
            }
            final int[] newCluster = new int[children.size()];
            for (int j = 0; j < children.size(); j++) {
                newCluster[j] = ((Integer) this.observableNames.get(
                        children.get(j).toString()));
            }
            partition.add(newCluster);
        }
        for (int i = 0; i < impurities.length - 1; i++) {
            for (int j = i + 1; j < impurities.length; j++) {
                if (impurities[i][j]) {
                    System.out.println(this.measuredNodes.get(i).toString() + " x " +
                            this.measuredNodes.get(j).toString());
                }
            }
        }
        final List latentCliques = new ArrayList();
        final int[] firstClique = new int[latents.size()];
        for (int i = 0; i < firstClique.length; i++) {
            firstClique[i] = i;
        }
        latentCliques.add(firstClique);

        //Now, ready to purify
        for (final Object latentClique : latentCliques) {
            final int[] nextLatentList = (int[]) latentClique;
            final List nextPartition = new ArrayList();
            for (int p = 0; p < nextLatentList.length; p++) {
                nextPartition.add(partition.get(nextLatentList[p]));
            }
            final List solution = findInducedPureGraph(nextPartition, impurities);
            if (solution != null) {

                System.out.println("--Solution");
                final Iterator it = solution.iterator();
                while (it.hasNext()) {
                    final int[] c = (int[]) it.next();
                    for (int v = 0; v < c.length; v++) {
                        System.out.print(
                                this.measuredNodes.get(c[v]).toString() + " ");
                    }
                    System.out.println();
                }

                printlnMessage(">> SIZE: " + sizeCluster(solution));
                printlnMessage(">> New solution found!");
                final SemGraph graph2 = new SemGraph();
                graph2.setShowErrorTerms(true);
                final Node[] latentsArray = new Node[solution.size()];
                for (int p = 0; p < solution.size(); p++) {
                    final int[] cluster = (int[]) solution.get(p);
                    latentsArray[p] =
                            new GraphNode(ClusterUtils.LATENT_PREFIX + (p + 1));
                    latentsArray[p].setNodeType(NodeType.LATENT);
                    graph2.addNode(latentsArray[p]);
                    for (int q = 0; q < cluster.length; q++) {
                        final Node newIndicator = new GraphNode(
                                this.measuredNodes.get(cluster[q]).toString());
                        graph2.addNode(newIndicator);
                        graph2.addDirectedEdge(latentsArray[p], newIndicator);
                    }
                }
                for (int p = 0; p < latentsArray.length - 1; p++) {
                    for (int q = p + 1; q < latentsArray.length; q++) {
                        graph2.addDirectedEdge(latentsArray[p],
                                latentsArray[q]);
                    }
                }
                return graph2;
            } else {
                return null;
            }
        }
        return null;
    }

    private List findInducedPureGraph(final List partition, final boolean[][] impurities) {
        //Store the ID of all elements for fast access
        final int[][] elements = new int[sizeCluster(partition)][3];
        final int[] partitionCount = new int[partition.size()];
        int countElements = 0;
        for (int p = 0; p < partition.size(); p++) {
            final int[] next = (int[]) partition.get(p);
            partitionCount[p] = 0;
            for (int i = 0; i < next.length; i++) {
                elements[countElements][0] = next[i]; // global ID
                elements[countElements][1] = p;       // set partition ID
                countElements++;
                partitionCount[p]++;
            }
        }
        //Count how many impure relations are entailed by each indicator
        for (int i = 0; i < elements.length; i++) {
            elements[i][2] = 0;
            for (int j = 0; j < elements.length; j++) {
                if (impurities[elements[i][0]][elements[j][0]]) {
                    elements[i][2]++; // number of impure relations
                }
            }
        }

        //Iteratively eliminate impurities till some solution (or no solution) is found
        final boolean[] eliminated = new boolean[this.numVars];
        for (int i = 0; i < elements.length; i++) {
            eliminated[elements[i][0]] = impurities[elements[i][0]][elements[i][0]];
        }
//        while (!validSolution(elements, eliminated)) {
//            //Sort them in the descending order of number of impurities
//            //(heuristic to avoid exponential search)
//            sortByImpurityPriority(elements, partitionCount, eliminated);
//            //for (int i = 0; i < elements.length; i++)
//            //  if (elements[i][2] > 0)
//            printlnMessage("-- Eliminating " +
//                    this.tetradTest.getVarNames()[elements[0][0]]);
//            eliminated[elements[0][0]] = true;
//            for (int i = 0; i < elements.length; i++) {
//                if (impurities[elements[i][0]][elements[0][0]]) {
//                    elements[i][2]--;
//                }
//            }
//            partitionCount[elements[0][1]]--;
//        }
        return buildSolution2(elements, eliminated, partition);
    }

    private int sizeCluster(final List cluster) {
        int total = 0;
        final Iterator it = cluster.iterator();
        while (it.hasNext()) {
            final int[] next = (int[]) it.next();
            total += next.length;
        }
        return total;
    }

    private List buildSolution2(final int[][] elements, final boolean[] eliminated,
                                final List partition) {
        final List solution = new ArrayList();
        final Iterator it = partition.iterator();
        while (it.hasNext()) {
            final int[] next = (int[]) it.next();
            final int[] draftArea = new int[next.length];
            int draftCount = 0;
            for (int i = 0; i < next.length; i++) {
                for (int j = 0; j < elements.length; j++) {
                    if (elements[j][0] == next[i] &&
                            !eliminated[elements[j][0]]) {
                        draftArea[draftCount++] = next[i];
                    }
                }
            }
            if (draftCount > 0) {
                final int[] realCluster = new int[draftCount];
                System.arraycopy(draftArea, 0, realCluster, 0, draftCount);
                solution.add(realCluster);
            }
        }
        if (solution.size() > 0) {
            return solution;
        } else {
            return null;
        }
    }

    private double impurityScoreSearch(final double initialScore) {
        double score, nextScore = initialScore;
        final boolean[] changed = new boolean[1];
        do {
            changed[0] = false;
            score = nextScore;
            nextScore = addImpuritySearch(score, changed);
            if (changed[0]) {
                changed[0] = false;
                nextScore = deleteImpuritySearch(nextScore, changed);
            }
        } while (changed[0]);
        return score;
    }

    private double addImpuritySearch(final double initialScore, final boolean[] changed) {
        double score, nextScore = initialScore;
        int choiceType = -1;
        do {
            score = nextScore;
            int bestChoice1 = -1, bestChoice2 = -1;

            for (int i = 0; i < this.numObserved; i++) {

                //Add latent->indicator edges
                //NOTE: code deactivated. Seems not to be worthy trying.
                /*for (int j = 0; j < numLatent; j++)
                    if (!latentParent[i][j]) {
                        latentParent[i][j] = true;
                        double newScore = scoreCandidate();
                        if (newScore > nextScore) {
                            nextScore = newScore;
                            bestChoice1 = i;
                            bestChoice2 = j;
                            choiceType  = 0;
                        }
                        latentParent[i][j] = false;
                    }*/

                for (int j = i + 1; j < this.numObserved; j++) {

                    //Check if one should ignore the possibility of an impurity for this pair
                    if (forbiddenImpurity(this.measuredNodes.get(i).toString(),
                            this.measuredNodes.get(j).toString())) {
                        continue;
                    }

                    //indicator -> indicator edges (children of the same latent parent)
                    //NOTE: code deactivated. Seems not to be worthy trying.
                    //      Here, I am not checking for cycles, and edges are considered only in one direction
                    /*if (!correlatedErrors[i][j] && !observedParent[i][j] && !observedParent[j][i]
                            && clusterId[i] == clusterId[j]) { //
                        //Check if they have the same latent parent
                        observedParent[i][j] = true;
                        double newScore = scoreCandidate();
                        //System.out.println("Trying impurity " + i + " --> " + j + " (Score = " + newScore + ")"); //System.exit(0);
                        if (newScore > nextScore) {
                            nextScore = newScore;
                            bestChoice1 = i;
                            bestChoice2 = j;
                            choiceType = 1;
                        }
                        observedParent[i][j] = false;
                    }*/

                    //indicator <-> indicator edges
                    if (!this.correlatedErrors[i][j] && !this.observedParent[i][j] &&
                            !this.observedParent[j][i]) {
                        this.correlatedErrors[i][j] = this.correlatedErrors[j][i] = true;
                        final double newScore = scoreCandidate();
                        System.out.println("Trying impurity " + i + " <--> " + j + " (Score = " + newScore + ")"); //System.exit(0);
                        if (newScore > nextScore) {
                            nextScore = newScore;
                            bestChoice1 = i;
                            bestChoice2 = j;
                            choiceType = 2;
                        }
                        this.correlatedErrors[i][j] = this.correlatedErrors[j][i] = false;
                    }

                }
            }
            if (bestChoice1 != -1) {
                this.modifiedGraph = true;
                switch (choiceType) {
                    case 0:
                        this.latentParent[bestChoice1][bestChoice2] = true;
                        System.out.println(
                                "****************************Added impurity: " +
                                        this.latentNodes.get(
                                                bestChoice2).toString() +
                                        " --> " + this.measuredNodes.get(
                                        bestChoice1).toString() + " " +
                                        nextScore);
                        break;
                    case 1:
                        this.observedParent[bestChoice1][bestChoice2] = true;
                        System.out.println(
                                "****************************Added impurity: " +
                                        this.measuredNodes.get(
                                                bestChoice2).toString() +
                                        " --> " + this.measuredNodes.get(
                                        bestChoice1).toString() + " " +
                                        nextScore);
                        break;
                    case 2:
                        System.out.println(
                                "****************************Added impurity: " +
                                        this.measuredNodes.get(
                                                bestChoice1).toString() +
                                        " <--> " + this.measuredNodes.get(
                                        bestChoice2).toString() + " " +
                                        nextScore);
                        this.correlatedErrors[bestChoice1][bestChoice2] =
                                this.correlatedErrors[bestChoice2][bestChoice1] =
                                        true;
                }
                changed[0] = true;
            }
        } while (score < nextScore);
        printlnMessage("End of addition round");
        return score;
    }

    private boolean forbiddenImpurity(final String name1, final String name2) {
        if (this.forbiddenList == null) {
            return false;
        }
        for (final Iterator it = this.forbiddenList.iterator(); it.hasNext(); ) {
            final Set nextPair = (Set) it.next();
            if (nextPair.contains(name1) && nextPair.contains(name2)) {
                return true;
            }
        }
        return false;
    }

    private double scoreCandidate() {
        final SemGraph graph = updatedGraph();
        initializeGaussianEM(graph);
        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm, this.covarianceMatrix);
        gaussianMaximization(semIm);

        try {
            System.out.println("trunk ll = " + semIm.getTruncLL());

            return -semIm.getTruncLL() - 0.5 * semIm.getNumFreeParams() *
                    Math.log(this.covarianceMatrix.getSampleSize());
        } catch (final IllegalArgumentException e) {
            return -Double.MAX_VALUE;
        }
    }

    private SemGraph updatedGraph() {
        final SemGraph output = new SemGraph(this.basicGraph);
        output.setShowErrorTerms(true);
        for (int i = 0; i < output.getNodes().size() - 1; i++) {
            final Node node1 = output.getNodes().get(i);
            if (node1.getNodeType() != NodeType.MEASURED) {
                continue;
            }
            for (int j = 0; j < output.getNodes().size(); j++) {
                final Node node2 = output.getNodes().get(j);
                if (node2.getNodeType() != NodeType.LATENT) {
                    continue;
                }
                final int pos1 = (Integer) this.observableNames.get(
                        output.getNodes().get(i).toString());
                final int pos2 = (Integer) this.latentNames.get(
                        output.getNodes().get(j).toString());
                if (this.latentParent[pos1][pos2] &&
                        output.getEdge(node1, node2) == null) {
                    output.addDirectedEdge(node2, node1);
                }
            }
            for (int j = i + 1; j < output.getNodes().size(); j++) {
                final Node node2 = output.getNodes().get(j);
                if (node2.getNodeType() != NodeType.MEASURED) {
                    continue;
                }
                final Node errnode1 = output.getErrorNode(output.getNodes().get(i));
                final Node errnode2 = output.getErrorNode(output.getNodes().get(j));
                final int pos1 = (Integer) this.observableNames.get(
                        output.getNodes().get(i).toString());
                final int pos2 = (Integer) this.observableNames.get(
                        output.getNodes().get(j).toString());
                if (this.correlatedErrors[pos1][pos2] &&
                        output.getEdge(errnode1, errnode2) == null) {
                    output.addBidirectedEdge(errnode1, errnode2);
                }
                if (this.observedParent[pos1][pos2] &&
                        output.getEdge(node1, node2) == null) {
                    output.addDirectedEdge(node2, node1);
                } else if (this.observedParent[pos2][pos1] &&
                        output.getEdge(node1, node2) == null) {
                    output.addDirectedEdge(node1, node2);
                }
            }
        }

        return output;
    }


    private double deleteImpuritySearch(final double initialScore,
                                        final boolean[] changed) {
        double score, nextScore = initialScore;
        int choiceType = -1;
        do {
            score = nextScore;
            int bestChoice1 = -1, bestChoice2 = -1;
            for (int i = 0; i < this.numObserved - 1; i++) {
                for (int j = i + 1; j < this.numObserved; j++) {
                    if (this.observedParent[i][j] || this.observedParent[j][i]) {
                        final boolean directionIJ = this.observedParent[i][j];
                        this.observedParent[i][j] = this.observedParent[j][i] = false;
                        final double newScore = scoreCandidate();
                        if (newScore > nextScore) {
                            nextScore = newScore;
                            bestChoice1 = i;
                            bestChoice2 = j;
                            choiceType = 0;
                        }
                        if (directionIJ) {
                            this.observedParent[i][j] = true;
                        } else {
                            this.observedParent[j][i] = true;
                        }
                    }
                    if (this.correlatedErrors[i][j]) {
                        this.correlatedErrors[i][j] = this.correlatedErrors[j][i] = false;
                        final double newScore = scoreCandidate();
                        if (newScore > nextScore) {
                            nextScore = newScore;
                            bestChoice1 = i;
                            bestChoice2 = j;
                            choiceType = 1;
                        }
                        this.correlatedErrors[i][j] = this.correlatedErrors[j][i] = true;
                    }
                }
            }
            if (bestChoice1 != -1) {
                this.modifiedGraph = true;
                switch (choiceType) {
                    case 0:
                        if (this.observedParent[bestChoice1][bestChoice2]) {
                            System.out.println(
                                    "****************************Removed impurity: " +
                                            this.measuredNodes.get(bestChoice2)
                                                    .toString() + " --> " +
                                            this.measuredNodes.get(bestChoice1)
                                                    .toString() + " " +
                                            nextScore);
                        } else {
                            System.out.println(
                                    "****************************Removed impurity: " +
                                            this.measuredNodes.get(bestChoice1)
                                                    .toString() + " --> " +
                                            this.measuredNodes.get(bestChoice2)
                                                    .toString() + " " +
                                            nextScore);
                        }
                        this.observedParent[bestChoice1][bestChoice2] =
                                this.observedParent[bestChoice2][bestChoice1] =
                                        false;
                        break;
                    case 1:
                        System.out.println(
                                "****************************Removed impurity: " +
                                        this.measuredNodes.get(
                                                bestChoice1).toString() +
                                        " <--> " + this.measuredNodes.get(
                                        bestChoice2).toString() + " " +
                                        nextScore);
                        this.correlatedErrors[bestChoice1][bestChoice2] =
                                this.correlatedErrors[bestChoice2][bestChoice1] =
                                        false;
                }
                changed[0] = true;
            }
        } while (score < nextScore);
        printlnMessage("End of deletion round");
        return score;
    }


    // The number of variables in cluster that have not been eliminated.

    private int numNotEliminated(final int[] cluster, final boolean[] eliminated) {
        int n1 = 0;
        for (int i = 0; i < cluster.length; i++) {
            if (!eliminated[cluster[i]]) {
                n1++;
            }
        }
        return n1;
    }
}


