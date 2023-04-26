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

/**
 * @author Joseph Ramsey
 */
public class TestLing {

    @Test
    public void test1() {
        long seed = 402030204L;
        RandomUtil.getInstance().setSeed(seed);
        System.out.println("Seed = " + seed + "L");
        System.out.println();

        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_MEASURES, 6);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.SIMULATION_ERROR_TYPE, 3); // Exp(3)
        parameters.set(Params.SIMULATION_PARAM1, 1);
//        parameters.set(Params.SIMULATION_PARAM2, 1);
        parameters.set(Params.PENALTY_DISCOUNT, 1);

        SemSimulation sim = new SemSimulation(new RandomForward());
        sim.createData(parameters, true);
        DataSet dataSet = (DataSet) sim.getDataModel(0);
        Graph g = sim.getTrueGraph(0);
        System.out.println("True graph = " + g);

        Matrix W = Lingam.estimateW(dataSet, 5000, 1e-6, 1.2);

        System.out.println("W = " + W);

        System.out.println("LiNGAM");

        double pruneFactor = 0.3;

        System.out.println("Prune factor = " + pruneFactor);

        Lingam lingam = new Lingam();
        lingam.setPruneFactor(pruneFactor);
        Graph g2 = lingam.search(W, dataSet.getVariables());
        System.out.println("Lingam graph = " + g2);

        System.out.println("LiNG-D");

        double wThreshold = 0.5;
        System.out.println("wThreshold = " + wThreshold);

        LingD ling = new LingD();
        ling.setWThreshold(wThreshold);
        List<PermutationMatrixPair> pairs = ling.search(W);

        for (PermutationMatrixPair pair : pairs) {
            System.out.println("Model = " + LingD.getBHat(pair));

            Graph graph = LingD.getGraph(pair, dataSet.getVariables());
            boolean stable = LingD.isStable(pair);
            System.out.println((stable ? "Is Stable" : "Not stable") + " cyclic = " + graph.paths().existsDirectedCycle());
            System.out.println(graph);
        }
    }

    @Test
    public void testNRooks() {
        int p = 3;
        boolean[][] allowableBoard = new boolean[p][p];
        for (boolean[] row : allowableBoard) Arrays.fill(row, true);
        allowableBoard[0][2] = false;
        List<int[]> solutions = NRooks.nRooks(allowableBoard);
        NRooks.printSolutions(solutions);
    }

    @Test
    public void testNRooks2() {
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
        NRooks.printSolutions(solutions);
    }
}


