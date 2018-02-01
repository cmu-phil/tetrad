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
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.StrictMath.abs;

/**
 * Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear nongaussian acyclic model for
 * causal discovery, JMLR 7 (2006). Largely follows the Matlab code.
 * <p>
 * <p>Note: This code is currently broken; please do not use it until it's fixed. 11/24/2015</p>
 *
 * @author Gustavo Lacerda
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
        List<Node> nodes = data.getVariables();

        CausalOrder result = estimateCausalOrder(data);
        int[] perm = result.getPerm();

        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
        score.setPenaltyDiscount(penaltyDiscount);
        Fges fges = new Fges(score);

        IKnowledge knowledge = new Knowledge2();
        final List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            knowledge.addToTier(i + 1, variables.get(perm[i]).getName());
        }

        fges.setKnowledge(knowledge);

        final Graph graph = fges.search();
        System.out.println("graph Returning this graph: " + graph);
        return graph;
    }

    //================================PUBLIC METHODS========================//

    private CausalOrder estimateCausalOrder(DataSet dataSet) {
        TetradMatrix X = dataSet.getDoubleData();
        FastIca fastIca = new FastIca(X, 20);
        fastIca.setVerbose(false);
        fastIca.setAlgorithmType(FastIca.DEFLATION);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setTolerance(1e-10);
        FastIca.IcaResult result = fastIca.findComponents();
        TetradMatrix w = result.getW();
        TetradMatrix k = result.getK();

        TetradMatrix W = w.transpose().times(k);// k.times(w.transpose());
        System.out.println("W = " + W);

        TetradLogger.getInstance().log("lingamDetails", "\nW " + W);

//        PermutationGenerator gen = new PermutationGenerator(W.columns());
//        int[] WPerm = new int[0];
//        double WSum = Double.POSITIVE_INFINITY;
//        int[] perm;
//
//        while ((perm = gen.next()) != null) {
//            double sum = 0.0;
//
//            for (int i = 0; i < W.rows(); i++) {
//                final double Wii = W.get(perm[i], i);
//                sum += 1.0 / abs(Wii);
//            }
//
//            if (sum < WSum) {
//                WSum = sum;
//                WPerm = Arrays.copyOf(perm, perm.length);
//            }
//        }

        TetradMatrix Wp = new TetradMatrix(W);

//        for (int i = 0; i < WPerm.length; i++) {
//            for (int j = 0; j < WPerm.length; j++) {
//                Wp.set(WPerm[i], j, W.get(i, j));
//            }
//        }

//        for (int i = 0; i < WPerm.length; i++) {
//            double diag = Wp.get(i, i);
//
//            for (int j = 0; j < WPerm.length; j++) {
//                Wp.set(i, j, Wp.get(i, j) / diag);
//            }
//        }

        final int m = dataSet.getNumColumns();
        TetradMatrix B = TetradMatrix.identity(m).minus(Wp.transpose());

        class Entry {
            private double entry;
            private int i;
            private int j;

            public Entry(double entry, int i, int j) {
                this.entry = entry;
                this.i = i;
                this.j = j;
            }

            public double getEntry() {
                return entry;
            }

            public int getI() {
                return i;
            }

            public int getJ() {
                return j;
            }

            public String toString() {
                return ("entry = " + entry + " i = " + i + " j = " + j);
            }
        }

        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < B.rows(); i++) {
            for (int j = 0; j < B.columns(); j++) {
                entries.add(new Entry(B.get(i, j), i, j));
            }
        }

        entries.sort(Comparator.comparingDouble(o -> abs(o.getEntry())));

        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) indices.add(i);

        List<Entry> min = new ArrayList<>();
        Set<Integer> iIndices = new HashSet<>();
        Set<Integer> jIndices = new HashSet<>();

        for (Entry entry : entries) {
            int i = entry.getI();
            int j = entry.getJ();

            if (!iIndices.contains(i) && !jIndices.contains(j)){
                min.add(entry);
                iIndices.add(i);
                jIndices.add(j);

                if (iIndices.equals(indices)) break;
            }
        }

        for (Entry entry : entries) {
            System.out.println(entry);
        }

        System.out.println("-------");

        for (Entry entry : min) {
            System.out.println(entry);
        }



//        int m = B.rows() * (B.rows() + 1) / 2;
//
//        double cutoff = entries.get(m);
//
//        for (int i = 0; i < B.rows(); i++) {
//            for (int j = 0; j < B.columns(); j++) {
//                if (abs(B.get(i, j)) > abs(cutoff)) {
//                    B.set(i, j, 0.0);
//                }
//            }
//        }

        System.out.println("B = " + B);

//        PermutationGenerator gen = new PermutationGenerator(W.columns());
//        int[] WPerm = new int[0];
//        double WSum = Double.POSITIVE_INFINITY;
//        int[] perm;
//
//        while ((perm = gen.next()) != null) {
//            double sum = 0.0;
//
//            for (int i = 0; i < W.rows(); i++) {
//                final double Wii = W.get(perm[i], i);
//                sum += 1.0 / abs(Wii);
//            }
//
//            if (sum < WSum) {
//                WSum = sum;
//                WPerm = Arrays.copyOf(perm, perm.length);
//            }
//        }

        PermutationGenerator gen2 = new PermutationGenerator(B.rows());
        int[] betaPerm = new int[0];
        double utSum = Double.POSITIVE_INFINITY;
        int[] choice;

        while ((choice = gen2.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.rows(); i++) {
                final double c = B.get(i, i);
                sum += c * c;
            }

            if (sum < utSum) {
                utSum = sum;
                betaPerm = Arrays.copyOf(choice, choice.length);
            }
        }

        return new CausalOrder(betaPerm);
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public static class CausalOrder {
        private int[] perm;

        CausalOrder(int[] perm) {
            this.perm = perm;
        }

        int[] getPerm() {
            return perm;
        }
    }

}


