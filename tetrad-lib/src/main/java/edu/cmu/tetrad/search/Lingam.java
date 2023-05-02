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

import edu.cmu.tetrad.util.Matrix;

/**
 * <p>Implements an interpretation of the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen,
 * and Kerminen, A linear nongaussian acyclic model for causal discovery, JMLR 7 (2006).</p>
 * <p>The focus for this implementation was making super-simple code, not so much
 * because the method was trivial (it's not) but out of an attempt to compartmentalize.
 * Bootstrapping and other forms of improving the estimate of BHat were not addressed,
 * and not attempt was made here to ensure that LiNGAM outputs a DAG. For high sample sizes
 * for an acyclic model it does tend to. No attempt was made to implement DirectLiNGAM
 * since it was tangential to my effort to get LiNG-D to work. Also, only a passing effort
 * to get either of these algorithms to handle real data. There are two tuning parameters--a
 * threshold for finding a strong diagonal and a threshold on the B matrix for finding edges
 * in the final graph; these are finicky. So there's more work to do, and the implementation may
 * improve in the future.</p>
 * <p>Both N Rooks and Hungarian Algorithm were tested for finding the best strong diagonal;
 * these were not compared head to head, though the initial impression was that N Rooks was better,
 * so this version uses it.</p>
 *
 * @author josephramsey
 */
public class Lingam {
    private double spineThreshold = 0.5;
    private double bThreshold = 0.1;

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    /**
     * Searches given the W matrix from ICA.
     * @param W the W matrix from ICA, WX = e.
     * @return The estimated B Hat matrix.
     */
    public Matrix fit(Matrix W) {
        PermutationMatrixPair bestPair = LingD.strongestDiagonalByCols(W, spineThreshold);
        return LingD.getScaledBHat(bestPair, bThreshold);
    }

    /**
     * The threshold to use for estimated B Hat matrices for the LiNGAM algorithm.
     * @param bThreshold Some value >= 0.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * Sets the threshold used to prune the matrix for purpose of searching for alterantive strong dia=gonals..
     * @param spineThreshold The threshold, a non-negative number.
     */
    public void setSpineThreshold(double spineThreshold) {
        if (spineThreshold < 0)
            throw new IllegalArgumentException("Expecting a non-negative number: " + spineThreshold);
        this.spineThreshold = spineThreshold;
    }
}

