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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Ling;
import edu.cmu.tetrad.search.NRooks;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author Joseph Ramsey
 */
public class TestLing {

    @Test
    public void test1() {
        Graph g = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);

        System.out.println("True graph = " + g);

        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(5000, false);
        Ling alg = new Ling(dataSet);
        alg.setThreshold(0.5);
        List<double[][]> models = alg.search();

        for (double[][] model : models) {
            Graph graph = Ling.getGraph(model, dataSet.getVariables());
//            boolean stable = Ling.isStable(model);
//            System.out.println((stable ? "Is Stable" : "Not stable") + " cyclic = " + graph.paths().existsDirectedCycle());
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
}


