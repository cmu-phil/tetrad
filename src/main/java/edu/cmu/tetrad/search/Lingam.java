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

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.PlusMult;
import edu.cmu.tetrad.cluster.FastIca;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import no.uib.cipr.matrix.*;
import no.uib.cipr.matrix.Vector;

import java.util.*;

/**
 * Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear nongaussian acyclic model for
 * causal discovery, JMLR 7 (2006). Largely follows the Matlab code.
 *
 * @author Gustavo Lacerda
 * @author Joseph Ramsey
 */
public class Lingam {
    private double pruneFactor = 1.0;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    //================================CONSTRUCTORS==========================//

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    public Graph search(DataSet data) {
        DoubleMatrix2D X = new DenseDoubleMatrix2D(data.getDoubleData().toArray());
        List<Node> nodes = data.getVariables();

        EstimateResult result = estimate(X);
        DoubleMatrix2D bHat = pruneEdgesByResampling(X, result.getK());

        Graph graph = new EdgeListGraph(nodes);

        for (int j = 0; j < bHat.columns(); j++) {
            for (int i = 0; i < bHat.rows(); i++) {
                if (bHat.get(i, j) != 0) {
                    graph.addDirectedEdge(nodes.get(j), nodes.get(i));
                }
            }
        }

        this.logger.log("graph", "\nReturning this graph: " + graph);

        return graph;
    }

    //================================PUBLIC METHODS========================//

    public EstimateResult estimate(DoubleMatrix2D X) {
        DoubleMatrix2D W = calcWFastIca(X);
//        DoubleMatrix2D W = calcWR6(X);

//        DoubleMatrix2D A = result.getA().transpose();
//        DoubleMatrix2D W = MatrixUtils.inverse(A);
        System.out.println("W = " + W);

        TetradLogger.getInstance().log("lingamDetails", "\nW " + W);

        // The method that calls assign() twice could be a problem for the
        // negative coefficients
        DoubleMatrix2D S = W.copy();
        S.assign(cern.jet.math.Functions.abs);

        S.assign(cern.jet.math.Functions.inv);

        //this is an n x 2 matrix, i.e. a list of index pairs
        int[][] assignment = Hungarian.hgAlgorithm(S.viewDice().toArray(), "min");

//        for (int i = 0; i < assignment.length; i++) {
//            for (int j = 0; j < assignment[0].length; j++) {
//                System.out.print(assignment[i][j]);
//            }
//
//            System.out.println();
//        }

        int[] rowp = new int[assignment.length];

        for (int i = 0; i < rowp.length; i++) {
            rowp[i] = assignment[i][1];
        }

        TetradLogger.getInstance().log("lingamDetails", "\nrowp = ");

        for (int row : rowp) {
            TetradLogger.getInstance().log("lingamDetails", row + "\t");
        }

        DoubleMatrix2D Wp = W.viewSelection(rowp, range(0, W.columns() - 1));

        TetradLogger.getInstance().log("lingamDetails", "\nWp = " + Wp);

        DoubleMatrix1D estdisturbancesstd = new DenseDoubleMatrix1D(Wp.rows());

        for (int i = 0; i < Wp.rows(); i++) {
            estdisturbancesstd.set(i, 1.0 / Math.abs(Wp.get(i, i)));
        }

        TetradLogger.getInstance().log("lingamDetails", "\nWp = " + Wp);

        DoubleMatrix1D diag = DoubleFactory2D.dense.diagonal(Wp);

        for (int i = 0; i < Wp.rows(); i++) {
            for (int j = 0; j < Wp.columns(); j++) {
                Wp.set(i, j, Wp.get(i, j) / diag.get(i));
            }
        }

        TetradLogger.getInstance().log("lingamDetails", "\nWp = " + Wp);

        DoubleMatrix2D Best = DoubleFactory2D.dense.identity(Wp.rows());
        Best = Best.assign(Wp, PlusMult.plusMult(-1));

        TetradLogger.getInstance().log("lingamDetails", "\nBest + " + Best);

        DoubleMatrix1D Xm = new DenseDoubleMatrix1D(X.columns());

        for (int j = 0; j < X.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < X.rows(); i++) {
                double v = X.get(i, j);
                sum += v;
            }

            double mean = sum / X.rows();

            Xm.set(j, mean);
        }

        DoubleMatrix1D cest = new Algebra().mult(Wp, Xm);

        TetradLogger.getInstance().log("lingamDetails", "cest = " + cest);

        StlPruneResult result1 = stlPrune(Best);

        DoubleMatrix2D bestCausal = result1.getBestcausal();
        int[] causalperm = result1.getCausalperm();

        TetradLogger.getInstance().log("lingamDetails", "\nBest causal " + bestCausal);
        TetradLogger.getInstance().log("lingamDetails", "\ncausalperm = " + Arrays.toString(causalperm));

