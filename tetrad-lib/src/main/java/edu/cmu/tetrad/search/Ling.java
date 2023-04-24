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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Lacerda, G., Spirtes, P. L., Ramsey, J., & Hoyer, P. O. (2012). Discovering cyclic causal models
 * by independent components analysis. arXiv preprint arXiv:1206.3273.
 *
 * @author lacerda
 * @author josephramsey
 */
public class Ling {

    /**
     * The dataset being used for search.
     */
    private final DataSet dataSet;

    /**
     * The variables in the dataset.
     */
    private final List<Node> variables;

    /**
     * This algorithm uses thresholding to zero out small covariance values. This variable defines the value at which
     * the thresholding occurs.
     */
    private double threshold = .5;

    private double fastIcaA = 1.1;
    private int fastIcaMaxIter = 2000;
    private double fastIcaTolerance = 1e-6;

    //=============================CONSTRUCTORS============================//

    /**
     * The algorithm only requires a DataSet to process. Passing in a Dataset and then running the search algorithm is
     * an effetive way to use LiNG.
     *
     * @param dataSet a DataSet over which the algorithm can process
     */
    public Ling(DataSet dataSet) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * @return DataSet   Returns a dataset of the data used by the algorithm.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * The search method is used to process LiNG. Call search when you want to run the algorithm.
     */
    public List<double[][]> search() {
        Matrix w = getWFastIca();
        return findCandidateModels(w);
    }

    /**
     * Sets the value at which thresholding occurs on Fast ICA data. Default is .05.
     *
     * @param t The value at which the thresholding is set
     */
    public void setThreshold(double t) {
        this.threshold = t;
    }

