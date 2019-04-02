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

    //================================CONSTRUCTORS==========================//

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    public Graph search(DataSet data) {
        data = DataUtils.center(data);

        TetradMatrix X = data.getDoubleData();
        FastIca fastIca = new FastIca(X, 30);
        fastIca.setVerbose(false);
        FastIca.IcaResult result11 = fastIca.findComponents();
        TetradMatrix W = result11.getW();

        System.out.println("W = " + W);

        PermutationGenerator gen1 = new PermutationGenerator(W.columns());
        int[] perm1 = new int[0];
        double sum1 = Double.POSITIVE_INFINITY;
        int[] choice1;

        while ((choice1 = gen1.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.columns(); i++) {
                final double wij = W.get(i, choice1[i]);
                sum += 1.0 / abs(wij);
            }

            if (sum < sum1) {
                sum1 = sum;
                perm1 = Arrays.copyOf(choice1, choice1.length);
            }
        }

        TetradMatrix WTilde = W.getSelection(perm1, perm1);

        System.out.println("WTilde before normalization = " + WTilde);

        for (int i = 0; i < WTilde.rows(); i++) {
            for (int j = 0; j < WTilde.columns(); j++) {
                WTilde.set(i, j, WTilde.get(j, i) / WTilde.get(i, i));
            }
        }

        System.out.println("WTilde after normalization = " + WTilde);

        final int m = data.getNumColumns();
        TetradMatrix B = TetradMatrix.identity(m).minus(WTilde);

        System.out.println("B = " + B);

        PermutationGenerator gen2 = new PermutationGenerator(B.rows());
        int[] perm2 = new int[0];
        double sum2 = Double.POSITIVE_INFINITY;
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.rows(); i++) {
                for (int j = i + 1; j < W.columns(); j++) {
                    final double c = B.get(choice2[i], choice2[j]);
                    sum += abs(c);
                }
            }

            if (sum < sum2) {
                sum2 = sum;
                perm2 = Arrays.copyOf(choice2, choice2.length);
            }
        }

        TetradMatrix BTilde = B.getSelection(perm2, perm2);

        System.out.println("BTilde = " + BTilde);

        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
        score.setPenaltyDiscount(penaltyDiscount);
        Fges fges = new Fges(score);

        IKnowledge knowledge = new Knowledge2();
        final List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            knowledge.addToTier(i + 1, variables.get(perm2[i]).getName());
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

}

