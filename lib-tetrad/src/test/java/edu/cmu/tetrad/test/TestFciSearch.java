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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Tests the FciSearch class.
 *
 * @author Joseph Ramsey
 */
public class TestFciSearch extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestFciSearch(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    /**
     * Tests a specific search. (See code for details.)
     */
    public void testSearch1() {
        System.out.println("Test1");
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1o-oX2,X1o-oX3,X2-->X4,X3-->X4", new Knowledge2()); // With Jiji's R6.
    }

    /**
     * Tests a specific search. (See code for details.)
     */
    public void testSearch2() {
        System.out.println("Test2");
        checkSearch("Z1-->X,Z2-->X,X-->Y", "Z1o->X,Z2o->X,X-->Y", new Knowledge2());
    }


    /**
     * Basic discriminating path checker.
     */
    public void testSearch3() {
        System.out.println("Test3");
        checkSearch("A-->C,B-->C,B-->D,C-->D", "Ao->C,Bo->C,B-->D,C-->D", new Knowledge2());
    }

    /**
     * Basic discriminating path checker.
     */
    public void testSearch3a() {
        System.out.println("Test3");
        checkSearch("Latent(G),Latent(R),H-->F,F<--G,G-->A,A<--R,R-->C,B-->C,B-->D,C-->D,F-->D,A-->D",
                "Ho->F,F<->A,A<->C,Bo->C,B-->D,C-->D,F-->D,A-->D", new Knowledge2());
    }

    /**
     * Basic discriminating path checker with an extra variable thrown in. (For some reason, FCI was screwing up on
     * this.)
     */
    public void testSearch4() {
        System.out.println("Test4");
        checkSearch("A-->C,B-->C,B-->D,C-->D,E", "Ao->C,Bo->C,B-->D,C-->D,E", new Knowledge2());
    }

    /**
     * FCI was breaking with 1 or 2 variables.
     */
    public void testSearch5() {
        System.out.println("Test5");
        checkSearch("A-->B", "Ao-oB", new Knowledge2());
    }

    /**
     * A specific graph. This is the graph on p. 137 (2000) or p. 180 (1993) of CPS.
     * <p/>
     * Note. No it's not. It's a completely different graph. Draw it. jdramsey 2/23/2010
     */
    public void testSearch6() {
        System.out.println("Test6");

//        checkSearch("Latent(E),Latent(G),E-->D,E-->H,G-->H,G-->L,D-->L,D-->M," +
//                "H-->M,L-->M,S-->D,I-->S,P-->S",
//                "D<->H,H<->L,D-->L,D-->M,H-->M,L-->M,S-->D,Io->S,Po->S", new Knowledge2());

//        checkSearch("Latent(E),Latent(G),E-->D,E-->H,G-->H,G-->L,D-->L,D-->M," +
//                "H-->M,L-->M,S-->D,I-->S,P-->S",
//                "D<-oH,Ho-oL,D-->L,D-->M,Ho-oM,Lo-oM,S-->D,Io->S,Po->S", new Knowledge2());


        checkSearch("Latent(E),Latent(G),E-->D,E-->H,G-->H,G-->L,D-->L,D-->M," +
                "H-->M,L-->M,S-->D,I-->S,P-->S",
                "D<->H,D-->L,D-->M,H<->L,H-->M,Io->S,L-->M,Po->S,S-->D", new Knowledge2());
    }

    /**
     * A specific graph. This is the graph on p. 5 of Danks, Learning Integrated Structure from Distributed Databases
     * with Overlapping Variables.
     */
    public void testSearch7() {
        System.out.println("Test7");
        checkSearch("X-->Z,Y-->Z,Z-->B,B-->A,C-->A",
                "Xo->Z,Yo->Z,Z-->B,B-->A,Co->A", new Knowledge2());
    }

    /**
     * A specific graph. This is the test case from p. 142-144 that tests the possible Dsep step of FCI. This doesn't
     * work in the optimized FCI algorithm. It works in the updated version (FciSearch).  (ekorber)
     */
    public void testSearch8() {
        System.out.println("Test8");
        checkSearch(
                "Latent(T1),Latent(T2),T1-->A,T1-->B,B-->E,F-->B,C-->F,C-->H," +
                        "H-->D,D-->A,T2-->D,T2-->E",
//                "A<->B,B-->E,Fo->B,Fo-oC,Co-oH,Ho->D,D<->E,D-->A", new Knowledge2()); // Left out E<->A.
                "A<->B,B-->E,Co-oH,D-->A,E<->A,E<->D,Fo->B,Fo-oC,Ho->D", new Knowledge2());
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    public void testSearch9() {
        System.out.println("Test9");
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "Ao->D,Ao-oB,Bo->D,Co->D,D-->E", new Knowledge2());
    }


    public void testSearch10() {
        System.out.println("Test10");
        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o->X2,X3o->X2", new Knowledge2());

        List<String> varNames = new ArrayList<String>();
        varNames.add("X1");
        varNames.add("X2");
        varNames.add("X3");

        IKnowledge knowledge = new Knowledge2(varNames);
        knowledge.addToTier(1, "X1");
        knowledge.addToTier(1, "X2");
        knowledge.addToTier(2, "X3");

        System.out.println(knowledge);

        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o-oX2,X2o->X3", knowledge);
    }

    public void testSearch11() {
        System.out.println("Test11");
        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o->X2,X3o->X4,X2<->X4", new Knowledge2());

        Knowledge2 knowledge = new Knowledge2();
        knowledge.setRequired("X2", "X4");

        System.out.println(knowledge);

        assertTrue(knowledge.isRequired("X2", "X4"));

        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o-oX2,X3o->X4,X2-->X4", knowledge);
    }

    public void testMarginal() {
        System.out.println("Marginal Test");
        Node A = new ContinuousVariable("X1");
        Node B = new ContinuousVariable("X2");
        Node C = new ContinuousVariable("X3");
        Node D = new ContinuousVariable("X4");

        Graph G1 = new EdgeListGraph(Arrays.asList(A, B, C, D));
        G1.addEdge(new Edge(A, B, Endpoint.TAIL, Endpoint.ARROW));
        G1.addEdge(new Edge(D, B, Endpoint.TAIL, Endpoint.ARROW));
        G1.addEdge(new Edge(A, C, Endpoint.TAIL, Endpoint.ARROW));
        G1.addEdge(new Edge(C, D, Endpoint.TAIL, Endpoint.ARROW));
        Fci fci1 = new Fci(new IndTestDSep(G1));
        System.out.println(fci1.search());
        Fci fci2 = new Fci(new IndTestDSep(G1), Arrays.asList(A, B, C));
        System.out.println(fci2.search());
        Fci fci3 = new Fci(new IndTestDSep(G1), Arrays.asList(A, B, D));
        System.out.println(fci3.search());
        Fci fci4 = new Fci(new IndTestDSep(G1), Arrays.asList(A, D, C));
        System.out.println(fci4.search());
        Fci fci5 = new Fci(new IndTestDSep(G1), Arrays.asList(B, D, C));
        System.out.println(fci5.search());
    }

    public void rtestSearch4() {

        int numVars = 40;
        int numEdges = 40;
        int sampleSize = 1000;
        boolean latentDataSaved = false;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 10, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        SemPm bayesPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(bayesPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, latentDataSaved);
        IndependenceTest test = new IndTestFisherZ(dataSet, 0.1);

//        BayesPm bayesPm = new BayesPm(trueGraph);
//        MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
//        RectangularDataSet dataSet = bayesIm.simulateData(sampleSize);

        Fci search = new Fci(test);

        // Run search
        Graph resultGraph = search.search();

        // PrintUtil out problem and graphs.
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
    }

    public void rtestSearch5() {

        int numVars = 30;
        int numEdges = 30;
        int sampleSize = 1000;
        boolean latentDataSaved = false;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 10, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        System.out.println("\n# vars = " + numVars);

        SemPm bayesPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(bayesPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, latentDataSaved);
        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

//        BayesPm bayesPm = new BayesPm(trueGraph);
//        MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
//        RectangularDataSet dataSet = bayesIm.simulateData(sampleSize);

        System.out.println("\nUsing local test:");
        Cfci search2 = new Cfci(test);
        Graph resultGraph2 = search2.search();
//        search2.search();
        System.out.println("Elapsed time = " + (search2.getElapsedTime() / 1000.));
        System.out.println(resultGraph2);

        System.out.println("\nUsing sepsets:");
        Fci search = new Fci(test);
        Graph resultGraph = search.search();
//        search.search();
        System.out.println("Elapsed time = " + (search.getElapsedTime() / 1000.));
        System.out.println(resultGraph);

        // PrintUtil out problem and graphs.
//        System.out.println("\nResult graph:");
//        System.out.println(resultGraph);
    }

    public void rtestSearch6() {

        int numVars = 80;
        int numEdges = 80;
        int sampleSize = 1000;
        boolean latentDataSaved = false;
        int numLatents = 40;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, numLatents, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        SemPm bayesPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(bayesPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, latentDataSaved);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

//        BayesPm bayesPm = new BayesPm(trueGraph);
//        MlBayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
//        RectangularDataSet dataSet = bayesIm.simulateData(sampleSize);

        Cfci search = new Cfci(test);

        // Run search
        Graph resultGraph = search.search();

        // PrintUtil out problem and graphs.
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */


    private void checkSearch(String inputGraph, String outputGraph, IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        Fci fci = new Fci(independence);
//        Cfci fci = new Cfci(independence);
        fci.setPossibleDsepSearchDone(true);
        fci.setCompleteRuleSetUsed(true);
        fci.setKnowledge(knowledge);
        fci.setMaxPathLength(-1);

        // Run search
        Graph resultGraph = fci.search();

        // Build comparison graph.
        Graph compareGraph = new EdgeListGraph(GraphConverter.convert(outputGraph));

        // PrintUtil out problem and graphs.
        System.out.println("FCI:");
        System.out.println("Input graph:");
        System.out.println(graph);
        System.out.println("Result graph:");
        System.out.println(resultGraph);
        System.out.println("True graph:");
        System.out.println(compareGraph);

        // Do test (output of FCI search equals true graph)
        resultGraph.setUnderLineTriples(compareGraph.getUnderLines());
        resultGraph.setDottedUnderLineTriples(compareGraph.getDottedUnderlines());

        resultGraph = GraphUtils.replaceNodes(resultGraph, compareGraph.getNodes());

        assertTrue(compareGraph.equals(resultGraph));

//        // Redo tests for CFCI.
//        Cfci cfci = new Cfci(independence);
//        cfci.setKnowledge(knowledge);
//
//        // Run search
//        Graph _resultGraph = cfci.search();
//
//        // PrintUtil out problem and graphs.
//        System.out.println("CFCI:");
//        System.out.println("Input graph:");
//        System.out.println(graph);
//        System.out.println("Result graph:");
//        System.out.println(_resultGraph);
//        System.out.println("True graph:");
//        System.out.println(compareGraph);
//
//        // Do test (output of CFCI search equals true graph)
//        resultGraph.setUnderLineTriples(compareGraph.getUnderLines());
//        resultGraph.setDottedUnderLineTriples(compareGraph.getDottedUnderlines());
//
//        assertTrue(compareGraph.equals(resultGraph));
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestFciSearch.class);
    }
}





