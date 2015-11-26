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

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Mult;
import cern.jet.math.PlusMult;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.MatrixUtils;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Implements ICF as specified in Drton and Richardson (2003), Iterative Conditional Fitting for Gaussian Ancestral
 * Graph Models, using hints from previous implementations by Drton in the ggm package in R and by Silva in the Purify
 * class. The reason for reimplementing in this case is to take advantage of linear algebra optimizations in the COLT
 * library.
 *
 * @author Joseph Ramsey
 */
public class Ricf {
    private ICovarianceMatrix covMatrix;

    //==============================CONSTRUCTORS==========================//

    public Ricf() {
    }

    //=============================PUBLIC METHODS=========================//

    public RicfResult ricf(SemGraph mag, ICovarianceMatrix covMatrix, double tolerance) {
        this.covMatrix = covMatrix;
        mag.setShowErrorTerms(false);

        DoubleFactory2D factory = DoubleFactory2D.dense;
        Algebra algebra = new Algebra();

        DoubleMatrix2D S = new DenseDoubleMatrix2D(covMatrix.getMatrix().toArray());
        int p = covMatrix.getDimension();

        if (p == 1) {
            return new RicfResult(S, S, null, null, 1, Double.NaN, covMatrix);
        }

        List<Node> nodes = new ArrayList<Node>();

        for (String name : covMatrix.getVariableNames()) {
            nodes.add(mag.getNode(name));
        }

        DoubleMatrix2D omega = factory.diagonal(factory.diagonal(S));
        DoubleMatrix2D B = factory.identity(p);

        int[] ug = ugNodes(mag, nodes);
        int[] ugComp = complement(p, ug);

        if (ug.length > 0) {
            List<Node> _ugNodes = new LinkedList<Node>();

            for (int i : ug) {
                _ugNodes.add(nodes.get(i));
            }

            Graph ugGraph = mag.subgraph(_ugNodes);
            ICovarianceMatrix ugCov = covMatrix.getSubmatrix(ug);
            DoubleMatrix2D lambdaInv = fitConGraph(ugGraph, ugCov, p + 1, tolerance).shat;
            omega.viewSelection(ug, ug).assign(lambdaInv);
        }

        // Prepare lists of parents and spouses.
        int[][] pars = parentIndices(p, mag, nodes);
        int[][] spo = spouseIndices(p, mag, nodes);

        int i = 0;
        double _diff;

        while (true) {
            i++;

            DoubleMatrix2D omegaOld = omega.copy();
            DoubleMatrix2D bOld = B.copy();

            for (int _v = 0; _v < p; _v++) { // Need to exclude the UG part.

                // Exclude the UG part.
                if (Arrays.binarySearch(ug, _v) >= 0) {
                    continue;
                }

                int[] v = new int[]{_v};
                int[] vcomp = complement(p, v);
                int[] all = range(0, p - 1);
                int[] parv = pars[_v];
                int[] spov = spo[_v];

                DoubleMatrix2D a6 = B.viewSelection(v, parv);
                if (spov.length == 0) {
                    if (parv.length != 0) {
                        if (i == 1) {
                            DoubleMatrix2D a1 = S.viewSelection(parv, parv);
                            DoubleMatrix2D a2 = S.viewSelection(v, parv);
                            DoubleMatrix2D a3 = algebra.inverse(a1);
                            DoubleMatrix2D a4 = algebra.mult(a2, a3);
                            a4.assign(Mult.mult(-1));
                            a6.assign(a4);

                            DoubleMatrix2D a7 = S.viewSelection(parv, v);
                            DoubleMatrix2D a9 = algebra.mult(a6, a7);
                            DoubleMatrix2D a8 = S.viewSelection(v, v);
                            DoubleMatrix2D a8b = omega.viewSelection(v, v);
                            a8b.assign(a8);
                            omega.viewSelection(v, v).assign(a9, PlusMult.plusMult(1));
                        }
                    }
                } else {
                    if (parv.length != 0) {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

                        DoubleMatrix2D Z = algebra.mult(oInv.viewSelection(spov, vcomp),
                                B.viewSelection(vcomp, all));

                        int lpa = parv.length;
                        int lspo = spov.length;

                        // Build XX
                        DoubleMatrix2D XX = new DenseDoubleMatrix2D(lpa + lspo, lpa + lspo);
                        int[] range1 = range(0, lpa - 1);
                        int[] range2 = range(lpa, lpa + lspo - 1);

                        // Upper left quadrant
                        XX.viewSelection(range1, range1).assign(S.viewSelection(parv, parv));

                        // Upper right quadrant
                        DoubleMatrix2D a11 = algebra.mult(S.viewSelection(parv, all),
                                algebra.transpose(Z));
                        XX.viewSelection(range1, range2).assign(a11);

                        // Lower left quadrant
                        DoubleMatrix2D a12 = XX.viewSelection(range2, range1);
                        DoubleMatrix2D a13 = algebra.transpose(XX.viewSelection(range1, range2));
                        a12.assign(a13);

                        // Lower right quadrant
                        DoubleMatrix2D a14 = XX.viewSelection(range2, range2);
                        DoubleMatrix2D a15 = algebra.mult(Z, S);
                        DoubleMatrix2D a16 = algebra.mult(a15, algebra.transpose(Z));
                        a14.assign(a16);

                        // Build XY
                        DoubleMatrix1D YX = new DenseDoubleMatrix1D(lpa + lspo);
                        DoubleMatrix1D a17 = YX.viewSelection(range1);
                        DoubleMatrix1D a18 = S.viewSelection(v, parv).viewRow(0);
                        a17.assign(a18);

                        DoubleMatrix1D a19 = YX.viewSelection(range2);
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix1D a21 = algebra.mult(a20, algebra.transpose(Z)).viewRow(0);
                        a19.assign(a21);

                        // Temp
                        DoubleMatrix2D a22 = algebra.inverse(XX);
                        DoubleMatrix1D temp = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to b.
                        DoubleMatrix1D a23 = a6.viewRow(0);
                        DoubleMatrix1D a24 = temp.viewSelection(range1);
                        a23.assign(a24);
                        a23.assign(Mult.mult(-1));

                        // Assign to omega.
                        omega.viewSelection(v, spov).viewRow(0).assign(temp.viewSelection(range2));
                        omega.viewSelection(spov, v).viewColumn(0).assign(temp.viewSelection(range2));

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(temp, YX);
                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.viewSelection(v, v).assign(tempVar);
                        omega.viewSelection(v, v).assign(a31, PlusMult.plusMult(1));
                    } else {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

//                        System.out.println("O.inv = " + oInv);

                        DoubleMatrix2D a4 = oInv.viewSelection(spov, vcomp);
                        DoubleMatrix2D a5 = B.viewSelection(vcomp, all);
                        DoubleMatrix2D Z = algebra.mult(a4, a5);

//                        System.out.println("Z = " + Z);

                        // Build XX
                        DoubleMatrix2D XX = algebra.mult(algebra.mult(Z, S), Z.viewDice());

//                        System.out.println("XX = " + XX);

                        // Build XY
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix1D YX = algebra.mult(a20, Z.viewDice()).viewRow(0);

//                        System.out.println("YX = " + YX);

                        // Temp
                        DoubleMatrix2D a22 = algebra.inverse(XX);
                        DoubleMatrix1D a23 = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to omega.
                        DoubleMatrix1D a24 = omega.viewSelection(v, spov).viewRow(0);
                        a24.assign(a23);
                        DoubleMatrix1D a25 = omega.viewSelection(spov, v).viewColumn(0);
                        a25.assign(a23);

//                        System.out.println("Omega 2 " + omega);

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(a24, YX);

//                        System.out.println("tempVar = " + tempVar);

                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.set(_v, _v, tempVar + a31.get(0, 0));

//                        System.out.println("Omega final " + omega);
                    }
                }
            }

            DoubleMatrix2D a32 = omega.copy();
            a32.assign(omegaOld, PlusMult.plusMult(-1));
            double diff1 = algebra.norm1(a32);

            DoubleMatrix2D a33 = B.copy();
            a33.assign(bOld, PlusMult.plusMult(-1));
            double diff2 = algebra.norm1(a32);

            double diff = diff1 + diff2;
            _diff = diff;

            if (diff < tolerance) break;
        }

        DoubleMatrix2D a34 = algebra.inverse(B);
        DoubleMatrix2D a35 = algebra.inverse(B.viewDice());
        DoubleMatrix2D sigmahat = algebra.mult(algebra.mult(a34, omega), a35);

        DoubleMatrix2D lambdahat = omega.copy();
        DoubleMatrix2D a36 = lambdahat.viewSelection(ugComp, ugComp);
        a36.assign(factory.make(ugComp.length, ugComp.length, 0.0));

        DoubleMatrix2D omegahat = omega.copy();
        DoubleMatrix2D a37 = omegahat.viewSelection(ug, ug);
        a37.assign(factory.make(ug.length, ug.length, 0.0));

        DoubleMatrix2D bhat = B.copy();

        return new RicfResult(sigmahat, lambdahat, bhat, omegahat, i, _diff, covMatrix);
    }

