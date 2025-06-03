/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.DagToPag;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.graph.GraphTransforms.dagToPag;
import static org.junit.Assert.*;


/**
 * @author josephramsey
 */
public class TestFci {

    private static final Logger log = LoggerFactory.getLogger(TestFci.class);

    private static void runLvSearch(String outputGraph, IGraphSearch fci, Graph graph) {
        // Run search
        Graph resultGraph = null;
        try {
            resultGraph = fci.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Graph pag = GraphUtils.convert(outputGraph);
        resultGraph = GraphUtils.replaceNodes(resultGraph, pag.getNodes());

        System.out.println(resultGraph.paths().isLegalPag() ? "Legal PAG" : "Illegal PAG");
        System.out.println(unshieldedCollidersIdenticalPagMag(resultGraph)
                ? "Unshielded colliders the same " : "Unshielded colliders different.");

        assertEquals(pag, resultGraph);
//        System.out.println("DAG to PAG: " + dagToPag(graph));
//        assertEquals(pag, dagToPag(graph));
    }

    private static boolean isUnshieldedCollider(Node x, Node y, Node z, Graph g) {
        if (!(g.isAdjacentTo(x, y) && g.isAdjacentTo(y, z) && !g.isAdjacentTo(x, z))) {
            return false;
        }

        return g.getEndpoint(x, y) == Endpoint.ARROW && g.getEndpoint(z, y) == Endpoint.ARROW;
    }

    private static boolean isLegalMag(Graph graph) {
        List<Node> selection = graph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        GraphSearchUtils.LegalMagRet legalMag = GraphSearchUtils.isLegalMag(graph, new HashSet<>(selection));

        if (!legalMag.isLegalMag()) {
            System.out.println("Not legal mag, reason = " + legalMag.getReason());
        }

        return legalMag.isLegalMag();
    }

    private static boolean isLegalPag(Graph graph) {
        List<Node> selection = graph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        GraphSearchUtils.LegalPagRet legalMag = GraphSearchUtils.isLegalPag(graph, new HashSet<>(selection));

        if (!legalMag.isLegalPag()) {
            System.out.println("Not legal pag, reason = " + legalMag.getReason());
        }


        return legalMag.isLegalPag();
    }

    public static boolean unshieldedCollidersIdenticalPagMag(Graph estPag3) {
        return getUnshieldedColliders(estPag3).equals(getUnshieldedColliders(GraphTransforms.zhangMagFromPag(estPag3)));
    }

    public static Set<Triple> getUnshieldedColliders(Graph graph) {
        Set<Triple> unshieldedTriples = new HashSet<>();

        for (Node b : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (!graph.isAdjacentTo(x, y) && graph.isDefCollider(x, b, y)) {
                        unshieldedTriples.add(new Triple(x, b, y));
                    }
                }
            }
        }

