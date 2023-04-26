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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.LingD;
import edu.cmu.tetrad.search.Lingam;
import edu.cmu.tetrad.search.NRooks;
import edu.cmu.tetrad.search.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Joseph Ramsey
 */
public class TestLing {

    @Test
    public void test1() {

        // Testing LiNGAM and LiNG-D on a simple 6-node 6-edge example. This
        // uses Exp(1) non-Gaussian errors and otherwise default parameters.
        // We're not using bootstrapping yet here, which could make the result
        // more accurate.
        long seed = 402030204L;
        RandomUtil.getInstance().setSeed(seed);
        System.out.println("Seed = " + seed + "L");
        System.out.println();

        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_MEASURES, 6);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.SIMULATION_ERROR_TYPE, 3); // Exp(1)
        parameters.set(Params.SIMULATION_PARAM1, 1);
//        parameters.set(Params.SIMULATION_PARAM2, 1);

        SemSimulation sim = new SemSimulation(new RandomForward());
        sim.createData(parameters, true);
        DataSet dataSet = (DataSet) sim.getDataModel(0);
        Graph g = sim.getTrueGraph(0);
        System.out.println("True graph = " + g);

        // First we use ICA to estimate the W matrix.
        Matrix W = Lingam.estimateW(dataSet, 5000, 1e-6, 1.2);
        System.out.println("W = " + W);

        // We then apply LiNGAM with a prune factor of .3. We should get a mostly correct DAG
        // back. The "prune factor" is a threshold for the B Hat matrix below which values are
        // sent to zero in absolute value, so that only coefficients whose absolute values
        // exceed the prune factor are reported as edges in the model. Self-loops are not reported
        // in the printed graphs but are assumed ot exist for purposes of this algorithm. The
        // B Hat matrices are scaled so that self-loops always have strength 1.
        System.out.println("LiNGAM");
        double pruneFactor = 0.3;
        System.out.println("Prune factor = " + pruneFactor);

        Lingam lingam = new Lingam();
        lingam.setPruneFactor(pruneFactor);
        Graph g2 = lingam.search(W, dataSet.getVariables());
        System.out.println("Lingam graph = " + g2);

        // Next we try LiNG-D.
        System.out.println("LiNG-D");

        // Here we send any small value in W to 0 that has absolute value below a given threshold.
        double wThreshold = 0.5;
        System.out.println("wThreshold = " + wThreshold);

        LingD ling = new LingD();
        ling.setWThreshold(wThreshold);

        // We generate pairs of column permutations (solving the constriained N Rooks problem) with their
        // associated column-permuted W thresholded W matrices. For the constrained N rooks problme we
        // are allowed to place a "rook" at any position in the thresholded W matrix that is not zero.
        List<PermutationMatrixPair> pairs = ling.search(W);

        // Then for each N Rook solution we print stuff.
        for (PermutationMatrixPair pair : pairs) {

            // We print the B Hat matrix; this is the matrix of coefficients for the implied linear moodel.
            System.out.println("Model = " + LingD.getBHat(pair));

            // We print the corresponding graph.
            Graph graph = LingD.getGraph(pair, dataSet.getVariables());

            System.out.println("Graph = " + graph);
            boolean stable = LingD.isStable(pair);

            // Finally we print a judgment of whether the BHat model is stable and cyclic.
            System.out.println((stable ? "Is Stable" : "Not stable") + " cyclic = " + graph.paths().existsDirectedCycle());
        }
    }

    @Test
    public void testNRooks() {

        // Print all N Rook solutions for a board of size 24 with one square marked
        // as non-allowable. There should be 4 solutions.

        int p = 3;
        boolean[][] allowableBoard = new boolean[p][p];
        for (boolean[] row : allowableBoard) Arrays.fill(row, true);
        allowableBoard[0][0] = false;

        for (boolean[] booleans : allowableBoard) {
            System.out.println();
            for (int j = 0; j < allowableBoard[0].length; j++) {
                System.out.print((booleans[j] ? 1 : 0) + " ");
            }
        }

        System.out.println();

        List<int[]> solutions = NRooks.nRooks(allowableBoard);

        // Each solution is a permutation of the columns of the board, one integer
        // for each row indicating where to place the rook in that row.
        NRooks.printSolutions(solutions);

        // There should be 4 solutions.
        assertEquals(4, solutions.size());
    }
}