    /**
     * @return an enumeration of the cliques of the given graph considered as undirected.
     */
    public List<List<Node>> cliques(Graph graph) {
        List<Node> nodes = graph.getNodes();
        List<List<Node>> cliques = new ArrayList<List<Node>>();

        for (int i = 0; i < nodes.size(); i++) {
            List<Node> adj = graph.getAdjacentNodes(nodes.get(i));

            SortedSet<Integer> L1 = new TreeSet<Integer>();
            L1.add(i);

            SortedSet<Integer> L2 = new TreeSet<Integer>();

            for (Node _adj : adj) {
                L2.add(nodes.indexOf(_adj));
            }

            int moved = -1;

            while (true) {
                addNodesToRight(L1, L2, graph, nodes, moved);

                if (isMaximal(L1, L2, graph, nodes)) {
                    record(L1, cliques, nodes);
                }

                moved = moveLastBack(L1, L2);

                if (moved == -1) {
                    break;
                }
            }
        }

        return cliques;
    }

    /**
     * Fits a concentration graph. Coding algorithm #2 only.
     */
    public FitConGraphResult fitConGraph(Graph graph, ICovarianceMatrix cov, int n, double tol) {
        DoubleFactory2D factory = DoubleFactory2D.dense;
        Algebra algebra = new Algebra();

        List<Node> nodes = graph.getNodes();
        String[] nodeNames = new String[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (!cov.getVariableNames().contains(node.getName())) {
                throw new IllegalArgumentException("Node in graph not in cov matrix: " + node);
            }

            nodeNames[i] = node.getName();
        }

        DoubleMatrix2D S = new DenseDoubleMatrix2D(cov.getSubmatrix(nodeNames).getMatrix().toArray());
        graph = graph.subgraph(nodes);

        List<List<Node>> cli = cliques(graph);

        int nc = cli.size();

        if (nc == 1) {
            return new FitConGraphResult(S, 0, 0, 1);
        }

        int k = S.rows();
        int it = 0;

        // Only coding alg #2 here.
        DoubleMatrix2D K = algebra.inverse(factory.diagonal(factory.diagonal(S)));

        int[] all = range(0, k - 1);

        while (true) {
            DoubleMatrix2D KOld = K.copy();
            it++;

            for (int i = 0; i < nc; i++) {
                int[] a = asIndices(cli.get(i), nodes);
                int[] b = complement(all, a);
                DoubleMatrix2D a1 = S.viewSelection(a, a);
                DoubleMatrix2D a2 = algebra.inverse(a1);
                DoubleMatrix2D a3 = K.viewSelection(a, b);
                DoubleMatrix2D a4 = K.viewSelection(b, b);
                DoubleMatrix2D a5 = algebra.inverse(a4);
                DoubleMatrix2D a6 = K.viewSelection(b, a).copy();
                DoubleMatrix2D a7 = algebra.mult(a3, a5);
                DoubleMatrix2D a8 = algebra.mult(a7, a6);
                a2.assign(a8, PlusMult.plusMult(1));
                DoubleMatrix2D a9 = K.viewSelection(a, a);
                a9.assign(a2);
            }

            DoubleMatrix2D a32 = K.copy();
            a32.assign(KOld, PlusMult.plusMult(-1));
            double diff = algebra.norm1(a32);

            System.out.println(diff);

            if (diff < tol) break;
        }

        DoubleMatrix2D V = algebra.inverse(K);

        int numNodes = graph.getNumNodes();
        int df = numNodes * (numNodes - 1) / 2 - graph.getNumEdges();
        double dev = lik(algebra.inverse(V), S, n, k);

        return new FitConGraphResult(V, dev, df, it);
    }

