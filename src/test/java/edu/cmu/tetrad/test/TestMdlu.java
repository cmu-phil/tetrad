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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;


/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestMdlu extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestMdlu(String name) {
        super(name);
    }


    public void setUp() throws Exception {
//        TetradLogger.getInstance().addOutputStream(System.out);
//        TetradLogger.getInstance().setForceLog(true);
//        RandomUtil.getInstance().setSeed(-1857293L);

    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    public void testMdlu() {
        for (int i = 0; i < 1; i++) {
//            System.out.println("Round " + i);

            Dag graph = new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false));

            Pc pc = new Pc(new IndTestDSep(graph));
            Graph knowledgePattern = pc.search();

            BayesPm pm = new BayesPm(graph, 2, 4);
            BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
            DataSet data = im.simulateData(1000, false);
            MdluScore mdlu = new MdluScore(data, 1);
            BDeuScore score = new BDeuScore(data);
            score.setSamplePrior(10);
            score.setStructurePrior(0.001);

            Ges ges = new Ges(data);
            ges.setDiscreteScore(score);


            Graph pattern1 = ges.search();

            Ges ges2 = new Ges(data);
            ges2.setDiscreteScore(mdlu);
            Graph pattern2 = ges2.search();

//            System.out.println(knowledgePattern + " " + pattern1);

            Cpc cpc = new Cpc(new IndTestGSquare(data, .01));
//            Jpc cpc = new Jpc(new IndTestGSquare(data, 0.001));

            Graph pattern3 = cpc.search();

            Jpc jpc = new Jpc(new IndTestGSquare(data, 0.0001));

            Graph pattern4 = jpc.search();


            List<Graph> patterns = new ArrayList<Graph>();
            patterns.add(pattern1);
            patterns.add(pattern2);
            patterns.add(pattern3);
            patterns.add(pattern4);

            printErrors(graph, knowledgePattern, patterns);


        }
    }

    private void printErrors(Graph graph, Graph knowledgePattern, List<Graph> patterns) {

        for (Graph pattern : patterns) {

            int adjFp = GraphUtils.adjacenciesComplement(pattern, knowledgePattern).size();
            int adjFn = GraphUtils.adjacenciesComplement(knowledgePattern, pattern).size();
            int orientationErrors = GraphUtils.numDifferentOrientations(pattern, knowledgePattern);
            int directedrientationErrors = GraphUtils.numDifferentOrientationsDirected(pattern, graph);

//        System.out.println("Adjacency FP = " + adjFp1 + ", adjacency FN = " + adjFn1 +
//                ", orientation errors = " + orientationErrors1 +
//                ", directed orientation errors = " + directedrientationErrors1
//        );


            System.out.print(adjFp + "\t" + adjFn + "\t" + orientationErrors +
                    "\t" + directedrientationErrors + "\t"
            );

        }

        System.out.println();
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMdlu.class);
    }
}


