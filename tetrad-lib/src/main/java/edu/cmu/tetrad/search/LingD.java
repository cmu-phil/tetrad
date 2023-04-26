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
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.pow;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Lacerda, G., Spirtes, P. L., Ramsey, J., & Hoyer, P. O. (2012). Discovering cyclic causal models
 * by independent components analysis. arXiv preprint arXiv:1206.3273.
 *
 * @author lacerda
 * @author josephramsey
 */
public class LingD {

    private double wThreshold = .5;

    //=============================CONSTRUCTORS============================//

    /**
     * The algorithm only requires a DataSet to process. Passing in a Dataset and then running the search algorithm is
     * an effetive way to use LiNG.
     */
    public LingD() {
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * Searches given the W matrix from ICA.
     *
     * @param W the W matrix from ICA
     * @return the LiNGAM graph.
     */
    public List<PermutationMatrixPair> search(Matrix W) {
        return nRooks(Lingam.threshold(W, wThreshold));
    }

    /**
     * Sets the value at which thresholding occurs for the W matrix.
     * @param wThreshold The value at which the thresholding is set.
     */
    public void setWThreshold(double wThreshold) {
        this.wThreshold = wThreshold;
    }

    public static Matrix getBHat(PermutationMatrixPair pair) {
        Matrix _w = pair.getPermutedMatrix();
        Matrix bHat = Matrix.identity(_w.rows()).minus(_w);
        return Lingam.scale(bHat);
    }

    public static Graph getGraph(PermutationMatrixPair pair, List<Node> variables) {
        int[] perm = pair.getColPerm();

        List<Node> permVars = new ArrayList<>();

        for (int i = 0; i < variables.size(); i++) {
            permVars.add(variables.get(perm[i]));
        }

        return getGraph(getBHat(pair), permVars);
    }

    /**
     * Whether the BHat matrix represents a stable model. The eigenvalues are checked ot make sure they are
     * all less than 1.
     * @param pair The permutation pair.
     * @return True iff the model is stable.
     */
    public static boolean isStable(PermutationMatrixPair pair) {
        EigenDecomposition eigen = new EigenDecomposition(new BlockRealMatrix(getBHat(pair).toArray()));
        double[] realEigenvalues = eigen.getRealEigenvalues();
        double[] imagEigenvalues = eigen.getImagEigenvalues();

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            double modulus = sqrt(pow(realEigenvalue, 2) + pow(imagEigenvalue, 2));

            System.out.println("modulus" + " " + modulus);

            if (modulus >= 1.0) {
                return false;
            }
        }

        return true;
    }

    //==============================PRIVATE METHODS=========================//

    private List<PermutationMatrixPair> nRooks(Matrix W) {
        List<PermutationMatrixPair> pairs = new java.util.ArrayList<>();

        System.out.println("Listing permutation pairs, W = " + W);

        //returns all zeroless-diagonal column-pairs
        boolean[][] allowablePositions = new boolean[W.rows()][W.columns()];

        for (int i = 0; i < W.rows(); i++) {
            for (int j = 0; j < W.columns(); j++) {
                allowablePositions[i][j] = W.get(i, j) != 0;
            }
        }

        printAllowablePositions(W, allowablePositions);

        List<int[]> colPermutations = NRooks.nRooks(allowablePositions);

        //for each assignment, add the corresponding permutation to 'pairs'
        for (int[] colPermutation : colPermutations) {
            pairs.add(new PermutationMatrixPair(null, colPermutation, W));
        }

        return pairs;
    }

    private static void printAllowablePositions(Matrix W, boolean[][] allowablePositions) {
        System.out.println("\nAllowable rook positions");

        // Print allowable board.
        for (int i = 0; i < W.rows(); i++) {
            System.out.println();
            for (int j = 0; j < W.columns(); j++) {
                System.out.print((allowablePositions[i][j] ? 1 : 0) + " ");
            }
        }

        System.out.println();
        System.out.println();
    }


    /**
     * Returns the graph for the givem model.
     * @param bHat The B Hat for the model.
     * @param variables The variables for the model.
     * @return The graph.
     */
    private static Graph getGraph(Matrix bHat, List<Node> variables) {
        Graph graph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = 0; j < variables.size(); j++) {
                if (i != j && bHat.get(j, i) != 0) {
                    graph.addDirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        return graph;
    }
}