    private int[] asIndices(List<Node> clique, List<Node> nodes) {
        int[] a = new int[clique.size()];

        for (int j = 0; j < clique.size(); j++) {
            a[j] = nodes.indexOf(clique.get(j));
        }

        return a;
    }

    private double lik(DoubleMatrix2D K, DoubleMatrix2D S, int n, int k) {
        Algebra algebra = new Algebra();
        DoubleMatrix2D SK = algebra.mult(S, K);
        return (algebra.trace(SK) - Math.log(algebra.det(SK)) - k) * n;
    }

    //==============================PRIVATE METHODS=======================//

    private int[] range(int from, int to) {
        if (from < 0 || to < 0 || from > to) {
            throw new IllegalArgumentException();
        }

        int[] range = new int[to - from + 1];

        for (int k = from; k <= to; k++) {
            range[k - from] = k;
        }

        return range;
    }

    private int[] complement(int p, int[] a) {
        Arrays.sort(a);
        int[] vcomp = new int[p - a.length];

        int k = -1;

        for (int j = 0; j < p; j++) {
            if (Arrays.binarySearch(a, j) >= 0) continue;
            vcomp[++k] = j;
        }

        return vcomp;
    }

    private int[] complement(int all[], int[] remove) {
        Arrays.sort(remove);
        int[] vcomp = new int[all.length - remove.length];

        int k = -1;

        for (int j = 0; j < all.length; j++) {
            if (Arrays.binarySearch(remove, j) >= 0) continue;
            vcomp[++k] = j;
        }

        return vcomp;
    }