        int[] icausal = iperm(causalperm);

        for (int i = 0; i < bestCausal.rows(); i++) {
            for (int j = i + 1; j < bestCausal.columns(); j++) {
                bestCausal.set(i, j, 0);
            }
        }

        TetradLogger.getInstance().log("lingamDetails", "\nbestCausal = " + bestCausal);

        DoubleMatrix2D B = bestCausal.viewSelection(icausal, icausal).copy();


        TetradLogger.getInstance().log("lingamDetails", "B = " + B);

        return new EstimateResult(
                causalperm);
    }

    private DoubleMatrix2D calcWFastIca(DoubleMatrix2D X) {
        FastIca fastIca = new FastIca(X.copy(), X.columns());
        fastIca.setVerbose(true);
//        fastIca.setAlgorithmType(FastIca.DEFLATION);
//        fastIca.setFunction(FastIca.LOGCOSH);
//        fastIca.setTolerance(1e-20);
        FastIca.IcaResult result = fastIca.findComponents();
        DoubleMatrix2D w = result.getW();
        DoubleMatrix2D k = result.getK();

        return new Algebra().mult(k, w).viewDice();
    }

    public double getPruneFactor() {
        return pruneFactor;
    }

    public void setPruneFactor(double pruneFactor) {
        if (pruneFactor <= 0) {
            throw new IllegalArgumentException("Prune factor must be greater than zero.");
        }

        this.pruneFactor = pruneFactor;
    }

    public static class EstimateResult {
        private int[] k;

        public EstimateResult(int[] k) {
            this.k = k;
        }

        public int[] getK() {
            return k;
        }
    }

    private static class StlPruneResult {
        private DoubleMatrix2D Bestcausal;
        private int[] causalperm;

        public StlPruneResult(DoubleMatrix2D Bestcausal, int[] causalPerm) {
            this.Bestcausal = Bestcausal;
            this.causalperm = causalPerm;
        }

        public DoubleMatrix2D getBestcausal() {
            return Bestcausal;
        }

        public int[] getCausalperm() {
            return causalperm;
        }
    }

    private StlPruneResult stlPrune(DoubleMatrix2D bHat) {
        int m = bHat.rows();

        LinkedList<Entry> entries = getEntries(bHat);

        // Sort entries by absolute value.
        java.util.Collections.sort(entries);

        DoubleMatrix2D bHat2 = bHat.copy();
//
        int numUpperTriangle = m * (m + 1) / 2;
        int numTotal = m * m;
//
        for (int i = 0; i < numUpperTriangle; i++) {
            Entry entry = entries.get(i);
            bHat.set(entry.row, entry.column, 0);
        }

        // If that doesn't result in a permutation, try setting one more entry
        // to zero, iteratively, until you get a permutation.
        for (int i = numUpperTriangle; i < numTotal; i++) {
            int[] permutation = algorithmB(bHat);

            if (permutation != null) {
                DoubleMatrix2D Bestcausal = permute(permutation, bHat2);

                return new StlPruneResult(Bestcausal, permutation);
            }

            Entry entry = entries.get(i);
            bHat.set(entry.row, entry.column, 0);
        }

        throw new IllegalArgumentException("No permutation was found.");
    }

    private DoubleMatrix2D permute(int[] permutation, DoubleMatrix2D data) {
        return data.viewSelection(permutation, permutation);
    }

    private LinkedList<Entry> getEntries(DoubleMatrix2D mat) {
        LinkedList<Entry> entries = new LinkedList<Entry>();

        for (int i = 0; i < mat.rows(); i++) {
            for (int j = 0; j < mat.columns(); j++) {
                Entry entry = new Entry(i, j, mat.get(i, j));
                entries.add(entry);
            }
        }

        return entries;
    }

    private static class Entry implements Comparable<Entry> {
        private int row;
        private int column;
        private double value;

        public Entry(int row, int col, double val) {
            this.row = row;
            this.column = col;
            this.value = val;
        }

        /**
         * Used for sorting. An entry is smaller than another if its absolute value is smaller.
         *
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        public int compareTo(Entry entry) {
            double thisVal = Math.abs(value);
            double entryVal = Math.abs(entry.value);
            return (new Double(thisVal).compareTo(entryVal));
        }

        public String toString() {
            return "[" + row + "," + column + "]:" + value + " ";
        }
    }

    public int[] algorithmB(DoubleMatrix2D mat) {
        List<Integer> removedIndices = new ArrayList<Integer>();
        List<Integer> permutation = new ArrayList<Integer>();

        while (removedIndices.size() < mat.rows()) {
            int allZerosRow = -1;

            // Find a new row with zeroes in new columns.
            for (int i = 0; i < mat.rows(); i++) {
                if (removedIndices.contains(i)) {
                    continue;
                }

                if (zeroesInNewColumns(mat.viewRow(i), removedIndices)) {
                    allZerosRow = i;
                    break;
                }
            }

            // No such row.
            if (allZerosRow == -1) {
                return null;
            }

            removedIndices.add(allZerosRow);
            permutation.add(allZerosRow);
        }

        int[] _permutation = new int[permutation.size()];

        for (int i = 0; i < _permutation.length; i++) {
            _permutation[i] = permutation.get(i);
        }

        return _permutation;
    }

    private boolean zeroesInNewColumns(DoubleMatrix1D vec, List<Integer> removedIndices) {
        for (int i = 0; i < vec.size(); i++) {
            if (vec.get(i) != 0 && !removedIndices.contains(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This is the method used in Patrik's code.
     */
    public DoubleMatrix2D pruneEdgesByResampling(DoubleMatrix2D data, int[] k) {
        if (k.length != data.columns()) {
            throw new IllegalArgumentException("Execting a permutation.");
        }

        Set<Integer> set = new LinkedHashSet<Integer>();

        for (int i = 0; i < k.length; i++) {
            if (k[i] >= k.length) {
                throw new IllegalArgumentException("Expecting a permutation.");
            }

            if (set.contains(i)) {
                throw new IllegalArgumentException("Expecting a permutation.");
            }

            set.add(i);
        }

        Matrix X = new DenseMatrix(data.viewDice().toArray());

        int npieces = 10;
        int cols = X.numColumns();
        int rows = X.numRows();
        int piecesize = (int) Math.floor(cols / npieces);

        List<Matrix> bpieces = new ArrayList<Matrix>();
//        List<Vector> diststdpieces = new ArrayList<Vector>();
//        List<Vector> cpieces = new ArrayList<Vector>();

        for (int p = 0; p < npieces; p++) {

//          % Select subset of data, and permute the variables to the causal order
//          Xp = X(k,((p-1)*piecesize+1):(p*piecesize));

            int p0 = (p) * piecesize;
            int p1 = (p + 1) * piecesize - 1;
            int[] range = range(p0, p1);


            Matrix Xp = Matrices.getSubMatrix(X, k, range);

//          % Remember to subract out the mean
//          Xpm = mean(Xp,2);
//          Xp = Xp - Xpm*ones(1,size(Xp,2));
//
//          % Calculate covariance matrix
//          cov = (Xp*Xp')/size(Xp,2);

            double[] Xpm = new double[rows];

            for (int i = 0; i < rows; i++) {
                double sum = 0.0;

                for (int j = 0; j < Xp.numColumns(); j++) {
                    sum += Xp.get(i, j);
                }

                Xpm[i] = sum / Xp.numColumns();
            }

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < Xp.numColumns(); j++) {
                    Xp.set(i, j, Xp.get(i, j) - Xpm[i]);
                }
            }


            Matrix XpT = new DenseMatrix(Xp.numColumns(), rows);
            Matrix Xpt = Xp.transpose(XpT);

            Matrix cov = new DenseMatrix(rows, rows);
            cov = Xp.mult(Xpt, cov);

            for (int i = 0; i < cov.numRows(); i++) {
                for (int j = 0; j < cov.numColumns(); j++) {
                    cov.set(i, j, cov.get(i, j) / Xp.numColumns());
                }
            }

//          % Do QL decomposition on the inverse square root of cov
//          [Q,L] = tridecomp(cov^(-0.5),'ql');

            boolean posDef = MatrixUtils.isPositiveDefinite(new TetradMatrix(Matrices.getArray(cov)));
//            TetradLogger.getInstance().log("lingamDetails","Positive definite = " + posDef);

            if (!posDef) {
                System.out.println("Covariance matrix is not positive definite.");
            }

            DenseMatrix sqrt;

            try {
                sqrt = sqrt(new DenseMatrix(cov));
            } catch (NotConvergedException e) {
                throw new RuntimeException(e);
            }

            DenseMatrix I = Matrices.identity(rows);
            DenseMatrix AI = I.copy();
            DenseMatrix invSqrt;

            try {
                invSqrt = new DenseMatrix(sqrt.solve(I, AI));
            }
            catch (MatrixSingularException e) {
                throw new RuntimeException("Singular matrix.", e);
            }

            QL ql = QL.factorize(invSqrt);
            Matrix L = ql.getL();

//          % The estimated disturbance-stds are one over the abs of the diag of L
//          newestdisturbancestd = 1./diag(abs(L));

            Vector newestdisturbancestd = new DenseVector(rows);

            for (int t = 0; t < rows; t++) {
                newestdisturbancestd.set(t, 1.0 / Math.abs(L.get(t, t)));
            }

//          % Normalize rows of L to unit diagonal
//          L = L./(diag(L)*ones(1,dims));
//
            for (int s = 0; s < rows; s++) {
                for (int t = 0; t <= s; t++) {
                    L.set(s, t, L.get(s, t) / L.get(s, s));
                }
            }

//          % Calculate corresponding B
//          bnewest = eye(dims)-L;

            Matrix bnewest = Matrices.identity(rows);
            bnewest = bnewest.add(-1.0, L);

//          % Also calculate constants
//          cnewest = L*Xpm;

//            Vector cnewest = new DenseVector(rows);
//            cnewest = L.mult(new DenseVector(Xpm), cnewest);

//          % Permute back to original variable order
//          ik = iperm(k);
//          bnewest = bnewest(ik, ik);
//          newestdisturbancestd = newestdisturbancestd(ik);
//          cnewest = cnewest(ik);

            int[] ik = iperm(k);

//            System.out.println("ik = " + Arrays.toString(ik));

            bnewest = Matrices.getSubMatrix(bnewest, ik, ik);
//            newestdisturbancestd = Matrices.getSubVector(newestdisturbancestd, ik);
//            cnewest = Matrices.getSubVector(cnewest, ik);

//          % Save results
//          Bpieces(:,:,p) = bnewest;
//          diststdpieces(:,p) = newestdisturbancestd;
//          cpieces(:,p) = cnewest;

            bpieces.add(bnewest);
//            diststdpieces.add(newestdisturbancestd);
//            cpieces.add(cnewest);

//
//        end

        }


