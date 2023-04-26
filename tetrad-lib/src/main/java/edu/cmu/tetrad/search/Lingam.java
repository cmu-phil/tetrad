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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.PermutationGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.cmu.tetrad.search.LingD.threshold;
import static java.lang.StrictMath.abs;

/**
 * <p>Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear
 * nongaussian acyclic model for causal discovery, JMLR 7 (2006).</p>
 *
 * @author josephramsey
 */
public class Lingam {
    private double pruneFactor = 0.3;
    private double wThreshold = 0.0;
    private Matrix permutedBHat = null;
    private List<Node> permutedVars = null;

    //================================CONSTRUCTORS==========================//

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    /**
     * Searches given the W matrix from ICA.
     *
     * @param W         the W matrix from ICA.
     * @param variables The variables from the original dataset used to generate the W matrix,
     *                  in the order they occur in that dataset.
     * @return The graph returned.
     */
    public Graph search(Matrix W, List<Node> variables, double wThreshold) {

        wThreshold = 0.1;
        Matrix thresholded = threshold(W, wThreshold);
        W = thresholded;

        System.out.println("Thresholded W = " + thresholded);

        //////// ONE WAY


        List<PermutationMatrixPair> pairs = LingD.nRooks(thresholded.transpose());

        PermutationMatrixPair bestPair = null;
        double sum1 = Double.POSITIVE_INFINITY;

        P:
        for (PermutationMatrixPair pair : pairs) {
            Matrix permutedMatrix = pair.getPermutedMatrix();

//            System.out.println("Permuted = " + permutedMatrix);

            double sum = 0.0;
            for (int j = 0; j < permutedMatrix.rows(); j++) {
                double a = permutedMatrix.get(j, j);

                if (a == 0) {
                    continue P;
                }

                sum += 1.0 / abs(a);
            }

            if (sum < sum1) {
                sum1 = sum;
                bestPair = pair;
            }
        }

        if (bestPair == null) {
            throw new NullPointerException("Could not find an N Rooks solution with that threshold.");
        }

        Matrix WTilde = bestPair.getPermutedMatrix().transpose();


        //////// OTHER WAY

//        PermutationGenerator gen1 = new PermutationGenerator(W.rows());
//
//        // The first task is to find a row permutation of the W matrix that maximizes
//        // the absolute values on its diagonal. We do this by minimizing SUM(1 / |Wii|).
//        int[] rowPerm = new int[0];
//        double sum1 = Double.POSITIVE_INFINITY;
//        int[] choice1;
//
//        P:
//        while ((choice1 = gen1.next()) != null) {
//            double sum = 0.0;
//
//            for (int j = 0; j < W.rows(); j++) {
////                double a = W.get(choice1[j], j);
////                sum += a == 0 ? Double.POSITIVE_INFINITY : 1.0 / abs(a);
//
//                double a = W.get(choice1[j], j);
//
//                if (a == 0) {
//                    continue P;
//                }
//
//                sum += 1.0 / abs(a);
//            }
//
//            if (sum < sum1) {
//                sum1 = sum;
//                rowPerm = Arrays.copyOf(choice1, choice1.length);
//            }
//        }
//
//        Matrix WTilde = new PermutationMatrixPair(W, rowPerm, null).getPermutedMatrix();


        // We calculate BHat as I - WTilde.
        WTilde = LingD.scale(WTilde);
        Matrix BHat = Matrix.identity(W.columns()).minus(WTilde);

        // The second task is to rearrange the BHat matrix by permuting rows and columns
        // simultaneously so that the lower triangle is maximal--i.e., so that SUM(WTilde(i, j)^2)
        // is maximal for j > i. The goal of this is to find a causal order for the variables.
        // If all the big coefficients are in the lower triangle, we can interpret it as a
        // DAG model. We will ignore any big coefficients left over in the upper triangle.
        // We will assume the diagonal of the BHat matrix is zero--i.e., no self-loops.
        PermutationGenerator gen2 = new PermutationGenerator(BHat.rows());
        int[] perm = new int[0];
        double sum2 = Double.NEGATIVE_INFINITY;
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.rows(); i++) {
                for (int j = 0; j < i; j++) {
                    double b = BHat.get(choice2[i], choice2[j]);
                    sum += b * b;
                }
            }

            if (sum > sum2) {
                sum2 = sum;
                perm = Arrays.copyOf(choice2, choice2.length);
            }
        }

        // Grab that lower-triangle maximized version of the BHat matrix.
        Matrix BHatTilde = new PermutationMatrixPair(BHat, perm, perm).getPermutedMatrix();

        // Set the upper triangle now to zero, since we are ignoring it for this DAG algorithm.
        for (int i = 0; i < BHatTilde.rows(); i++) {
            for (int j = i + 1; j < BHatTilde.columns(); j++) {
                BHatTilde.set(i, j, 0.0);
            }
        }

        // Permute the variables too for that order.
        List<Node> varPerm = new ArrayList<>();
        for (int k : perm) varPerm.add(variables.get(k));

        // Grab the permuted BHat and variables.
        this.permutedBHat = BHatTilde;
        this.permutedVars = varPerm;

        // Make the graph and return it.
        Graph g = new EdgeListGraph(varPerm);

        for (int j = 0; j < getPermutedBHat().columns(); j++) {
            for (int i = j + 1; i < getPermutedBHat().rows(); i++) {
                if (abs(getPermutedBHat().get(i, j)) > pruneFactor) {
                    g.addDirectedEdge(getPermutedVars().get(j), getPermutedVars().get(i));
                }
            }
        }

        return g;
    }

    /**
     * The threshold to use for estimated B Hat matrices for the LiNGAM algorithm.
     *
     * @param pruneFactor Some value >= 0.
     */
    public void setPruneFactor(double pruneFactor) {
        if (pruneFactor < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + pruneFactor);
        this.pruneFactor = pruneFactor;
    }

    /**
     * After search the permuted BHat matrix can be retrieved using this method.
     *
     * @return The permutated (lower triangle) BHat matrix. Here, BHat(i, j) != 0 means that
     * there is an edge vars(j)-->vars(i) in the graph, where 'vars' means the permuted variables.
     */
    public Matrix getPermutedBHat() {
        return permutedBHat;
    }

    /**
     * The permuted variables of the graph. This is the estimated causal order of the models.
     *
     * @return This list of variables.
     */
    public List<Node> getPermutedVars() {
        return permutedVars;
    }
}

