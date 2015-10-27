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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.*;

import java.util.ArrayList;
import java.util.List;

public final class GesOptimizationEm implements TetradSerializable {
    static final long serialVersionUID = 23L;
    private static final double FUNC_TOLERANCE = 1.0e-4;
    private ArrayList<Node> nodes;
    private int numNodes;
    private TetradMatrix sampleCovar;
    private TetradMatrix edgeCoef;
    private TetradMatrix errorCovar;
    private TetradMatrix implCovar;
    private double logDetSample;
    private double[][] expectedCovariance;
    private Graph graph;

    // The following variables do not need to be serialized out, since they
    // are reset each time the optimize() method is called and are not
    // accessed otherwise.
    private transient double[][] yCov;
    private transient double[][] yCovModel;
    private transient double[][] yzCovModel;
    private transient double[][] zCovModel;
    private transient int numObserved;
    private transient int numLatent;
    private transient int[] idxLatent;
    private transient int[] idxObserved;
    private transient int[][] parents;
    private transient Node[] errorParent;
    private transient double[][] nodeParentsCov;
    private transient double[][][] parentsCov;
    private int sampleSize;

    //==============================CONSTRUCTORS==========================//

    /**
     * Blank constructor.
     */
    public GesOptimizationEm() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static SemOptimizerEm serializableInstance() {
        return new SemOptimizerEm();
    }

    public GesOptimizationEm(List<Node> nodes, TetradMatrix sampleCovar, int sampleSize) {
        this.nodes = new ArrayList<Node>(nodes);
        this.sampleSize = sampleSize;
        this.graph = new EdgeListGraph(this.nodes);
        this.numNodes = this.nodes.size();
        this.sampleCovar = sampleCovar;
        this.edgeCoef = new TetradMatrix(graph.getNumNodes(), graph.getNumNodes());
        this.errorCovar = TetradAlgebra.identity(graph.getNumNodes());
    }

    //=============================PUBLIC METHODS=========================//

    public void setGraph(Graph graph) {
        if (!graph.getNodes().equals(this.graph.getNodes())) {
            throw new IllegalArgumentException("Nodes of graph must be identical " +
                    "and in the same order.");
        }

        this.graph = graph;

//        for (Edge edge : graph.getEdges()) {
//            if (this.graph.getEdges(edge.getNode1(), edge.getNode2()) == null) {
//                int i = nodes.indexOf(edge.getNode1());
//                int j = nodes.indexOf(edge.getNode2());
//                edgeCoef.set(i, j, 1.0);
//            }
//        }
//
//        for (Edge edge : this.graph.getEdges()) {
//            if (graph.getEdges(edge.getNode1(), edge.getNode2()) == null) {
//                int i = nodes.indexOf(edge.getNode1());
//                int j = nodes.indexOf(edge.getNode2());
//                edgeCoef.set(i, j, 0.0);
//            }
//        }
    }

    public void optimize() {
//        if (getChiSquare() < getDof()) {
//            return Double.Na;
//        }

        this.edgeCoef = new TetradMatrix(graph.getNumNodes(), graph.getNumNodes());
        this.errorCovar = TetradAlgebra.identity(graph.getNumNodes());
        initialize();
        updateMatrices();
        double score, newScore = scoreSemIm();
        do {
            score = newScore;
//            System.out.println("FML = " + score + " Chisq = " + this.semIm.getChiSquare());
            expectation();
            maximization();
            updateMatrices();
            newScore = scoreSemIm();
        } while (newScore - score > FUNC_TOLERANCE);

//        return newScore;
    }

    /**
     * Fit the freeParameters by doing local regressions.
     */
    public void optimizeRegression() {
        List<Node> nodes = graph.getNodes();
        TetradAlgebra algebra = new TetradAlgebra();

//        TetradLogger.getInstance().log("info", "FML = " + semIm.getScore());

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.MEASURED) {
                continue;
            }

            int idx = nodes.indexOf(node);
            List<Node> parents = graph.getParents(node);
            Node errorParent = node;

            for (int i = 0; i < parents.size(); i++) {
                Node nextParent = parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    errorParent = nextParent;
                    parents.remove(nextParent);
                    break;
                }
            }

            double variance = sampleCovar.get(idx, idx);

            if (parents.size() > 0) {
                TetradVector nodeParentsCov = new TetradVector(parents.size());
                TetradMatrix parentsCov = new TetradMatrix(parents.size(), parents.size());

                for (int i = 0; i < parents.size(); i++) {
                    int idx2 = nodes.indexOf(parents.get(i));
                    nodeParentsCov.set(i, sampleCovar.get(idx, idx2));

                    for (int j = i; j < parents.size(); j++) {
                        int idx3 = nodes.indexOf(parents.get(j));
                        parentsCov.set(i, j, sampleCovar.get(idx2, idx3));
                        parentsCov.set(j, i, sampleCovar.get(idx2, idx3));
                    }
                }

                TetradVector edges = parentsCov.inverse().times(nodeParentsCov);

                for (int i = 0; i < edges.size(); i++) {
                    int idx2 = nodes.indexOf(parents.get(i));
//                    semIm.setParamValue(nodes.get(idx2), node,
//                            edges.get(i));
                    edgeCoef.set(idx2, idx, edges.get(i));
                }

                variance -= nodeParentsCov.dotProduct(edges);
            }