        return unshieldedTriples;
    }

    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1o-oX2,X1o-oX3,X2-->X4,X3-->X4", new Knowledge()); // With Zhang's R9.
    }

    /**
     * Tests a specific search. (See code for details.)
     */
    @Test
    public void testSearch2() {
        checkSearch("Z1-->X,Z2-->X,X-->Y", "Z1o->X,Z2o->X,X-->Y", new Knowledge());
    }

    /**
     * Basic discriminating path checker.
     */
    @Test
    public void testSearch3() {
        checkSearch("X-->W,V-->W,V-->Y,W-->Y", "Xo->W,Vo->W,V-->Y,W-->Y", new Knowledge());
    }

    /**
     * Basic discriminating path checker.
     */
    @Test
    public void testSearch4() {
        checkSearch("Latent(G),Latent(R),H-->F,F<--G,G-->A,A<--R,R-->C,B-->C,B-->D,C-->D,F-->D,A-->D",
                "A<->C,A-->D,Bo->C,Bo->D,Co->D,F<->A,F-->D,Ho->F", new Knowledge());
    }

    /**
     * Basic discriminating path checker with an extra variable thrown in. (For some reason, FCI was screwing up on
     * this.)
     */
    @Test
    public void testSearch5() {
        checkSearch("A-->C,B-->C,B-->D,C-->D,E", "Ao->C,Bo->C,B-->D,C-->D,E", new Knowledge());
    }

    /**
     * FCI was breaking with 1 or 2 variables.
     */
    @Test
    public void testSearch6() {
        checkSearch("A-->B", "Ao-oB", new Knowledge());
    }

    /**
     * A specific graph.
     */
    @Test
    public void testSearch7() {
        // Graph Nodes:
        //D;H;L;M;I;S;P
        //
        //Graph Edges:
        //1. D <-> H
        //2. D --> L
        //3. D --> M
        //4. H --> M
        //5. I o-> S
        //6. L <-> H
        //7. L --> M
        //8. P o-> S
        //9. S --> D

        checkSearch("Latent(E),Latent(G),E-->D,E-->H,G-->H,G-->L,D-->L,D-->M," +
                    "H-->M,L-->M,S-->D,I-->S,P-->S",
                "D<->H,D-->L,D-->M,H-->M,Io->S,L<->H,Lo->M,Po->S,S-->D", new Knowledge());
    }

    /**
     * A specific graph. This is the graph on p. 5 of Danks, Learning Integrated Structure from Distributed Databases
     * with Overlapping Variables.
     */
    @Test
    public void testSearch8() {
        checkSearch("X-->Z,Y-->Z,Z-->B,B-->A,C-->A",
                "Xo->Z,Yo->Z,Z-->B,B-->A,Co->A", new Knowledge());
    }

    /**
     * A specific graph. This is the test case from p. 142-144 that tests the possible dsep step of FCI. This doesn't
     * work in the optimized FCI algorithm. It works in the updated version (FciSearch).  (ekorber)
     */
    @Test
    public void testSearch9() {
        checkSearch("Latent(T1),Latent(T2),T1-->A,T1-->B,B-->E,F-->B,C-->F,C-->H," +
                    "H-->D,D-->A,T2-->D,T2-->E",
                "A<->B,B-->E,Fo->B,Fo-oC,Co-oH,Ho->D,D<->E,D-->A", new Knowledge()); // Left out E<->A.
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch10() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "Ao->D,Ao-oB,Bo->D,Co->D,D-->E", new Knowledge());
    }

    // This fails for FCIT from Oracle understandably. (GSTs from oracle can't use knowledge.)
    // For FCI etc. can turn it on.
//    @Test
    public void testSearch11() {
        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o->X2,X3o->X2", new Knowledge());

        List<String> varNames = new ArrayList<>();
        varNames.add("X1");
        varNames.add("X2");
        varNames.add("X3");

        Knowledge knowledge = new Knowledge(varNames);
        knowledge.addToTier(1, "X1");
        knowledge.addToTier(1, "X2");
        knowledge.addToTier(2, "X3");

        checkSearch("Latent(L1),Latent(L2),L1-->X1,L1-->X2,L2-->X2,L2-->X3",
                "X1o->X2,X2<->X3", knowledge);
    }

    // This fails for FCIT from Oracle understandably. (GSTs from oracle can't use knowledge.)
    // For FCI etc. can turn it on.