    public static Graph getGraph(double[][] b, List<Node> variables) {
        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = 0; j < variables.size(); j++) {
                if (i != j && b[j][i] != 0) {
                    graph.addDirectedEdge(variables.get(j), variables.get(i));
                }
            }
        }

        return graph;
    }

    public static boolean isStable(double[][] matrix) {
        EigenDecomposition eigen = new EigenDecomposition(new BlockRealMatrix(matrix));
        double[] realEigenvalues = eigen.getRealEigenvalues();
        double[] imagEigenvalues = eigen.getImagEigenvalues();

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
//            double imagEigenvalue = imagEigenvalues[i];
            double modulus = sqrt(pow(realEigenvalue, 2) /*+ pow(imagEigenvalue, 2)*/);

            System.out.println("modulus" + " " + modulus);

            if (realEigenvalue >= 1.0) {
                return false;
            }
        }

        return true;
    }

    //==============================PRIVATE METHODS====================//

    private List<double[][]> findCandidateModels(Matrix w) {
        for (int i = 0; i < w.rows(); i++) {
            for (int j = 0; j < w.columns(); j++) {
                if (abs(w.get(i, j)) < threshold) w.set(i, j, 0.0);
            }
        }

        List<double[][]> models = new ArrayList<>();
        List<PermutationMatrixPair> perms = nRooks(w);

        for (PermutationMatrixPair perm : perms) {
            Matrix bHat = Ling.computeBhat(perm.getW());
//            bHat = LingUtils.normalizeDiagonal(bHat);

            System.out.println("perm = " + perm.getPermutation() + "\nmodel = ");

            for (int i = 0; i < w.rows(); i++) {
                System.out.println();
                for (int j = 0; j < w.columns(); j++) {
                    int i1 = bHat.get(i, j) == 0 ? 0 : 1;
                    System.out.print(i1 + " ");
                }
            }

            System.out.println();

            models.add(bHat.toArray());
        }

        return models;
    }

    private List<PermutationMatrixPair> nRooks(Matrix w) {
        List<PermutationMatrixPair> pairs = new java.util.ArrayList<>();

//        pruned = pruneEdgesByResampling(dataSet.getDoubleData());

//        w = removeZeroRowsAndCols(w);

        System.out.println("w = " + w);

        //returns all zeroless-diagonal column-pairs
        boolean[][] allowablePositions = new boolean[w.rows()][w.columns()];
        for (int i = 0; i < w.rows(); i++) {
            for (int j = 0; j < w.columns(); j++) {
                allowablePositions[i][j] = w.get(i, j) == 0;
            }
        }

        for (int i = 0; i < w.rows(); i++) {
            System.out.println();
            for (int j = 0; j < w.columns(); j++) {
                System.out.print((allowablePositions[i][j] ? 0 : 1) + " ");
            }
        }

        System.out.println();

        List<int[]> nRookAssignments = NRooks.nRooks(allowablePositions);

        //for each assignment, add the corresponding permutation to 'pairs'
        for (int[] assignment : nRookAssignments) {
            List<Integer> _assignment = new ArrayList<>();
            for (int j : assignment) _assignment.add(j);

            Matrix _w = Ling.permuteColumns(w, _assignment);
            pairs.add(new PermutationMatrixPair(_assignment, _w));
        }

        return pairs;
    }

    private Matrix removeZeroRowsAndCols(Matrix w) {

        Matrix _W = w.copy();
        List<Node> _variables = new ArrayList<>(variables);
        List<Integer> remove = new ArrayList<>();

        ROW:
        for (int i = 0; i < _W.rows(); i++) {
            Vector row = _W.getRow(i);

            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) != 0) continue ROW;
            }

            remove.add(i);

        }

        COLUMN:
        for (int i = 0; i < _W.rows(); i++) {
            Vector col = _W.getColumn(i);

            for (int j = 0; j < col.size(); j++) {
                if (col.get(j) != 0) continue COLUMN;
            }

            if (!remove.contains((i))) {
                remove.add(i);
            }
        }

        int[] rows = new int[_W.rows() - remove.size()];

        int count = -1;
        for (int k = 0; k < w.rows(); k++) {
            if (remove.contains(k)) {
                variables.remove(_variables.get(k));
            } else {
                if (!remove.contains(k)) rows[++count] = k;
            }
        }

        w = w.getSelection(rows, rows);

        return w;
    }

    private static Matrix permuteColumns(Matrix mat, List<Integer> permutation) {
        Matrix permutedMat = mat.like();

        for (int j = 0; j < mat.rows(); j++) {
            Vector col = mat.getColumn(j);
            permutedMat.assignColumn(permutation.get(j), col);
        }

        return permutedMat;
    }

    private static Matrix computeBhat(Matrix W) {
        int size = W.rows();
        return Matrix.identity(size).minus(W);
    }

    private Matrix getWFastIca() {
//        Matrix data = new Matrix(this.dataSet.getDoubleData().toArray()).transpose();
//        FastIca fastIca = new FastIca(data, 30);
//        fastIca.setVerbose(false);
//        fastIca.setAlgorithmType(FastIca.DEFLATION);
//        fastIca.setFunction(FastIca.LOGCOSH);
//        fastIca.setTolerance(.001);
//        fastIca.setMaxIterations(5000);
//        fastIca.setAlpha(1.0);
//        FastIca.IcaResult result = fastIca.findComponents();
//        Matrix W = new Matrix(result.getW());


        Matrix X = dataSet.getDoubleData();
        X = DataUtils.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.rows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(this.fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.PARALLEL);
        fastIca.setTolerance(this.fastIcaTolerance);
        fastIca.setFunction(FastIca.EXP);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(this.fastIcaA);
        FastIca.IcaResult result11 = fastIca.findComponents();
        Matrix W = result11.getW();

        return W;
    }

    /**
     * This is the method used in Patrik's code.
     */
    public Matrix pruneEdgesByResampling(Matrix data) {
        Matrix X = new Matrix(data.transpose().toArray());

        final int npieces = 10;
        int cols = X.columns();
        int rows = X.rows();

        List<Matrix> bpieces = new ArrayList<>();

        for (int p = 0; p < npieces; p++) {
            List<Double> Xpm = new ArrayList<>();

            for (int i = 0; i < rows; i++) {
                double sum = 0.0;

                for (int j = 0; j < X.columns(); j++) {
                    sum += X.get(i, j);
                }

                Xpm.set(i, sum / X.columns());
            }

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < X.columns(); j++) {
                    X.set(i, j, X.get(i, j) - Xpm.get(i));
                }
            }

            Matrix Xpt = X.transpose();

            Matrix cov = X.times(Xpt);

            for (int i = 0; i < cov.rows(); i++) {
                for (int j = 0; j < cov.columns(); j++) {
                    cov.set(i, j, cov.get(i, j) / X.columns());
                }
            }

            boolean posDef = MatrixUtils.isPositiveDefinite(cov);

            if (!posDef) {
                System.out.println("Covariance matrix is not positive definite.");
            }

            Matrix sqrt = cov.sqrt();

            Matrix I = Matrix.identity(rows);
            I.copy();
            Matrix invSqrt = sqrt.inverse();

            QRDecomposition qr = new QRDecomposition(new BlockRealMatrix(invSqrt.toArray()));
            RealMatrix r = qr.getR();

//            List<Double> newestdisturbancestd = new ArrayList<>(rows);
//
//            for (int t = 0; t < rows; t++) {
//                newestdisturbancestd.set(t, 1.0 / abs(r.getEntry(t, t)));
//            }

            for (int s = 0; s < rows; s++) {
                for (int t = 0; t < min(s, cols); t++) {
                    r.setEntry(s, t, r.getEntry(s, t) / r.getEntry(s, s));
                }
            }

            Matrix bnewest = Matrix.identity(rows);
            bnewest = bnewest.minus(new Matrix(r.getData()));

            bpieces.add(bnewest);
        }

        Matrix means = new Matrix(rows, rows);
        Matrix stds = new Matrix(rows, rows);

        Matrix BFinal = new Matrix(rows, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                double sum = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sum += bpieces.get(y).get(i, j);
                }

                double themean = sum / (npieces);

                double sumVar = 0.0;

                for (int y = 0; y < npieces; y++) {
                    sumVar += pow((bpieces.get(y).get(i, j)) - themean, 2);
                }

                double thestd = sqrt(sumVar / (npieces));

                means.set(i, j, themean);
                stds.set(i, j, thestd);

                if (abs(themean) < this.threshold * thestd) {//  getPruneFactor() * thestd) {
                    BFinal.set(i, j, 0);
                } else {
                    BFinal.set(i, j, themean);
                }
            }
        }

        return BFinal;
    }


}


