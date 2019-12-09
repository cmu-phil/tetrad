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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.PermutationGenerator;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.StrictMath.abs;

/**
 * Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear nongaussian acyclic model for
 * causal discovery, JMLR 7 (2006). Largely follows the Matlab code.
 *
 * We use FGES with knowledge of causal order for the pruning step.
 *
 * @author Joseph Ramsey
 */
public class Lingam {
    private double penaltyDiscount = 2;
    private double fastIcaA = 1.1;
    private int fastIcaMaxIter = 2000;
    private double fastIcaTolerance = 1e-6;

    //================================CONSTRUCTORS==========================//

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    public Graph search(DataSet data) {
        TetradMatrix X = data.getDoubleData();
        X = DataUtils.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.rows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.PARALLEL);
        fastIca.setTolerance(fastIcaTolerance);
        fastIca.setFunction(FastIca.EXP);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(fastIcaA);
        FastIca.IcaResult result11 = fastIca.findComponents();
        TetradMatrix W = result11.getW();

        PermutationGenerator gen1 = new PermutationGenerator(W.columns());
        int[] perm1 = new int[0];
        double sum1 = Double.NEGATIVE_INFINITY;
        int[] choice1;

        while ((choice1 = gen1.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.columns(); i++) {
                final double wii = W.get(choice1[i], i);
                sum += abs(wii);
            }

            if (sum > sum1) {
                sum1 = sum;
                perm1 = Arrays.copyOf(choice1, choice1.length);
            }
        }

        int[] cols = new int[W.columns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        TetradMatrix WTilde = W.getSelection(perm1, cols);

        TetradMatrix WPrime = WTilde.copy();

        for (int i = 0; i < WPrime.rows(); i++) {
            for (int j = 0; j < WPrime.columns(); j++) {
                WPrime.assignRow(i, WTilde.getRow(i).scalarMult(1.0 / WTilde.get(i, i)));
            }
        }

//        System.out.println("WPrime = " + WPrime);

        final int m = data.getNumColumns();
        TetradMatrix BHat = TetradMatrix.identity(m).minus(WPrime);

        PermutationGenerator gen2 = new PermutationGenerator(BHat.rows());
        int[] perm2 = new int[0];
        double sum2 = Double.NEGATIVE_INFINITY;
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.rows(); i++) {
                for (int j = 0; j < i; j++) {
                    final double c = BHat.get(choice2[i], choice2[j]);
                    sum += abs(c);
                }
            }

            if (sum > sum2) {
                sum2 = sum;
                perm2 = Arrays.copyOf(choice2, choice2.length);
            }
        }

//        TetradMatrix BTilde = BHat.getSelection(perm2, perm2);
//
//        System.out.println("BTilde = " + BTilde);

        final SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(penaltyDiscount);
        Fges fges = new Fges(score);

        IKnowledge knowledge = new Knowledge2();
        final List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            knowledge.addToTier(i, variables.get(perm2[i]).getName());
        }

        fges.setKnowledge(knowledge);

        final Graph graph = fges.search();
        System.out.println("graph Returning this graph: " + graph);
        return graph;
    }

    //================================PUBLIC METHODS========================//

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setFastIcaA(double fastIcaA) {
        this.fastIcaA = fastIcaA;
    }

    public void setFastMaxIter(int maxIter) {
        this.fastIcaMaxIter = maxIter;
    }

    public void setFastIcaTolerance(double tolerance) {
        this.fastIcaTolerance = tolerance;
    }
}