//    @Test
    public void testSearch12() {
        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o->X2,X3o->X4,X2<->X4", new Knowledge());

        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("X2", "X4");

        assertTrue(knowledge.isRequired("X2", "X4"));

        checkSearch("Latent(L1),X1-->X2,X3-->X4,L1-->X2,L1-->X4",
                "X1o-oX2,X3o->X4,X2-->X4", knowledge);
    }

    @Test
    public void testSearch13() {
        final int numVars = 10;
        final int numEdges = 10;

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag trueGraph = new Dag(RandomGraph.randomGraph(nodes, 10, numEdges,
                7, 5, 5, false));

        IndependenceTest test = new MsepTest(trueGraph);

        Fci fci = new Fci(test);

        Graph graph = null;
        try {
            graph = fci.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        DagToPag dagToPag = new DagToPag(trueGraph);
//        Graph truePag = dagToPag.convert();

        Graph truePag = dagToPag(trueGraph);

        assertEquals(graph, truePag);
    }

    /**
     * This checks to see whether the R4 rule can correctly orient multiple discriminating paths from X to Y in various
     * configurations.
     */
    @Test
    public void testSearch14() {

        checkSearch("X-->W1,V1-->W1,V1-->Y,W1-->Y,X-->W2,V2-->W2,V2-->Y,W2-->Y",
                "Xo->W1,V1o->W1,V1-->Y,W1-->Y,Xo->W2,V2o->W2,V2-->Y,W2-->Y", new Knowledge());

        checkSearch("Latent(R),Latent(S),X-->W1,R-->W1,R-->V1,S-->V1,S-->Y,W1-->Y,X-->W2,V2-->W2,V2-->Y,W2-->Y",
                "Xo->W1,V1<->W1,V1<->Y,W1-->Y,Xo->W2,V2o->W2,V2-->Y,W2-->Y", new Knowledge());

        checkSearch("Latent(R),Latent(S),X-->W2,V2-->W2,V2-->Y,W2-->Y,X-->W1,R-->W1,R-->V1,S-->V1,S-->Y,W1-->Y",
                "Xo->W2,V2o->W2,V2-->Y,W2-->Y,Xo->W1,V1<->W1,V1<->Y,W1-->Y", new Knowledge());
    }

    /**
     * This checks to see whether the R4 rule can correctly orient multiple discriminating paths from X to Y in various
     * configurations.
     */
    @Test
    public void testSearch15() {
        checkSearch("Latent(L),X-->Y,W-->Z,L-->Y,L-->Z",
                "Xo->Y,Y<->Z,Wo->Z", new Knowledge());
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph, Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        boolean verbose = true;

        // Set up graph and node objects.
        Graph graph = GraphUtils.convert(inputGraph);

        System.out.println("Graph = " + graph);

        // Set up search.
        IndependenceTest independence = new MsepTest(graph);
        Score score = new GraphScore(graph);

        {
            Fci fci = new Fci(independence);
            fci.setKnowledge(knowledge);
            fci.setVerbose(verbose);

            runLvSearch(outputGraph, fci, graph);
        }

        {
            FgesFci fci = new FgesFci(independence, score);
            fci.setKnowledge(knowledge);
            fci.setVerbose(verbose);

            runLvSearch(outputGraph, fci, graph);
        }

        {
            GraspFci fci = new GraspFci(independence, score);
            fci.setKnowledge(knowledge);
            fci.setVerbose(verbose);

            runLvSearch(outputGraph, fci, graph);
        }

        {
            Fcit fci = new Fcit(independence, score);
            fci.setStartWith(Fcit.START_WITH.GRASP);
//            fci.setDepth(-1);
            fci.setKnowledge(knowledge);
//            fci.setPreserveMarkov(false);
            fci.setVerbose(verbose);

            runLvSearch(outputGraph, fci, graph);
        }
    }

    //    @Test
    public void testFciAnc() {
        final int numMeasures = 50;
        final double edgeFactor = 2.0;

        final int numRuns = 10;
        final double alpha = 0.01;
        final double penaltyDiscount = 4.0;
        final int numVarsToMarginalize = 5;
        final int numLatents = 10;

        System.out.println("num measures = " + numMeasures);
        System.out.println("edge factor = " + edgeFactor);
        System.out.println("alpha = " + alpha);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("num runs = " + numRuns);
        System.out.println("num vars to marginalize = " + numVarsToMarginalize);
        System.out.println("num latents = " + numLatents);

        System.out.println();

        for (int i = 0; i < numRuns; i++) {
            final int numEdges = (int) (edgeFactor * (numMeasures + numLatents));

            List<Node> nodes = new ArrayList<>();

            for (int r = 0; r < numMeasures + numLatents; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
            }

            Graph dag = RandomGraph.randomGraphRandomForwardEdges(nodes, numLatents, numEdges,
                    10, 10, 10, false, -1);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(1000, false);

            Graph pag = getPag(data);

            DataSet marginalData = data.copy();

            List<Node> variables = marginalData.getVariables();
            RandomUtil.shuffle(variables);

            for (int m = 0; m < numVarsToMarginalize; m++) {
                marginalData.removeColumn(marginalData.getColumnIndex(variables.get(m)));
            }

            Graph margPag = getPag(marginalData);

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

            {
                TextTable table = new TextTable(5, 3);
                table.setToken(0, 1, "Ancestral");
                table.setToken(0, 2, "Nonancestral");
                table.setToken(1, 0, "Ancestral");
                table.setToken(2, 0, "Nonancestral");
                table.setToken(3, 0, "Ambiguous");
                table.setToken(4, 0, "Total");

                NumberFormat nf = new DecimalFormat("0.00");

                table.setToken(1, 1, nf.format(ancAnc / (double) totalAncMarg));
                table.setToken(2, 1, nf.format(nancAnc / (double) totalAncMarg));
                table.setToken(3, 1, nf.format(ambAnc / (double) totalAncMarg));
                table.setToken(1, 2, nf.format(ancNanc / (double) totalNancMarg));
                table.setToken(2, 2, nf.format(nancNanc / (double) totalNancMarg));
                table.setToken(3, 2, nf.format(ambNanc / (double) totalNancMarg));
                table.setToken(4, 1, totalAncMarg + "");
                table.setToken(4, 2, totalNancMarg + "");

                System.out.println(table);
            }
        }
    }

    /**
     * This is a "problem MAG" that Peter recommended for testing, from Causation, Prediction, and Search. In order to
     * get it right, the correct conditioning sets need to be found for each of the two inducing paths.
     */
    @Test
    public void testSearch16() {
        boolean verbose = false;

        final String trueDag = "Graph Nodes:\n" +
                               "(ep);(g);cd;hd;lc;s;i;ps;mb\n" +
                               "\n" +
                               "Graph Edges:\n" +
                               "1. cd --> lc\n" +
                               "2. cd --> mb\n" +
                               "3. ep --> cd\n" +
                               "4. ep --> hd\n" +
                               "5. g --> hd\n" +
                               "6. g --> lc\n" +
                               "7. hd --> mb\n" +
                               "8. i --> s\n" +
                               "9. lc --> mb\n" +
                               "10. ps --> s\n" +
                               "11. s --> cd";

        final String correctPag = "Graph Nodes:\n" +
                                  "cd;hd;lc;s;i;ps;mb\n" +
                                  "\n" +
                                  "Graph Edges:\n" +
                                  "1. cd <-> hd\n" +
                                  "2. cd --> lc\n" +
                                  "3. cd --> mb\n" +
                                  "4. hd --> mb\n" +
                                  "5. i o-> s\n" +
                                  "6. lc <-> hd\n" +
                                  "7. lc o-> mb\n" +
                                  "8. ps o-> s\n" +
                                  "9. s --> cd";

        try {
            Graph trueMag_ = GraphSaveLoadUtils.readerToGraphTxt(trueDag);
            Graph truePag_ = GraphSaveLoadUtils.readerToGraphTxt(correctPag);

            System.out.println("True DAG");
            System.out.println(trueMag_);

            System.out.println("Correct PAG");
            System.out.println(truePag_);

            Fci fci = new Fci(new MsepTest(trueMag_));
            fci.setVerbose(verbose);
            Graph estPag1 = fci.search();
            assertEquals(truePag_, estPag1);

            GraspFci graspFci = new GraspFci(new MsepTest(trueMag_), new GraphScore(trueMag_));
            graspFci.setUseRaskuttiUhler(true);
            graspFci.setUseScore(false);
            graspFci.setVerbose(verbose);
            Graph estPag2 = graspFci.search();
            assertEquals(truePag_, estPag2);

            Fcit fcit = new Fcit(new MsepTest(trueMag_), new GraphScore(trueMag_));
            fcit.setStartWith(Fcit.START_WITH.GRASP);
//            fcit.setPreserveMarkov(false);
            Graph estPag3 = fcit.search();

            System.out.println(estPag3.paths().isLegalPag() ? "Legal PAG" : "Illegal PAG");
            System.out.println(unshieldedCollidersIdenticalPagMag(estPag3)
                    ? "Unshielded colliders the same " : "Unshielded colliders different.");

            assertEquals(truePag_, estPag3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test18() {

        // Possible d-sep check from Haoyue Dai.
        Graph graph = GraphUtils.convert("0o->2, 1o->2, 0o-o3, 2o-o3");
        Node v1 = graph.getNode("1");
        Node v3 = graph.getNode("3");
        assertTrue(graph.paths().possibleDsep(v3, -1).contains(v1));
        assertTrue(graph.paths().possibleDsep(v1, -1).contains(v3));
    }

    @Test
    public void test19() {
        Graph mag = GraphUtils.convert("x-->b,b-->z,z<->y,x<->a,a<->y,a-->b");

        Node x = mag.getNode("x");
        Node y = mag.getNode("y");

        Set<Node> B = null;
        try {
            B = RecursiveBlocking.blockPathsRecursively(mag, x, y, new HashSet<>(), new HashSet<>(), -1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        MsepTest msepTest = new MsepTest(mag);
        boolean indep = msepTest.checkIndependence(x, y, B).isIndependent();

        System.out.println("B = " + B + ", indep = " + indep);

        assertTrue(B.isEmpty());
        assertTrue(indep);
    }

    @Test
    public void test20() throws InterruptedException {
        Graph g = new EdgeListGraph();
        Node x = new GraphNode("x");
        Node y = new GraphNode("y");
        Node b1 = new GraphNode("b1");
        Node b2 = new GraphNode("b2");
        Node t = new GraphNode("t");

        g.addNode(x);
        g.addNode(y);
        g.addNode(b1);
        g.addNode(b2);
        g.addNode(t);
        g.addDirectedEdge(x, b1);
        g.addDirectedEdge(b1, y);
        g.addDirectedEdge(x, b2);
        g.addDirectedEdge(b2, t);
        g.addDirectedEdge(t, y);

        MsepTest msepTest = new MsepTest(g);

        Set<Node> Z = RecursiveBlocking.blockPathsRecursively(g, x, y, new HashSet<Node>(),
                new HashSet<Node>(), -1);
        assertTrue(msepTest.checkIndependence(x, y, Z).isIndependent());
    }

    @Test
    public void testSearch21() {
        boolean verbose = false;

        final String trueDag = "Graph Nodes:\n" +
                               "X1;X2;X4;X5;X6\n" +
                               "\n" +
                               "Graph Edges:\n" +
                               "1. X1 o-o X4\n" +
                               "2. X2 o-> X6\n" +
                               "3. X4 o-> X5\n" +
                               "4. X5 <-> X6";

        final String correctPag = "Graph Nodes:\n" +
                                  "X1;X2;X4;X5;X6\n" +
                                  "\n" +
                                  "Graph Edges:\n" +
                                  "1. X1 o-o X4\n" +
                                  "2. X2 o-> X6\n" +
                                  "3. X4 o-> X5\n" +
                                  "4. X5 <-> X6";

        try {
            Graph trueMag_ = GraphSaveLoadUtils.readerToGraphTxt(trueDag);
            Graph truePag_ = GraphSaveLoadUtils.readerToGraphTxt(correctPag);

            System.out.println("True DAG");
            System.out.println(trueMag_);

            System.out.println("Correct PAG");
            System.out.println(truePag_);

            Fci fci = new Fci(new MsepTest(trueMag_));
            fci.setVerbose(verbose);
            Graph estPag1 = fci.search();
            assertEquals(truePag_, estPag1);

            GraspFci graspFci = new GraspFci(new MsepTest(trueMag_), new GraphScore(trueMag_));
            graspFci.setUseRaskuttiUhler(true);
            graspFci.setUseScore(false);
            graspFci.setVerbose(verbose);
            Graph estPag2 = graspFci.search();
            assertEquals(truePag_, estPag2);

            Fcit fcit = new Fcit(new MsepTest(trueMag_), new GraphScore(trueMag_));
            fcit.setStartWith(Fcit.START_WITH.GRASP);
            fcit.setVerbose(true);
//            fcit.setPreserveMarkov(false);
            Graph estPag3 = fcit.search();

            System.out.println(estPag3.paths().isLegalPag() ? "Legal PAG" : "Illegal PAG");
            System.out.println(unshieldedCollidersIdenticalPagMag(estPag3)
                    ? "Unshielded colliders the same " : "Unshielded colliders different.");

            assertEquals(truePag_, estPag3);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean ancestral(Node n, Node q, Graph pag) {
        if (n == q) return false;

        if (pag.paths().isAncestorOf(n, q)) {
            return true;
        } else {
            List<Node> adj = uncoveredPotentiallyDirectedPathStarts(n, q, pag, new LinkedList<>());

            if (adj.size() >= 2) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
                int[] choice;
                boolean found = false;

                while ((choice = gen.next()) != null) {
                    List<Node> c = GraphUtils.asList(choice, adj);
                    Node n1 = c.getFirst();
                    Node n2 = c.get(1);

                    if (!pag.isAdjacentTo(n1, n2)) {
                        if (pag.isDefNoncollider(n1, n, n2)) {
                            found = true;
                        }
                    }
                }

                return found;
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

        return uncoveredPotentiallyDirectedPathStarts(n, q, pag, new LinkedList<>()).isEmpty();
    }

    private Graph getPag(DataSet data) {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);

        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(4.0);

        IGraphSearch search = new Pc(test);

        try {
            return search.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

    @Test
    public void testChokePoint() {
        Graph graph = GraphUtils.convert("x-->r,x-->s,r-->c,s-->c,c-->w,c-->v,w-->y,v-->y");

        try {
            Set<Node> B = RecursiveBlockingChokePointB.blockPathsRecursively(graph, graph.getNode("x"), graph.getNode("y"),
                    Set.of(), -1);

            System.out.println("B = " + B);

            MsepTest msepTest = new MsepTest(graph);

            System.out.println("independent = " + msepTest.checkIndependence(graph.getNode("x"), graph.getNode("y"), B).isIndependent());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

//    @Test
    public void testFcitFromOracle() {
        for (int i = 0; i < 100; i++) {
            System.out.println("==================== RUN " + (i + 1) + " TEST ====================");

            long seed = System.nanoTime();
//            long seed = 24723257885916L;

            RandomUtil.getInstance().setSeed(seed);

            Graph dag = RandomGraph.randomGraph(15, 3, 15, 100,
                    100, 100, false);
            MsepTest independence = new MsepTest(dag);
            dag = GraphUtils.replaceNodes(dag, independence.getVariables());
            GraphScore score = new GraphScore(dag);

            Graph _pag = new DagToPag(dag).convert();
            if (!_pag.paths().isLegalPag()) {
                throw new IllegalArgumentException("_Pag not a legal PAG.");
            }

            try {
//                Fci fci = new Fci(independence);
//                Gfci fci = new Gfci(independence, score);
//                GraspFci fci = new GraspFci(independence, score);

                Fcit fci = new Fcit(independence, score);
                fci.setStartWith(Fcit.START_WITH.GRASP);

                fci.setCompleteRuleSetUsed(true);
                fci.setVerbose(false);

                Graph pag = fci.search();

                boolean illegal = !isLegalPag(pag);

                if (illegal) {
                    System.out.println("Not a legal PAG");

                    Graph mag = GraphTransforms.zhangMagFromPag(pag);

                    if (getUnshieldedColliders(pag).equals(getUnshieldedColliders(mag))) {
                        System.out.println("Unshielded colliders match between mag and pag.");
                    }

                    DagToPag dagToPag = new DagToPag(mag);
                    Graph reconstitutedPag = dagToPag.convert();

                    for (Edge pagEdge : pag.getEdges()) {
                        Edge reconstitutedPagEdge = reconstitutedPag.getEdge(pagEdge.getNode1(), pagEdge.getNode2());

                        if (!pagEdge.equals(reconstitutedPagEdge)) {
                            System.out.println("Edge discrepancy: pagEdge = " + pagEdge + " reconstituted PAG edge = " + reconstitutedPagEdge);
                        }
                    }

                    System.out.println("pag is not legal pag seed = " + seed);
                }

                assertFalse(illegal);

//                if (!pag.equals(_pag)) {
//                    fci.setVerbose(true);
//                    fci.search();
//                }

                assertEquals(_pag, pag);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


//    @Test
    public void testFcitFromData() {
        for (int i = 0; i < 100; i++) {
            System.out.println("==================== RUN " + (i + 1) + " TEST ====================");

            Graph graph = RandomGraph.randomGraph(50, 6, 100, 100, 100, 100, false);
            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm);
            DataSet dataSet = im.simulateData(1000, false);

            IndependenceTest test = new IndTestFisherZ(dataSet, 0.00001);
            SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            score.setPenaltyDiscount(2.0);

            try {
                Fcit fcit = new Fcit(test, score);
                fcit.setPrintRestored(true);
//                fcit.setVerbose(true);
                fcit.setDepth(7);
                fcit.setCompleteRuleSetUsed(true);
                Graph pag = fcit.search();

                if (!pag.paths().isMaximal()) {
                    System.out.println("************ pag is not maximal **************");
                }

                Graph mag = GraphTransforms.zhangMagFromPag(pag);

                if (!mag.paths().isLegalMag()) {
                    System.out.println("************ mag in pag is not legal *********");
                }

                List<Node> selection = graph.getNodes().stream()
                        .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

                GraphSearchUtils.LegalPagRet ret = GraphSearchUtils.isLegalPag(pag, new HashSet<>(selection));

                if (!ret.isLegalPag()) {
                    System.out.println("************ pag is not legal ****************");
                    System.out.println("**** Reason = " + ret.getReason());

                    if (getUnshieldedColliders(pag).equals(getUnshieldedColliders(mag))) {
                        System.out.println("Unshielded colliders match between mag and pag.");
                    }
                }

                if (mag.paths().isLegalMag() && !pag.paths().isLegalPag()) {
                    List<Node> nodes = pag.getNodes();

                    for (Node y : nodes) {
                        List<Node> adjacentNodes = mag.getAdjacentNodes(y);

                        for (Node x : adjacentNodes) {
                            for (Node z : adjacentNodes) {
                                if (x == z) continue;

                                if (isUnshieldedCollider(x, y, z, mag)) {
                                    if (!isUnshieldedCollider(x, y, z, pag)) {
                                        System.out.println("a Zhang mag = " + GraphUtils.pathString(mag, x, y, z) + " adj(" + x + ", " + z + ") = " + mag.isAdjacentTo(x, z));
                                        System.out.println("a pag = " + GraphUtils.pathString(pag, x, y, z) + " adj(" + x + ", " + z + ") = " + mag.isAdjacentTo(x, z));
                                    }
                                }
                            }
                        }
                    }

                    for (Node y : nodes) {
                        List<Node> adjacentNodes = pag.getAdjacentNodes(y);

                        for (Node x : adjacentNodes) {
                            for (Node z : adjacentNodes) {
                                if (x == z) continue;

                                if (isUnshieldedCollider(x, y, z, pag)) {
                                    if (!isUnshieldedCollider(x, y, z, mag)) {
                                        System.out.println("b Zhang mag = " + GraphUtils.pathString(mag, x, y, z) + " adj(" + x + ", " + z + ") = " + mag.isAdjacentTo(x, z));
                                        System.out.println("b pag = " + GraphUtils.pathString(pag, x, y, z) + " adj(" + x + ", " + z + ") = " + mag.isAdjacentTo(x, z));
                                    }
                                }
                            }
                        }
                    }

                    DagToPag dagToPag = new DagToPag(mag);
                    Graph reconstitutedPag = dagToPag.convert();

                    for (Edge pagEdge : pag.getEdges()) {
                        Edge reconstitutedPagEdge = reconstitutedPag.getEdge(pagEdge.getNode1(), pagEdge.getNode2());

                        if (!pagEdge.equals(reconstitutedPagEdge)) {
                            System.out.println("Edge discrepancy: pagEdge = " + pagEdge + " reconstituted PAG edge = " + reconstitutedPagEdge);
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testZhangPagToMag() {

        // Make a random DAG and then try DAG to PAG and then PAG to MAG and see if the MAG is cyclic.

        int index = 0;

        for (int i = 0; i < 1000; i++) {
//            System.out.println("================= RUN " + (i + 1) + " TEST ====================");

            long seed = RandomUtil.getInstance().nextLong();
//            long seed = -6064115539269406491L;
            RandomUtil.getInstance().setSeed(seed);

            Graph dag = RandomGraph.randomGraph(20, 6, 20, 100, 100, 100, false);
            DagToPag dagToPag = new DagToPag(dag);
            dagToPag.setVerbose(false);
            Graph pag = dagToPag.convert();

//            if (!isLegalPag(pag)) {
//                System.out.println("Not a legal pag seed = " + seed);
//            }

            Graph mag = GraphTransforms.zhangMagFromPag(pag);
//            mag = GraphTransforms.dagToMag(dag);

            Set<Triple> magUnshieldedTriples = getUnshieldedColliders(mag);
            Set<Triple> pagUnshieldedTriples = getUnshieldedColliders(pag);

            if (!magUnshieldedTriples.equals(pagUnshieldedTriples)) {
                System.out.println("MAG and PAG unshielded triples not the same.");
            }

            boolean legalMag = isLegalMag(mag);

            if (!legalMag) {
                if (mag.paths().existsDirectedCycle()) {

                    for (Node b : mag.getNodes()) {
                        // List the cycles.
                        List<List<Node>> cyclicPaths = mag.paths().directedPaths(b, b, -1);

                        for (List<Node> cyclicPath : cyclicPaths) {
                            System.out.println(GraphUtils.pathString(mag, cyclicPath, false));
                            System.out.println("\t" + GraphUtils.pathString(pag, cyclicPath, false));
                        }
                    }
                }

                System.out.println("mag is not legal mag seed = " + seed);

//                DagToPag dagToPag2 = new DagToPag(dag);
//                dagToPag2.setVerbose(true);
//                Graph pag2 = dagToPag2.convert();
//
//                System.out.println("pag2 legal = " + pag2.paths().isLegalPag());
//
//                index++;
//
//                System.out.println("index = " + index);
//                GraphSaveLoadUtils.saveGraph(dag, new File("/Users/josephramsey/Downloads/check_graphs/dag." + index + ".txt"), false);
//                GraphSaveLoadUtils.saveGraph(pag, new File("/Users/josephramsey/Downloads/check_graphs/pag." + index + ".txt"), false);
//                GraphSaveLoadUtils.saveGraph(mag, new File("/Users/josephramsey/Downloads/check_graphs/mag." + index + ".txt"), false);
            }

            // If this fails, it could be because someone was mucking with the inducing path method. Try reverting that
            // to what it was.
            assertTrue(legalMag);
        }
    }

    @Test
    public void testFcitSimpleR4() {
        Graph graph = GraphUtils.convert("X-->W,V-->W,V-->Y,W-->Y");
        Graph pag = GraphUtils.convert("Xo->W,Vo->W,V-->Y,W-->Y");

        IndependenceTest independence = new MsepTest(graph);
        Score score = new GraphScore(graph);

        Fcit fci = new Fcit(independence, score);
        fci.setStartWith(Fcit.START_WITH.GRASP);
//        fci.setDepth(-1);
//        fci.setPreserveMarkov(false);
        fci.setVerbose(true);

        try {
            Graph resultGraph = fci.search();

            resultGraph = GraphUtils.replaceNodes(resultGraph, pag.getNodes());

            System.out.println(resultGraph.paths().isLegalPag() ? "Legal PAG" : "Illegal PAG");
            System.out.println(unshieldedCollidersIdenticalPagMag(resultGraph)
                    ? "Unshielded colliders the same " : "Unshielded colliders different.");

            assertEquals(pag, resultGraph);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}