//
//        for i=1:dims,
//          for j=1:dims,
//
//            themean = mean(Bpieces(i,j,:));
//            thestd = std(Bpieces(i,j,:));
//            if abs(themean)<prunefactor*thestd,
//          Bfinal(i,j) = 0;
//            else
//          Bfinal(i,j) = themean;
//            end
//
//          end
//        end

        Matrix means = new DenseMatrix(rows, rows);
        Matrix stds = new DenseMatrix(rows, rows);

        Matrix BFinal = new DenseMatrix(rows, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
//                double sum = 0.0;
//
//                for (int y = 0; y < npieces; y++) {
//                    sum += bpieces.get(y).get(i, j);
//                }
//
//                double themean = sum / (npieces);
//
//                double sumVar = 0.0;
//
//                for (int y = 0; y < npieces; y++) {
//                    sumVar += Math.pow((bpieces.get(y).get(i, j)) - themean, 2);
//                }
//
//                double thestd = Math.sqrt(sumVar / (npieces));
//
                double[] b = new double[npieces];

                for (int y = 0; y < npieces; y++) {
                    b[y] = bpieces.get(y).get(i, j);
                }

                double themean = StatUtils.mean(b);
                double thestd = StatUtils.sd(b);

                means.set(i, j, themean);
                stds.set(i, j, thestd);

                if (Math.abs(themean) < getPruneFactor() * thestd) {
                    BFinal.set(i, j, 0);
                } else {
                    BFinal.set(i, j, themean);
                }
            }
        }

