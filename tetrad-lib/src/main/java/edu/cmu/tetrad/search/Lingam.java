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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.List;

import static edu.cmu.tetrad.search.LingD.threshold;

/**
 * <p>Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear
 * nongaussian acyclic model for causal discovery, JMLR 7 (2006).</p>
 *
 * @author josephramsey
 */
public class Lingam {
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
    public LingD.Result search(Matrix W, List<Node> variables) {
        W = threshold(W, pruneFactor);
        PermutationMatrixPair bestPair = LingD.strongestDiagonalByCols(W);
        Matrix WTilde = bestPair.getPermutedMatrix().transpose();
        WTilde = LingD.scale(WTilde);
        Matrix BHat = Matrix.identity(W.columns()).minus(WTilde);

        // Grab the permuted BHat and variables.
        int[] perm = bestPair.getRowPerm();
        int[] inverse = LingD.inversePermutation(perm);
        PermutationMatrixPair inversePair = new PermutationMatrixPair(BHat, inverse, inverse);
        Matrix _bHat = inversePair.getPermutedMatrix();

        // Make the graph and return it.
        return new LingD.Result(BHat, LingD.makeGraph(_bHat, variables));
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
}

