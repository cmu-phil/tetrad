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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
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

//    @Test
    public void testFciAnc() {
        int numMeasures = 50;
        double edgeFactor = 2.0;

        int numRuns = 10;
        double alpha = 0.01;
        double penaltyDiscount = 4.0;
        int numVarsToMarginalize = 5;
        int numLatents = 10;

        System.out.println("num measures = " + numMeasures);
        System.out.println("edge factor = " + edgeFactor);
        System.out.println("alpha = " + alpha);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("num runs = " + numRuns);
        System.out.println("num vars to marginalize = " + numVarsToMarginalize);
        System.out.println("num latents = " + numLatents);

        System.out.println();

        for (int i = 0; i < numRuns; i++) {
            int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            List<Node> nodes = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
            }

            Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(1000, false);

            Graph pag = getPag(alpha, penaltyDiscount, data);

            DataSet marginalData = data.copy();

            List<Node> variables = marginalData.getVariables();
            Collections.shuffle(variables);

            for (int m = 0; m < numVarsToMarginalize; m++) {
                marginalData.removeColumn(marginalData.getColumn(variables.get(m)));
            }

            Graph margPag = getPag(alpha, penaltyDiscount, marginalData);

            int ancAnc = 0;
            int ancNanc = 0;
            int nancAnc = 0;
            int nancNanc = 0;
            int ambAnc = 0;
            int ambNanc = 0;

            int totalAncMarg = 0;
            int totalNancMarg = 0;

            for (Node n1 : marginalData.getVariables()) {
                for (Node n2 : marginalData.getVariables()) {
                    if (n1 == n2) continue;

                    if (ancestral(n1, n2, margPag)) {
                        if (ancestral(n1, n2, pag)) {
                            ancAnc++;
                        } else if (nonAncestral(n1, n2, pag)) {
                            nancAnc++;
                        } else {
                            ambAnc++;
                        }

                        totalAncMarg++;
                    } else if (nonAncestral(n1, n2, margPag)) {
                        if (ancestral(n1, n2, pag)) {
                            ancNanc++;
                        } else if (nonAncestral(n1, n2, pag)) {
                            nancNanc++;
                        } else {
                            ambNanc++;
                        }

                        totalNancMarg++;
                    }
                }
            }

//            {
//                TextTable table = new TextTable(5, 3);
//                table.setToken(0, 1, "Ancestral");
//                table.setToken(0, 2, "Nonancestral");
//                table.setToken(1, 0, "Ancestral");
//                table.setToken(2, 0, "Nonancestral");
//                table.setToken(3, 0, "Ambiguous");
//                table.setToken(4, 0, "Total");
//
//                table.setToken(1, 1, ancAnc + "");
//                table.setToken(2, 1, nancAnc + "");
//                table.setToken(3, 1, ambAnc + "");
//                table.setToken(1, 2, ancNanc + "");
//                table.setToken(2, 2, nancNanc + "");
//                table.setToken(3, 2, ambNanc + "");
//                table.setToken(4, 1, totalAncMarg + "");
//                table.setToken(4, 2, totalNancMarg + "");
//
//                System.out.println(table);
//            }

            {
                TextTable table = new TextTable(5, 3);
                table.setToken(0, 1, "Ancestral");
                table.setToken(0, 2, "Nonancestral");
                table.setToken(1, 0, "Ancestral");
                table.setToken(2, 0, "Nonancestral");
                table.setToken(3, 0, "Ambiguous");
                table.setToken(4, 0, "Total");

                NumberFormat nf = new DecimalFormat("0.00");

                table.setToken(1, 1, nf.format(ancAnc / (double) totalAncMarg) + "");
                table.setToken(2, 1, nf.format(nancAnc / (double) totalAncMarg) + "");
                table.setToken(3, 1, nf.format(ambAnc / (double) totalAncMarg) + "");
                table.setToken(1, 2, nf.format(ancNanc / (double) totalNancMarg) + "");
                table.setToken(2, 2, nf.format(nancNanc / (double) totalNancMarg) + "");
                table.setToken(3, 2, nf.format(ambNanc / (double) totalNancMarg) + "");
                table.setToken(4, 1, totalAncMarg + "");
                table.setToken(4, 2, totalNancMarg + "");

                System.out.println(table);
            }
        }
    }

    private boolean ancestral(Node n, Node q, Graph pag) {
        if (n == q) return false;

        if (pag.isAncestorOf(n, q)) {
            return true;
        } else {
            List<Node> adj = uncoveredPotentiallyDirectedPathStarts(n, q, pag, new LinkedList<Node>());

            if (adj.size() >= 2) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
                int[] choice;
                boolean found = false;

                while ((choice = gen.next()) != null) {
                    List<Node> c = GraphUtils.asList(choice, adj);
                    Node n1 = c.get(0);
                    Node n2 = c.get(1);

                    if (!pag.isAdjacentTo(n1, n2)) {
                        if (pag.isDefNoncollider(n1, n, n2)) {
                            found = true;
                        }
                    }
                }

                if (found) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean nonAncestral(Node n, Node q, Graph pag) {
        if (n == q) return false;

        if (ancestral(n, q, pag)) {
            return false;
        }

        if (pag.isAdjacentTo(n, q)) {
            if (pag.getEdge(n, q).pointsTowards(n)) {
                return true;
            }
        }

        if (uncoveredPotentiallyDirectedPathStarts(n, q, pag, new LinkedList<Node>()).isEmpty()) {
            return true;
        }

        return false;
    }

    private Graph getPag(double alpha, double penaltyDiscount, DataSet data) {
        IndTestFisherZ test = new IndTestFisherZ(data, alpha);

        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(data));
        score.setPenaltyDiscount(penaltyDiscount);

//        GraphSearch search = new Fci(test);
//        GraphSearch search = new GFci(score);
        GraphSearch search = new Pc(test);

        return search.search();
    }

    /**
     * Returns the adjacents n of x such that x*-*n... starts a potentially directed path.
     */
    private List<Node> uncoveredPotentiallyDirectedPathStarts(Node x, Node y, Graph g, LinkedList<Node> path) {
        List<Node> pathThrough = new ArrayList<>();

        if (x == y) return path;
        if (path.contains(x)) return path;
        path.add(x);

        for (Node n : g.getAdjacentNodes(x)) {
            Edge e = g.getEdge(x, n);

            if (e.getProximalEndpoint(x) == Endpoint.ARROW) continue;

            if (!uncoveredPotentiallyDirectedPathStarts(n, y, g, path).isEmpty()) {
                pathThrough.add(n);
            }
        }

        path.remove(x);
        return pathThrough;
    }

}