//
//        diststdfinal = mean(diststdpieces,2);
//        cfinal = mean(cpieces,2);
//
//        % Finally, rename all the variables to the way we defined them
//        % in the function definition
//
//        Bpruned = Bfinal;
//        stde = diststdfinal;
//        ci = cfinal;

        return new DenseDoubleMatrix2D(Matrices.getArray(BFinal));
    }

    public int[] iperm(int[] k) {
        int[] ik = new int[k.length];

        for (int i = 0; i < k.length; i++) {
            for (int j = 0; j < k.length; j++) {
                if (k[i] == j) {
                    ik[j] = i;
                }
            }
        }

        return ik;
    }

    private DenseMatrix sqrt(DenseMatrix m) throws NotConvergedException {
        EVD eig = new EVD(m.numRows());
        eig.factor(m);
        double[] r = eig.getRealEigenvalues();
        Matrix v = eig.getLeftEigenvectors();

        Matrix d = new DenseMatrix(m.numRows(), m.numRows());
        for (int i = 0; i < d.numRows(); i++) d.set(i, i, Math.sqrt(Math.abs(r[i])));

        Matrix vd = new DenseMatrix(m.numRows(), m.numRows());
        vd = v.mult(d, vd);
        Matrix vT = new DenseMatrix(m.numRows(), m.numRows());
        vT = v.transpose(vT);

        DenseMatrix prod = new DenseMatrix(m.numRows(), m.numRows());
        vd.mult(vT, prod);
        return prod;
    }

    private int[] range(int i1, int i2) {
        if (i2 < i1) throw new IllegalArgumentException("i2 must be >=  i2 " + i1 + ", " + i2);
        int series[] = new int[i2 - i1 + 1];
        for (int j = i1; j <= i2; j++) series[j - i1] = j;
        return series;
    }

}


