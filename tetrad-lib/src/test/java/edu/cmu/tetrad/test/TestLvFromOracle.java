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

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.GraspFci;
import edu.cmu.tetrad.search.LvLite;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;

import java.io.File;
import java.util.Date;
import java.util.stream.IntStream;


/**
 * Tests latent variable PAG algorithms from Oracle examples to see if they give the same results as DagToPag.
 *
 * @author josephramsey
 */
public class TestLvFromOracle {

    public static void main(String... args) {
        new TestLvFromOracle().testLvFromOracle();
    }

    public void testLvFromOracle() {
        int numMeasures = 15;
        int numLatents = 4;
        int numEdges = 25;
        int numReps = 50;

        System.out.println("Measures: " + numMeasures);
        System.out.println("Latents: " + numLatents);
        System.out.println("Num Edges: " + numEdges);

        String date = new Date().toString().replace(" ", "_");

        File dir = new File("/Users/josephramsey/Downloads/failed_models_" + date);

        // Make a random graph.
        IntStream.rangeClosed(1, numReps).forEach(rep -> {
            Graph dag = RandomGraph.randomGraph(numMeasures, numLatents, numEdges, 100, 100, 100, false);
            File dir2 = new File(dir, "rep_" + rep);
            dir2.mkdirs();
            File file = new File(dir2, "rep_" + rep + "_true_dag.txt");
            GraphSaveLoadUtils.saveGraph(dag, file, false);
            testAlgorithms(dag, rep, dir, dir2);
        });
    }

    private void testAlgorithms(Graph dag, int rep, File dir, File dir2) {
        MsepTest msepTest = new MsepTest(dag);
        GraphScore score = new GraphScore(dag);
        Graph truePag = GraphTransforms.dagToPag(dag);

//        for (LV_ALGORITHMS algorithm : LV_ALGORITHMS.values()) {
//            Graph estimated;
////            switch (algorithm) {
//////                case FCI -> estimated = new Fci(msepTest).search();
//////                case CFCI -> estimated = new Cfci(msepTest).search();
//////                case FCI_MAX -> estimated = new FciMax(msepTest).search();
//////                case GFCI -> estimated = new GFci(msepTest, score).search();
////                case GRASP_FCI -> estimated = new GraspFci(msepTest, score).search();
//////                case LV_LITE -> {
//////                    LvLite lvLite = new LvLite(msepTest, score);
//////                    lvLite.setTuckingAllowed(false);
//////                    estimated = lvLite.search();
//////                }
////                default -> throw new IllegalArgumentException();
////            }}

        LV_ALGORITHMS algorithm = LV_ALGORITHMS.LV_LITE;

        Graph estimated = new LvLite(msepTest, score).search();
//
//           Graph estimated = new GFci(msepTest, score).search();

        boolean equals = estimated.equals(truePag);

        System.out.println("Rep " + rep + " " + algorithm + " equals true PAG: " + equals);

        dir.mkdirs();

        if (!equals) {
            File file = new File(dir, "rep_" + rep + "_" + algorithm + ".txt");
            GraphSaveLoadUtils.saveGraph(estimated, file, false);

            File file2 = new File(dir2, "rep_" + rep + "_" + algorithm + ".txt");
            GraphSaveLoadUtils.saveGraph(estimated, file2, false);

            double ap = new AdjacencyPrecision().getValue(truePag, estimated, null);
            double ar = new AdjacencyRecall().getValue(truePag, estimated, null);
            double ahp = new ArrowheadPrecision().getValue(truePag, estimated, null);
            double ahr = new ArrowheadRecall().getValue(truePag, estimated, null);
            double ahpc = new ArrowheadPrecisionCommonEdges().getValue(truePag, estimated, null);
            double ahprc = new ArrowheadRecallCommonEdges().getValue(truePag, estimated, null);

            System.out.printf("AP = %5.2f, AR = %5.2f, AHP = %5.2f, AHR = %5.2f, AHPC = %5.2f, AHRC = %5.2f\n",
                    ap, ar, ahp, ahr, ahpc, ahprc);

            boolean _equals = estimated.equals(truePag);
//            }
        }
    }

    // BFCI currently cannot be run from Oracle.
    private enum LV_ALGORITHMS {
        FCI, CFCI, FCI_MAX, GFCI, GRASP_FCI, LV_LITE
//        GRASP_FCI
    }
}