    private int[] ugNodes(Graph mag, List<Node> nodes) {
        List<Node> ugNodes = new LinkedList<Node>();

        for (Node node : nodes) {
            if (mag.getNodesInTo(node, Endpoint.ARROW).size() == 0) {
                ugNodes.add(node);
            }
        }

        int[] indices = new int[ugNodes.size()];

        for (int j = 0; j < ugNodes.size(); j++) {
            indices[j] = nodes.indexOf(ugNodes.get(j));
        }

        return indices;
    }

    private int[][] parentIndices(int p, Graph mag, List<Node> nodes) {
        int[][] pars = new int[p][];

        for (int i = 0; i < p; i++) {
            List<Node> parents = mag.getParents(nodes.get(i));
            int[] indices = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                indices[j] = nodes.indexOf(parents.get(j));
            }

            pars[i] = indices;
        }

        return pars;
    }

    private int[][] spouseIndices(int p, Graph mag, List<Node> nodes) {
        int[][] spo = new int[p][];

        for (int i = 0; i < p; i++) {
            List<Node> list1 = mag.getNodesOutTo(nodes.get(i), Endpoint.ARROW);
            List<Node> list2 = mag.getNodesInTo(nodes.get(i), Endpoint.ARROW);
            list1.retainAll(list2);

            List<Node> list3 = new LinkedList<Node>(nodes);
            list3.removeAll(list1);

            int[] indices = new int[list1.size()];

            for (int j = 0; j < list1.size(); j++) {
                indices[j] = nodes.indexOf(list1.get(j));
            }

            spo[i] = indices;
        }

        return spo;
    }


    private int moveLastBack(SortedSet<Integer> L1, SortedSet<Integer> L2) {
        if (L1.size() == 1) {
            return -1;
        }

        int moved = L1.last();
        L1.remove(moved);
        L2.add(moved);

        return moved;
    }

    /**
     * If L2 is nonempty, moves nodes from L2 to L1 that can be added to L1. Nodes less than max(L1) are not
     * considered--i.e. L1 is being extended to the right. Nodes not greater than the most recently moved node are not
     * considered--this is a mechanism for
     */
    private void addNodesToRight(SortedSet<Integer> L1, SortedSet<Integer> L2,
                                 Graph graph, List<Node> nodes, int moved) {
        for (int j : new TreeSet<Integer>(L2)) {
            if (j > max(L1) && j > moved && addable(j, L1, graph, nodes)) {
                L1.add(j);
                L2.remove(j);
            }
        }
    }

    private void record(SortedSet<Integer> L1, List<List<Node>> cliques,
                        List<Node> nodes) {
        List<Node> clique = new LinkedList<Node>();

        for (int i : L1) {
            clique.add(nodes.get(i));
        }

        cliques.add(clique);
    }

    private boolean isMaximal(SortedSet<Integer> L1, SortedSet<Integer> L2, Graph graph, List<Node> nodes) {
        for (int j : L2) {
            if (addable(j, L1, graph, nodes)) {
                return false;
            }
        }

        return true;
    }

    private int max(SortedSet<Integer> L1) {
        int max = Integer.MIN_VALUE;

        for (int i : L1) {
            if (i > max) {
                max = i;
            }
        }

        return max;
    }

    /**
     * @return true if j is adjacent to all the nodes in l1.
     */
    private boolean addable(int j, SortedSet<Integer> L1, Graph graph, List<Node> nodes) {
        for (int k : L1) {
            if (!graph.isAdjacentTo(nodes.get(j), nodes.get(k))) {
                return false;
            }
        }

        return true;
    }

    //==============================PUBLIC CLASSES==========================//

    public static class RicfResult {
        private final ICovarianceMatrix covMatrix;
        private DoubleMatrix2D shat;
        private DoubleMatrix2D lhat;
        private DoubleMatrix2D bhat;
        private DoubleMatrix2D ohat;
        private int iterations;
        private double diff;

        public RicfResult(DoubleMatrix2D shat, DoubleMatrix2D lhat, DoubleMatrix2D bhat,
                          DoubleMatrix2D ohat, int iterations, double diff, ICovarianceMatrix covMatrix) {
            this.shat = shat;
            this.lhat = lhat;
            this.bhat = bhat;
            this.ohat = ohat;
            this.iterations = iterations;
            this.diff = diff;
            this.covMatrix = covMatrix;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();

            buf.append("\nSigma hat\n");
            buf.append(MatrixUtils.toStringSquare(getShat().toArray(), new DecimalFormat("0.0000"), covMatrix.getVariableNames()));

            buf.append("\n\nLambda hat\n");
            buf.append(MatrixUtils.toStringSquare(getLhat().toArray(), new DecimalFormat("0.0000"), covMatrix.getVariableNames()));

            buf.append("\n\nBeta hat\n");
            buf.append(MatrixUtils.toStringSquare(getBhat().toArray(), new DecimalFormat("0.0000"), covMatrix.getVariableNames()));

            buf.append("\n\nOmega hat\n");
            buf.append(MatrixUtils.toStringSquare(getOhat().toArray(), new DecimalFormat("0.0000"), covMatrix.getVariableNames()));

            buf.append("\n\nIterations\n");
            buf.append(getIterations());

            buf.append("\n\ndiff = " + diff);

            return buf.toString();
        }

        public DoubleMatrix2D getShat() {
            return shat;
        }

        public DoubleMatrix2D getLhat() {
            return lhat;
        }

        public DoubleMatrix2D getBhat() {
            return bhat;
        }

        public DoubleMatrix2D getOhat() {
            return ohat;
        }

        public int getIterations() {
            return iterations;
        }
    }

    public static class FitConGraphResult {
        private DoubleMatrix2D shat;
        double deviance;
        int df;
        int iterations;

        public FitConGraphResult(DoubleMatrix2D shat, double deviance,
                                 int df, int iterations) {
            this.shat = shat;
            this.deviance = deviance;
            this.df = df;
            this.iterations = iterations;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();

            buf.append("\nSigma hat\n");
            buf.append(shat);

            buf.append("\nDeviance\n");
            buf.append(deviance);

            buf.append("\nDf\n");
            buf.append(df);

            buf.append("\nIterations\n");
            buf.append(iterations);

            return buf.toString();
        }
    }
}



