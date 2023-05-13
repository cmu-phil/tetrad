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
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;

/**
 * <p>Implements an interpretation of the LiNGAM algorithm. The reference is here:</p>
 *
 * <p>Shimizu, S., Hoyer, P. O., Hyvärinen, A., Kerminen, A., &amp; Jordan, M. (2006).
 * A linear non-Gaussian acyclic model for causal discovery. Journal of Machine Learning
 * Research, 7(10).</p>
 *
 * <p>The focus for this implementation was making super-simple code, not so much
 * because the method was trivial (it's not) but out of an attempt to compartmentalize.
 * Bootstrapping and other forms of improving the estimate of BHat were not addressed,
 * and no attempt was made here to ensure that LiNGAM outputs a DAG. Fpr acuyclic inputs,
 * it does tend to. Also, no attempt was made to implement DirectLiNGAM since it was tangential
 * to the effort to get LiNG-D to work. Also, only a passing effort to get either of these
 * algorithms to handle real data. There are one tuning parameters (in addition to the FastICA
 * paramters that are exposed)--a threshold on the B matrix for finding edges in the final
 * graph. A future version may use bootstrapping with a p-value; this has not been addressed
 * here.</p>
 *
 * <p>We are using the Hungarian Algorithm to fine best diagonal for the W matrix.</p>
 *
 * <p>This class is not configured to respect knowledge of forbidden and required
 * edges.</p>
 *
 * @author josephramsey
 * @see LingD
 */
public class Lingam {
    private double bThreshold = 0.1;

    /**
     * Constructor..
     */
    public Lingam() {
    }

    /**
     * Fits a LiNGAM model to the given dataset using a default method for estimating W.
     *
     * @param D A continuous dataset.
     * @return The BHat matrix, where B[i][j] gives the coefficient of j->i if nonzero.
     */
    public Matrix fit(DataSet D) {
        Matrix W = LingD.estimateW(D, 5000, 1e-6, 1.2);
        return fitW(W);
    }

    /**
     * Searches given a W matrix is that is provided by the user (where WX = e).
     *
     * @param W A W matrix estimated by the user, possibly by some other method.
     * @return The estimated B Hat matrix.
     */
    public Matrix fitW(Matrix W) {
        PermutationMatrixPair bestPair = LingD.hungarianDiagonal(W);
        return LingD.getScaledBHat(bestPair, bThreshold);
    }

    /**
     * The threshold to use for set small elemtns to zerp in the B Hat matrices for the
     * LiNGAM algorithm.
     *
     * @param bThreshold Some value >= 0.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }
}

