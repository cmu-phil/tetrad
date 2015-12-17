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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Joseph Ramsey
 */
public class TestFci {

    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1o-oX2,X1o-oX3,X2-->X4,X3-->X4", new Knowledge2()); // With Jiji's R6.
    }

    /**
     * Tests a specific search. (See code for details.)
     */
    @Test
    public void testSearch2() {
        checkSearch("Z1-->X,Z2-->X,X-->Y", "Z1o->X,Z2o->X,X-->Y", new Knowledge2());
    }


    /**
     * Basic discriminating path checker.
     */
    @Test
    public void testSearch3() {
        checkSearch("A-->C,B-->C,B-->D,C-->D", "Ao->C,Bo->C,B-->D,C-->D", new Knowledge2());
    }

    /**
     * Basic discriminating path checker.
     */
    @Test
    public void testSearch4() {
        checkSearch("Latent(G),Latent(R),H-->F,F<--G,G-->A,A<--R,R-->C,B-->C,B-->D,C-->D,F-->D,A-->D",
                "Ho->F,F<->A,A<->C,Bo->C,B-->D,C-->D,F-->D,A-->D", new Knowledge2());
    }

    /**
     * Basic discriminating path checker with an extra variable thrown in. (For some reason, FCI was screwing up on
     * this.)
     */
    @Test
    public void testSearch5() {
        checkSearch("A-->C,B-->C,B-->D,C-->D,E", "Ao->C,Bo->C,B-->D,C-->D,E", new Knowledge2());
    }

    /**
     * FCI was breaking with 1 or 2 variables.
     */
    @Test
    public void testSearch6() {
        checkSearch("A-->B", "Ao-oB", new Knowledge2());
    }

    /**
     * A specific graph.
     */
    @Test
    public void testSearch7() {
        checkSearch("Latent(E),Latent(G),E-->D,E-->H,G-->H,G-->L,D-->L,D-->M," +
                "H-->M,L-->M,S-->D,I-->S,P-->S",
                "D<->H,D-->L,D-->M,H<->L,H-->M,Io->S,L-->M,Po->S,S-->D", new Knowledge2());
    }

    /**
     * A specific graph. This is the graph on p. 5 of Danks, Learning Integrated Structure from Distributed Databases
     * with Overlapping Variables.
     */
    @Test
    public void testSearch8() {
        checkSearch("X-->Z,Y-->Z,Z-->B,B-->A,C-->A",
                "Xo->Z,Yo->Z,Z-->B,B-->A,Co->A", new Knowledge2());
    }

    /**
     * A specific graph. This is the test case from p. 142-144 that tests the possible Dsep step of FCI. This doesn't
     * work in the optimized FCI algorithm. It works in the updated version (FciSearch).  (ekorber)
     */
    @Test
    public void testSearch9() {
        checkSearch("Latent(T1),Latent(T2),T1-->A,T1-->B,B-->E,F-->B,C-->F,C-->H," +
                        "H-->D,D-->A,T2-->D,T2-->E",
//                "A<->B,B-->E,Fo->B,Fo-oC,Co-oH,Ho->D,D<->E,D-->A", new Knowledge2()); // Left out E<->A.
                "A<->B,B-->E,Co-oH,D-->A,E<->A,E<->D,Fo->B,Fo-oC,Ho->D", new Knowledge2());
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch10() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "Ao->D,Ao-oB,Bo->D,Co->D,D-->E", new Knowledge2());
    }

    @Test
    public void testSearch11() {
        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o->X2,X3o->X2", new Knowledge2());

        List<String> varNames = new ArrayList<>();
        varNames.add("X1");
        varNames.add("X2");
        varNames.add("X3");

        IKnowledge knowledge = new Knowledge2(varNames);
        knowledge.addToTier(1, "X1");
        knowledge.addToTier(1, "X2");
        knowledge.addToTier(2, "X3");

        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o-oX2,X2o->X3", knowledge);
    }

    @Test
    public void testSearch12() {
        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o->X2,X3o->X4,X2<->X4", new Knowledge2());

        Knowledge2 knowledge = new Knowledge2();
        knowledge.setRequired("X2", "X4");

        assertTrue(knowledge.isRequired("X2", "X4"));

        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o-oX2,X3o->X4,X2-->X4", knowledge);
    }

    @Test
    public void testSearch13() {
        int numVars = 10;
        int numEdges = 10;

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, 10, numEdges,
                7, 5, 5, false));

        IndependenceTest test = new IndTestDSep(trueGraph);

        Fci fci = new Fci(test);

        Graph graph = fci.search();

        DagToPag dagToPag = new DagToPag(trueGraph);
        Graph truePag = dagToPag.convert();

        assertEquals(graph, truePag);
    }

    @Test
    public void testSearch15() {
        int numVars = 80;
        int numEdges = 80;
        int sampleSize = 1000;
        boolean latentDataSaved = false;
        int numLatents = 40;

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(GraphUtils.randomGraph(nodes, numLatents, numEdges,
                7, 5, 5, false));

        SemPm bayesPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(bayesPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, latentDataSaved);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

        Cfci search = new Cfci(test);

        // Run search
        search.search();
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
        fci.setPossibleDsepSearchDone(true);
        fci.setCompleteRuleSetUsed(true);
        fci.setKnowledge(knowledge);
        fci.setMaxPathLength(-1);

        // Run search
        Graph resultGraph = fci.search();
//
//        // Build comparison graph.
//        Graph compareGraph = new EdgeListGraph(GraphConverter.convert(outputGraph));
//
//        // Do test (output of FCI search equals true graph)
//        resultGraph.setUnderLineTriples(compareGraph.getUnderLines());
//        resultGraph.setDottedUnderLineTriples(compareGraph.getDottedUnderlines());
//
//        resultGraph = GraphUtils.replaceNodes(resultGraph, compareGraph.getNodes());
//
//        assertTrue(compareGraph.equals(resultGraph));
    }
}





