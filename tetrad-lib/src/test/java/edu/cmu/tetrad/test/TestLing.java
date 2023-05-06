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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.LingD;
import edu.cmu.tetrad.search.Lingam;
import edu.cmu.tetrad.search.utils.NRooks;
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
        // Please don't change this seed--this is set up as an actual unit test
        // for this example.
        long seed = 402030204L;
        RandomUtil.getInstance().setSeed(seed);
        System.out.println("Seed = " + seed + "L");
        System.out.println();

        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_MEASURES, 6);
        parameters.set(Params.AVG_DEGREE, 2);

        // Using Exp(1) for the non-Gaussian error for all variables.
        parameters.set(Params.SIMULATION_ERROR_TYPE, 3);
        parameters.set(Params.SIMULATION_PARAM1, 1);

        SemSimulation sim = new SemSimulation(new RandomForward());
        sim.createData(parameters, true);
        DataSet dataSet = (DataSet) sim.getDataModel(0);
        Graph trueGraph = sim.getTrueGraph(0);
        System.out.println("True graph = " + trueGraph);

//        // First we use ICA to estimate the W matrix.
//        Matrix W = LingD.estimateW(dataSet, 5000, 1e-6, 1.2);

        // We then apply LiNGAM with a W threshold of .3. We should get a mostly correct DAG
        // back. The "W threshold" is a threshold for the B Hat matrix below which values are
        // sent to zero in absolute value, so that only coefficients whose absolute values
        // exceed the W threshold are reported as edges in the model. Self-loops are not reported
        // in the printed graphs but are assumed ot exist for purposes of this algorithm. The
        // B Hat matrices are scaled so that self-loops always have strength 1.
        System.out.println("LiNGAM");

        // We send any small value in W to 0 that has absolute value below a given threshold.
        // We do no further pruning on the B matrix. (The algorithm spec wants us to do both
        // but pruning the W matrix seems to be giving better bHats, and besides in LiNG-D
        // the W matrix is pruned. Could switch though.)
        double bThreshold = 0.25;
        double spineThreshold = 0.5;
        System.out.println("W threshold = " + bThreshold);

        Lingam lingam = new Lingam();
        lingam.setBThreshold(bThreshold);
        lingam.setSpineThreshold(spineThreshold);
        Matrix lingamBhat = lingam.fit(dataSet);
        Graph lingamGraph = LingD.makeGraph(lingamBhat, dataSet.getVariables());
        System.out.println("Lingam graph = " + lingamGraph);

        boolean lingamStable = LingD.isStable(lingamBhat);
        System.out.println(lingamStable ? "Is Stable" : "Not stable");

        lingamGraph = GraphUtils.replaceNodes(lingamGraph, trueGraph.getNodes());
        assertEquals(trueGraph, lingamGraph);

        // For LiNG-D, we can just call the relevant public static methods. This was obviously written
        // by a Matlab person.
        //
        // We generate bHats of column permutations (solving the constrained N Rooks problem) with their
        // associated column-permuted W thresholded W matrices. For the constrained N rooks problme we
        // are allowed to place a "rook" at any position in the thresholded W matrix that is not zero.
        System.out.println("LiNG-D");
        LingD lingD = new LingD();
        lingD.setBThreshold(bThreshold);
        lingD.setSpineThreshold(spineThreshold);
        List<Matrix> bHats = lingD.fit(dataSet);

        if (bHats.isEmpty()) {
            throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");
        }

        System.out.println("Then, for each constrained N Rooks solution, a column permutation of thresholded W:");

        for (Matrix bHat : bHats) {
            System.out.println("BHat = " + bHat);

            Graph lingGraph = LingD.makeGraph(bHat, dataSet.getVariables());
            System.out.println("\nGraph = " + lingGraph);

            boolean stable = LingD.isStable(bHat);
            System.out.println(stable ? "Is Stable" : "Not stable");

            // For this example, there is exactly one graph (or should be, unless the example was changed).
            lingGraph = GraphUtils.replaceNodes(lingGraph, trueGraph.getNodes());
            assertEquals(trueGraph, lingGraph);
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

        // There should be 4 solutions.
        assertEquals(4, solutions.size());
    }
}


