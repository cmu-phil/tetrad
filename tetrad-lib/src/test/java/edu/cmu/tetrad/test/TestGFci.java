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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestGFci {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(1450189593459L);

        int numNodes = 10;
        int numLatents = 5;
        int numEdges = 10;
        int sampleSize = 1000;

//        int numNodes = 3000;
//        int numLatents = 150;
//        int numEdges = 4500;
//        int sampleSize = 1000;

        double alpha = 0.01;
        double penaltyDiscount = 2;
        int depth = -1;
        int maxPathLength = -1;
        boolean possibleDsepDone = true;
        boolean completeRuleSetUsed = false;
        boolean faithfulnessAssumed = true;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = GraphUtils.randomGraphUniform(vars, numLatents, numEdges, 4, 4, 4, false);
//        Graph dag = GraphUtils.randomGraphRandomForwardEdges1(vars, numLatents, numEdges);
//        Graph dag = DataGraphUtils.scaleFreeGraph(vars, numLatents, .05, .05, .05, 3);

        DataSet data;

        LargeSemSimulator simulator = new LargeSemSimulator(dag);
        simulator.setCoefRange(.5, 1.5);
        simulator.setVarRange(1, 3);
        data = simulator.simulateDataAcyclic(sampleSize);
        data = DataUtils.restrictToMeasured(data);

        ICovarianceMatrix cov = new CovarianceMatrix(data);

        IndTestFisherZ independenceTest = new IndTestFisherZ(cov, alpha);

        independenceTest.setAlpha(alpha);

        GFci gFci = new GFci(independenceTest);
        gFci.setVerbose(false);
        gFci.setPenaltyDiscount(penaltyDiscount);
        gFci.setDepth(depth);
        gFci.setMaxPathLength(maxPathLength);
        gFci.setPossibleDsepSearchDone(possibleDsepDone);
        gFci.setCompleteRuleSetUsed(completeRuleSetUsed);
        gFci.setFaithfulnessAssumed(faithfulnessAssumed);
        Graph outGraph = gFci.search();

        final DagToPag dagToPag = new DagToPag(dag);
        dagToPag.setCompleteRuleSetUsed(false);
        dagToPag.setMaxPathLength(maxPathLength);
        Graph truePag = dagToPag.convert();

        outGraph = GraphUtils.replaceNodes(outGraph, truePag.getNodes());

        int[][] counts = SearchGraphUtils.graphComparison(outGraph, truePag, null);

        int[][] expectedCounts = {
                {0, 0, 0, 0, 0, 0},
                {0, 4, 0, 0, 0, 1},
                {0, 0, 3, 0, 0, 1},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }
    }
}





