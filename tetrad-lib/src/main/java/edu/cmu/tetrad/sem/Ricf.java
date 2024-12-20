/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.MView;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Implements ICF as specified in Drton and Richardson (2003), Iterative Conditional Fitting for Gaussian Ancestral
 * Graph Models, using hints from previous implementations by Drton in the ggm package in R and by Silva in the Purify
 * class. The reason for reimplementing in this case is to take advantage of linear algebra optimizations in the COLT
 * library.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Ricf {

    /**
     * Represents the Ricf class. This class provides methods for calculating the Restricted Information Criterion
     * Fusion (RICF) for a given SemGraph.
     */
    public Ricf() {
    }

    /**
     * Calculates the Restricted Information Criterion Fusion (RICF) for a given SemGraph.
     *
     * @param mag       The SemGraph object representing the graph to calculate RICF for.
     * @param covMatrix The ICovarianceMatrix object representing the covariance matrix.
     * @param tolerance The tolerance value for convergence.
     * @return The RicfResult object containing the results of the RICF calculation.
     */
    public RicfResult ricf(SemGraph mag, ICovarianceMatrix covMatrix, double tolerance) {
        mag.setShowErrorTerms(false);

        Matrix S = new Matrix(covMatrix.getMatrix().toArray());
        int p = covMatrix.getDimension();

        if (p == 1) {
            return new RicfResult(S, S, null, null, 1, Double.NaN, covMatrix);
        }

        List<Node> nodes = new ArrayList<>();

        for (String name : covMatrix.getVariableNames()) {
            nodes.add(mag.getNode(name));
        }

        Matrix omega = Vector.diag(S.diag());
        Matrix B = Matrix.identity(p);

        int[] ug = ugNodes(mag, nodes);
        int[] ugComp = complement(p, ug);

        if (ug.length > 0) {
            List<Node> _ugNodes = new LinkedList<>();

            for (int i : ug) {
                _ugNodes.add(nodes.get(i));
            }

            Graph ugGraph = mag.subgraph(_ugNodes);
            ICovarianceMatrix ugCov = covMatrix.getSubmatrix(ug);
            Matrix lambdaInv = (fitConGraph(ugGraph, ugCov, p + 1, tolerance).shat);
            omega.view(ug, ug).set(lambdaInv);
        }

        // Prepare lists of parents and spouses.
        int[][] pars = parentIndices(p, mag, nodes);
        int[][] spo = spouseIndices(p, mag, nodes);

//        for (int[] par : pars) {
//            System.out.println(Arrays.toString(par));
//        }
//
//        for (int[] sp : spo) {
//            System.out.println(Arrays.toString(sp));
//        }

        int i = 0;
        double _diff;

        while (true) {
            i++;

            Matrix omegaOld = omega.copy();
            Matrix bOld = B.copy();

            for (int _v = 0; _v < p; _v++) { // Need to exclude the UG part.

                // Exclude the UG part.
                if (Arrays.binarySearch(ug, _v) >= 0) {
                    continue;
                }

                int[] v = {_v};
                int[] vcomp = complement(p, v);
                int[] all = range(0, p - 1);
                int[] parv = pars[_v];
                int[] spov = spo[_v];

                MView a6 = B.view(v, parv);
                if (spov.length == 0) {
                    if (parv.length != 0) {
                        if (i == 1) {
                            MView a1 = S.view(parv, parv);
                            MView a2 = S.view(v, parv);
                            Matrix a3 = a1.mat().inverse();
                            Matrix a4 = a2.mat().times(a3).scale(-1);
                            a6.set(a4);

                            Matrix a7 = S.view(parv, v).mat();
                            Matrix a9 = a6.mat().times(a7);
                            Matrix a8 = S.view(v, v).mat();
                            MView a8b = omega.view(v, v);
                            a8b.set(a8);

                            omega.view(v, v).set(omega.view(v, v).mat().plus(a9));
                        }
                    }
                } else {
                    if (parv.length != 0) {
                        Matrix oInv = new Matrix(p, p);
                        Matrix a2 = omega.view(vcomp, vcomp).mat();
                        Matrix a3 = a2.inverse();
                        oInv.view(vcomp, vcomp).set(a3);

                        Matrix Z = oInv.view(spov, vcomp).mat().times(B.view(vcomp, all).mat());

                        int lpa = parv.length;
                        int lspo = spov.length;

                        // Build XX
                        Matrix XX = new Matrix(lpa + lspo, lpa + lspo);
                        int[] range1 = range(0, lpa - 1);
                        int[] range2 = range(lpa, lpa + lspo - 1);

                        // Upper left quadrant
                        XX.view(range1, range1).set(S.view(parv, parv).mat());

                        // Upper right quadrant
                        Matrix mat = S.view(parv, all).mat();
                        Matrix transpose = Z.transpose();
                        Matrix a11 = null;
                        a11 = mat.times(transpose);
                        XX.view(range1, range2).set(a11);

                        // Lower left quadrant
                        MView a12 = XX.view(range2, range1);
                        Matrix a13 = XX.view(range1, range2).mat().transpose();
                        a12.set(a13);

                        // Lower right quadrant
                        MView a14 = XX.view(range2, range2);
                        Matrix a15 = Z.times(S);
                        Matrix a16 = a15.times(Z.transpose());
                        a14.set(a16);

                        // Build XY
                        Matrix YX = new Matrix(1, lpa + lspo);
                        MView a17 = YX.view(range1, new int[]{0});
                        MView a18 = S.view(v, parv);
                        a17.set(a18);

                        MView a19 = YX.view(new int[]{0}, range2);
                        Matrix a20 = S.view(v, all).mat();
                        Matrix a21 = a20.times(Z.transpose());
                        a19.set(a21);

                        // Temp
                        Matrix a22 = XX.inverse();
                        Matrix temp = YX.times(a22.transpose());

                        // Assign to b.
                        MView a23 = a6.viewRow(0);
                        MView a24 = temp.view(range1, new int[]{0});
                        a23.set(a24.mat().scale(-1));

                        // Assign to omega.
                        MView view = temp.view(new int[]{0}, range2);
                        omega.view(v, spov).set(view.mat());
                        omega.view(spov, v).set(view.mat().transpose());

                        // Variance.
                        double tempVar = S.get(_v, _v) - temp.times(YX.transpose()).get(0, 0);
                        MView a27 = omega.view(v, spov);
                        MView a28 = oInv.view(spov, spov);
                        Matrix a29 = omega.view(spov, v).mat();
                        Matrix a30 = a27.mat().times(a28.mat());
                        Matrix a31 = a30.times(a29).scalarPlus(tempVar);
                        omega.view(v, v).set(a31);
                    } else {
                        Matrix oInv = new Matrix(p, p);
                        MView a2 = omega.view(vcomp, vcomp);
                        Matrix a3 = a2.mat().inverse();
                        oInv.view(vcomp, vcomp).set(a3);

                        Matrix a4 = oInv.view(spov, vcomp).mat();
                        Matrix a5 = B.view(vcomp, all).mat();
                        Matrix Z = a4.times(a5);

                        // Build XX
                        Matrix XX = Z.times(S).times(Z.transpose());

                        // Build XY
                        Matrix a20 = S.view(v, all).mat();
                        Matrix YX = a20.times(Z.transpose()).viewRow(0).mat();

                        // Temp
                        Matrix a22 = XX.inverse();
                        Matrix a23 = YX.times(a22.transpose());

                        // Assign to omega.
                        MView a24 = omega.view(v, spov);

                        a24.set(a23);
                        MView a25 = omega.view(spov, v);
                        a25.set(a23.transpose());

                        // Variance.
                        double tempVar = S.get(_v, _v) - a24.mat().transpose().times(YX).get(0, 0);

                        MView a27 = omega.view(v, spov);
                        MView a28 = oInv.view(spov, spov);
                        Matrix a29 = omega.view(spov, v).mat();
                        Matrix a30 = a27.mat().times(a28.mat());
                        Matrix a31 = a30.times(a29);
                        omega.set(_v, _v, tempVar + a31.get(0, 0));
                    }
                }
            }

            Matrix a32 = omega.minus(omegaOld);
            double diff1 = a32.norm1();

            Matrix a33 = B.minus(bOld);
            double diff2 = a33.norm1();

            double diff = diff1 + diff2;
            _diff = diff;

            if (diff < tolerance) break;
        }

        Matrix a34 = B.inverse();
        Matrix a35 = B.transpose().inverse();
        Matrix sigmahat = a34.times(omega).times(a35);

        Matrix lambdahat = omega.copy();
        MView a36 = lambdahat.view(ugComp, ugComp);
        a36.set(new Matrix(ugComp.length, ugComp.length));

        Matrix omegahat = omega.copy();
        MView a37 = omegahat.view(ug, ug);
        a37.set(new Matrix(ug.length, ug.length));

        Matrix bhat = B.copy();

        return new RicfResult(sigmahat, lambdahat, bhat, omegahat, i, _diff, covMatrix);
    }

    /**
     * <p>cliques.</p>
     *
     * @param graph a {@link Graph} object
     * @return an enumeration of the cliques of the given graph considered as undirected.
     */
    public List<List<Node>> cliques(Graph graph) {
        List<Node> nodes = graph.getNodes();
        List<List<Node>> cliques = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            List<Node> adj = graph.getAdjacentNodes(nodes.get(i));

            SortedSet<Integer> L1 = new TreeSet<>();
            L1.add(i);

            SortedSet<Integer> L2 = new TreeSet<>();

            for (Node _adj : adj) {
                L2.add(nodes.indexOf(_adj));
            }

            int moved = -1;

            do {
                addNodesToRight(L1, L2, graph, nodes, moved);

                if (isMaximal(L1, L2, graph, nodes)) {
                    record(L1, cliques, nodes);
                }

                moved = moveLastBack(L1, L2);

            } while (moved != -1);
        }

        return cliques;
    }

    /**
     * Fits a concentration graph. Coding algorithm #2 only.
     */
    private FitConGraphResult fitConGraph(Graph graph, ICovarianceMatrix cov, int n, double tol) {
        List<Node> nodes = graph.getNodes();
        String[] nodeNames = new String[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (!cov.getVariableNames().contains(node.getName())) {
                throw new IllegalArgumentException("Node in graph not in cov matrix: " + node);
            }

            nodeNames[i] = node.getName();
        }

        Matrix S = new Matrix(cov.getSubmatrix(nodeNames).getMatrix().toArray());
        graph = graph.subgraph(nodes);

        List<List<Node>> cli = cliques(graph);

        int nc = cli.size();

        if (nc == 1) {
            return new FitConGraphResult(S, 0, 0, 1);
        }

        int k = S.getNumRows();
        int it = 0;

        // Only coding alg #2 here.
        Matrix K = Vector.diag(S.diag()).inverse();

        int[] all = range(0, k - 1);

        while (true) {
            Matrix KOld = K.copy();
            it++;

            for (List<Node> aCli : cli) {
                int[] a = asIndices(aCli, nodes);
                int[] b = complement(all, a);
                MView a1 = S.view(a, a);
                Matrix a2 = a1.mat().inverse();
                MView a3 = K.view(a, b);
                MView a4 = K.view(b, b);
                Matrix a5 = a4.mat().inverse();
                Matrix a6 = K.view(b, a).mat();
                Matrix a7 = a3.mat().times(a5);
                Matrix a8 = a7.times(a6);
                a2 = a2.plus(a8);
                MView a9 = K.view(a, a);
                a9.set(a2);
            }

            Matrix a32 = K.copy().minus(KOld);
            double diff = a32.norm1();

            if (diff < tol) break;
        }

        Matrix V = K.inverse();

        int numNodes = graph.getNumNodes();
        int df = numNodes * (numNodes - 1) / 2 - graph.getNumEdges();
        double dev = lik(V.inverse(), S, n, k);

        return new FitConGraphResult(V, dev, df, it);
    }

    private int[] asIndices(List<Node> clique, List<Node> nodes) {
        int[] a = new int[clique.size()];

        for (int j = 0; j < clique.size(); j++) {
            a[j] = nodes.indexOf(clique.get(j));
        }

        return a;
    }

    private double lik(Matrix K, Matrix S, int n, int k) {
        Matrix SK = S.times(K);
        return (SK.trace() - FastMath.log(SK.det()) - k) * n;
    }

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

    private int[] complement(int[] all, int[] remove) {
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
        List<Node> ugNodes = new LinkedList<>();

        for (Node node : nodes) {
            if (mag.getNodesInTo(node, Endpoint.ARROW).isEmpty()) {
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
            List<Node> parents = new ArrayList<>(mag.getParents(nodes.get(i)));
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
        for (int j : new TreeSet<>(L2)) {
            if (j > max(L1) && j > moved && addable(j, L1, graph, nodes)) {
                L1.add(j);
                L2.remove(j);
            }
        }
    }

    private void record(SortedSet<Integer> L1, List<List<Node>> cliques,
                        List<Node> nodes) {
        List<Node> clique = new LinkedList<>();

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
     * Determines if a node j can be added to a set L1 while maintaining adjacency with all nodes in L1.
     *
     * @param j     The index of the node to be added.
     * @param L1    The set of indices representing the current set of nodes.
     * @param graph The graph containing the nodes.
     * @param nodes The list of nodes.
     * @return Returns true if node j can be added to L1 while maintaining adjacency with all nodes in L1, false
     * otherwise.
     */
    private boolean addable(int j, SortedSet<Integer> L1, Graph graph, List<Node> nodes) {
        for (int k : L1) {
            if (!graph.isAdjacentTo(nodes.get(j), nodes.get(k))) {
                return false;
            }
        }

        return true;
    }

    /**
     * RICF result.
     */
    public static class RicfResult {

        /**
         * The covariance matrix.
         */
        private final ICovarianceMatrix covMatrix;

        /**
         * The shat matrix.
         */
        private final Matrix shat;

        /**
         * The lhat matrix.
         */
        private final Matrix lhat;

        /**
         * The bhat matrix.
         */
        private final Matrix bhat;

        /**
         * The ohat matrix.
         */
        private final Matrix ohat;

        /**
         * The number of iterations.
         */
        private final int iterations;

        /**
         * The diff.
         */
        private final double diff;

        /**
         * The result.
         *
         * @param shat       The shat matrix.
         * @param lhat       The laht matrix.
         * @param bhat       The bhat matrix.
         * @param ohat       The ohat matrix.
         * @param iterations The number of iterations.
         * @param diff       The diff.
         * @param covMatrix  The covariance matrix.
         */
        public RicfResult(Matrix shat, Matrix lhat, Matrix bhat,
                          Matrix ohat, int iterations, double diff, ICovarianceMatrix covMatrix) {
            this.shat = shat;
            this.lhat = lhat;
            this.bhat = bhat;
            this.ohat = ohat;
            this.iterations = iterations;
            this.diff = diff;
            this.covMatrix = covMatrix;
        }

        /**
         * Returns a string representation of the RicfResult object.
         *
         * @return The string representation of the RicfResult object.
         */
        public String toString() {

            return "\nSigma hat\n" +
                   MatrixUtils.toStringSquare(getShat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nLambda hat\n" +
                   MatrixUtils.toStringSquare(getLhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nBeta hat\n" +
                   MatrixUtils.toStringSquare(getBhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nOmega hat\n" +
                   MatrixUtils.toStringSquare(getOhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nIterations\n" +
                   getIterations() +
                   "\n\ndiff = " + this.diff;
        }

        /**
         * Retrieves the shat matrix.
         *
         * @return The shat matrix.
         */
        public Matrix getShat() {
            return this.shat;
        }

        /**
         * Returns the "lhat" matrix.
         *
         * @return The "lhat" matrix.
         */
        public Matrix getLhat() {
            return this.lhat;
        }

        /**
         * Returns the bhat matrix.
         *
         * @return The bhat matrix.
         */
        public Matrix getBhat() {
            return this.bhat;
        }

        /**
         * Returns the ohat matrix.
         *
         * @return The ohat matrix.
         */
        public Matrix getOhat() {
            return this.ohat;
        }

        /**
         * Returns the number of iterations.
         *
         * @return The number of iterations.
         */
        public int getIterations() {
            return this.iterations;
        }
    }

    /**
     * The fit con graph result.
     */
    public static class FitConGraphResult {

        /**
         * The shat matrix
         */
        private final Matrix shat;

        /**
         * The deviance
         */
        double deviance;

        /**
         * The degrees of freedom.
         */
        int df;

        /**
         * The number of iterations.
         */
        int iterations;

        /**
         * The result.
         *
         * @param shat       The shat matrix.
         * @param deviance   The deviance.
         * @param df         The degrees of freedom.
         * @param iterations The iterations.
         */
        public FitConGraphResult(Matrix shat, double deviance,
                                 int df, int iterations) {
            this.shat = shat;
            this.deviance = deviance;
            this.df = df;
            this.iterations = iterations;
        }

        /**
         * Returns a string representation of the FitConGraphResult object. The string includes the Sigma hat matrix,
         * deviance value, degrees of freedom, and number of iterations.
         *
         * @return a string representation of the FitConGraphResult object.
         */
        public String toString() {

            return "\nSigma hat\n" +
                   this.shat +
                   "\nDeviance\n" +
                   this.deviance +
                   "\nDf\n" +
                   this.df +
                   "\nIterations\n" +
                   this.iterations;
        }
    }
}



