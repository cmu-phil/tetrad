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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.LingD.threshold;

/**
 * <p>Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear
 * nongaussian acyclic model for causal discovery, JMLR 7 (2006).</p>
 *
 * @author josephramsey
 */
public class Lingam {
    private Matrix permutedBHat = null;
    private List<Node> permutedVars = null;
    private double pruneFactor;

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
    public Graph search(Matrix W, List<Node> variables) {
        W = threshold(W, pruneFactor);

        PermutationMatrixPair bestPair = LingD.strongestDiagonalByCols(W);

        if (bestPair == null) {
            throw new NullPointerException("Could not find an N Rooks solution with that threshold.");
        }

        Matrix WTilde = bestPair.getPermutedMatrix().transpose();
        WTilde = LingD.scale(WTilde);
        Matrix BHat = Matrix.identity(W.columns()).minus(WTilde);
        int[] perm = LingD.encourageLowerTriangular(BHat);
        Matrix permutedBHat = new PermutationMatrixPair(BHat, perm, perm).getPermutedMatrix();

        // Set the upper triangle now to zero, since we are ignoring it for this DAG algorithm.
        for (int i = 0; i < permutedBHat.rows(); i++) {
            for (int j = i + 1; j < permutedBHat.columns(); j++) {
                permutedBHat.set(i, j, 0.0);
            }
        }

        // Permute the variables too for that order.
        List<Node> permutedVars = new ArrayList<>();
        for (int k : perm) permutedVars.add(variables.get(k));

        // Grab the permuted BHat and variables.
        this.permutedBHat = permutedBHat;
        this.permutedVars = permutedVars;

        // Make the graph and return it.
        return LingD.makeGraph(permutedBHat, permutedVars);
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

