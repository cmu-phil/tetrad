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
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * <p>Implements an interpretation of the LiNGAM algorithm. The reference is here:</p>
 *
 * <p>Shimizu, S., Hoyer, P. O., Hyvärinen, A., Kerminen, A., &amp; Jordan, M. (2006).
 * A linear non-Gaussian acyclic model for causal discovery. Journal of Machine Learning
 * Research, 7(10).</p>
 *
 * <p>The focus for this implementation was making super-simple code, not so much
 * because the method was trivial (it's not) but out of an attempt to compartmentalize.
 * Bootstrapping and other forms of improving the estimate of B Hat were not addressed.
 * A parameter is included to set whether an acyclic model is to be enforced; it this
 * is set to true, then as in the above reference small coefficients are set to zero
 * until an acyclic model is achieved. Even without this fpr acyclic inputs, in our
 * esperience, it does tend to produce an acyclic model in simulation, though for cyclic
 * inputs it tends to produce the cyclic DAG, so long as coefficients are bounded somewhat
 * away from zero. No attempt was made to implement DirectLiNGAM since it was tangential
 * to the effort to get LiNG-D to work. Only a passing effort was made to ensure good
 * performance on real data. There is an additional tuning parameters (in addition to the
 * FastICA paramters that are exposed), a threshold on the absolute value of entries in the
 * B Hat matrix for finding edges in the final graph.</p>
 *
 * <p>We are using the Hungarian Algorithm to find the best diagonal for the W matrix
 * and are not doing any searches over all permutations.</p>
 *
 * <p>This class is not configured to respect knowledge of forbidden and required
 * edges.</p>
 *
 * @author josephramsey
 * @see LingD
 */
public class Lingam {
    private double bThreshold = 0.1;
    private boolean acyclicityGuaranteed = true;

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
        Matrix scaledBHat = LingD.getScaledBHat(bestPair, bThreshold);

        if (!acyclicityGuaranteed) {
            return scaledBHat;
        }

        List<Node> dummyVars = new ArrayList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            dummyVars.add(new GraphNode("dummy" + i));
        }

        class Record {
            double coef;
            int i;
            int j;
        }

        LinkedList<Record> coefs = new LinkedList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                if (i != j && scaledBHat.get(i, j) != 0.0) {
                    Record record = new Record();
                    record.coef = scaledBHat.get(i, j);
                    record.i = i;
                    record.j = j;

                    coefs.add(record);
                }
            }
        }

        coefs.sort(Comparator.comparingDouble(o -> abs(o.coef)));

        while (true) {
            Record coef = coefs.removeFirst();
            scaledBHat.set(coef.i, coef.j, 0.0);
            if (LingD.isAcyclic(scaledBHat, dummyVars)) {
                return scaledBHat;
            }
        }
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

    /**
     * Whether or not the LiNGAM algorithm is guaranteed to produce an acyclic graph. This is
     * is implemnted by setting small coefficients in B hat to zero until an acyclic model is
     * found.
     *
     * @param acyclicityGuaranteed True if so.
     */
    public void setAcyclicityGuaranteed(boolean acyclicityGuaranteed) {
        this.acyclicityGuaranteed = acyclicityGuaranteed;
    }
}