//            semIm.setParamValue(errorParent, errorParent, variance);
            errorCovar.set(idx, idx, variance);
            TetradLogger.getInstance().log("info", "FML = " + getFml());
        }

//        return -getScore();
    }

    public double[][] getExpectedCovarianceMatrix() {
        return this.expectedCovariance;
    }

    //==============================PRIVATE METHODS========================//

    private int getDof() {
        return (nodes.size() * (nodes.size() + 1)) / 2
                - (graph.getNumNodes() + graph.getNumEdges());
    }

    /**
     * Returns the chi square value for the model.
     */
    public double getChiSquare() {
        return (sampleSize - 1) * getFml();
    }

    private void initialize() {
        this.yCov = sampleCovar.toArray();
        this.numObserved = this.numLatent = 0;
        for (int i = 0; i < this.graph.getNodes().size(); i++) {
            Node node = this.graph.getNodes().get(i);
            if (node.getNodeType() == NodeType.LATENT) {
                this.numLatent++;
            }
            else if (node.getNodeType() == NodeType.MEASURED) {
                this.numObserved++;
            }
            else if (node.getNodeType() == NodeType.ERROR) {
                break;
            }
        }

        // If trying this on a model with no latents. -jdramsey
        if (numLatent == 0) numLatent = 1;

        this.idxLatent = new int[this.numLatent];
        this.idxObserved = new int[this.numObserved];
        int countLatent = 0, countObserved = 0;
        for (int i = 0; i < this.graph.getNodes().size(); i++) {
            Node node = this.graph.getNodes().get(i);
            if (node.getNodeType() == NodeType.LATENT) {
                this.idxLatent[countLatent++] = i;
            }
            else if (node.getNodeType() == NodeType.MEASURED) {
                this.idxObserved[countObserved++] = i;
            }
            else if (node.getNodeType() == NodeType.ERROR) {
                break;
            }
        }
        this.expectedCovariance = new double[this.numObserved + this.numLatent][
                this.numObserved + this.numLatent];
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = i; j < this.numObserved; j++) {
                this.expectedCovariance[this.idxObserved[i]][this.idxObserved[j]] =
                        this.expectedCovariance[this.idxObserved[j]][this.idxObserved[i]] =
                                this.yCov[i][j];
            }
        }
        this.yCovModel = new double[this.numObserved][this.numObserved];
        this.yzCovModel = new double[this.numObserved][this.numLatent];
        this.zCovModel = new double[this.numLatent][this.numLatent];


        this.parents = new int[this.numLatent + this.numObserved][];
        this.errorParent = new Node[this.numLatent + this.numObserved];
        this.nodeParentsCov = new double[this.numLatent + this.numObserved][];
        this.parentsCov = new double[this.numLatent + this.numObserved][][];
        for (Node node : this.graph.getNodes()) {
            if (node.getNodeType() == NodeType.ERROR) {
                break;
            }
            int idx = this.graph.getNodes().indexOf(node);
            List parents = this.graph.getParents(node);
            this.errorParent[idx] = node;
            for (int i = 0; i < parents.size(); i++) {
                Node nextParent = (Node) parents.get(i);
                if (nextParent.getNodeType() == NodeType.ERROR) {
                    this.errorParent[idx] = nextParent;
                    parents.remove(nextParent);
                    break;
                }
            }
            if (parents.size() > 0) {
                this.parents[idx] = new int[parents.size()];
                this.nodeParentsCov[idx] = new double[parents.size()];
                this.parentsCov[idx] =
                        new double[parents.size()][parents.size()];
                for (int i = 0; i < parents.size(); i++) {
                    this.parents[idx][i] =
                            this.graph.getNodes().indexOf(parents.get(i));
                }
            }
            else {
                this.parents[idx] = null;
            }
        }
    }

    private void expectation() {
        double delta[][] = MatrixUtils.product(
                MatrixUtils.inverse(this.yCovModel), this.yzCovModel);
        double Delta[][] = MatrixUtils.subtract(this.zCovModel,
                MatrixUtils.product(MatrixUtils.transpose(this.yzCovModel),
                        delta));
        double yzE[][] = MatrixUtils.product(this.yCov, delta);
        double zzE[][] = MatrixUtils.sum(MatrixUtils.product(
                MatrixUtils.product(MatrixUtils.transpose(delta), this.yCov),
                delta), Delta);
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = i; j < this.numLatent; j++) {
                this.expectedCovariance[this.idxLatent[i]][this.idxLatent[j]] =
                        this.expectedCovariance[this.idxLatent[j]][this.idxLatent[i]] =
                                zzE[i][j];
            }
            for (int j = 0; j < this.numObserved; j++) {
                this.expectedCovariance[this.idxLatent[i]][this.idxObserved[j]] =
                        this.expectedCovariance[this.idxObserved[j]][this.idxLatent[i]] =
                                yzE[j][i];
            }
        }
    }

    private void maximization() {
        for (Node node : this.graph.getNodes()) {
            if (node.getNodeType() == NodeType.ERROR) {
                break;
            }
            int idx = this.graph.getNodes().indexOf(node);
            double variance = this.expectedCovariance[idx][idx];
            if (this.parents[idx] != null) {
                for (int i = 0; i < this.parents[idx].length; i++) {
                    int idx2 = this.parents[idx][i];
                    this.nodeParentsCov[idx][i] =
                            this.expectedCovariance[idx][idx2];
                    for (int j = i; j < this.parents[idx].length; j++) {
                        int idx3 = this.parents[idx][j];
                        this.parentsCov[idx][i][j] =
                                this.parentsCov[idx][j][i] =
                                        this.expectedCovariance[idx2][idx3];
                    }
                }
                double edges[] = MatrixUtils.product(
                        MatrixUtils.inverse(this.parentsCov[idx]),
                        this.nodeParentsCov[idx]);
                for (int i = 0; i < edges.length; i++) {
                    int idx2 = this.parents[idx][i];
                    try {
                        edgeCoef.set(idx2, idx, edges[i]);

//                        this.semIm.setParamValue(
//                                this.graph.getNodes().get(idx2), node,
//                                edges[i]);
                    }
                    catch (IllegalArgumentException e) {
                        //Dont't do anything: it is just a fixed parameter
                    }
                }
                variance -= MatrixUtils.innerProduct(this.nodeParentsCov[idx],
                        edges);
            }
            try {
                errorCovar.set(idx, idx, variance);

//                this.semIm.setParamValue(this.errorParent[idx],
//                        this.errorParent[idx], variance);
            }
            catch (IllegalArgumentException e) {
                //Don't do anything: it is just a fixed parameter
            }
        }
    }

    private void updateMatrices() {
        computeImpliedCovar();
        double impliedCovar[][] = implCovar.toArray();
        for (int i = 0; i < this.numObserved; i++) {
            for (int j = i; j < this.numObserved; j++) {
                this.yCovModel[i][j] = this.yCovModel[j][i] =
                        impliedCovar[this.idxObserved[i]][this.idxObserved[j]];
            }
            for (int j = 0; j < this.numLatent; j++) {
                this.yzCovModel[i][j] =
                        impliedCovar[this.idxObserved[i]][this.idxLatent[j]];
            }
        }
        for (int i = 0; i < this.numLatent; i++) {
            for (int j = i; j < this.numLatent; j++) {
                this.zCovModel[i][j] = this.zCovModel[j][i] =
                        impliedCovar[this.idxLatent[i]][this.idxLatent[j]];
            }
        }
    }

    private double scoreSemIm() {
//        return -this.semIm.getNegTruncLL();
        double score = getFml();

        if (Double.isNaN(score)) {
            score = Double.POSITIVE_INFINITY;
        }

        TetradLogger.getInstance().log("info", "FML = " + (score));
        return -score;
    }

    /**
     * The value of the maximum likelihood function for the getModel the model
     * (Bollen 107). To optimize, this should be minimized.
     */
    public double getFml() {
        try {
            computeImpliedCovar();
        } catch (Exception e) {
            e.printStackTrace();
            return Double.NaN;
        }

        return logDet(implCovar) +
                traceSSigmaInv(sampleCovar, implCovar) -
                logDetSample() - numNodes;
    }

    /**
     * Computes the implied covariance matrices of the Sem. There are two:
     * <code>implCovar </code> contains the covariances of all the variables and
     * <code>implCovarMeas</code> contains covariance for the measured variables
     * only.
     */
    private void computeImpliedCovar() {
        TetradMatrix edgeCoefT = edgeCoef.transpose();

        // Note. Since the sizes of the temp matrices in this calculation
        // never change, we ought to be able to reuse them.
        this.implCovar = MatrixUtils.impliedCovar(edgeCoefT, errorCovar);
    }

    private double logDet(TetradMatrix implCovarMeas) {
        return Math.log(implCovarMeas.det());
    }

    private double traceSSigmaInv(TetradMatrix s,
                                  TetradMatrix sigma) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        TetradMatrix inverse = sigma.inverse();
        TetradMatrix product = s.times(inverse);

        double v = product.trace();

        if (v < -1e-8) {
            throw new IllegalArgumentException("Trace was negative.");
        }

        return v;
    }

    private double logDetSample() {
        if (logDetSample == 0.0 && sampleCovar != null) {
            double det = sampleCovar.det();
            logDetSample = Math.log(det);
        }

        return logDetSample;
    }
}



