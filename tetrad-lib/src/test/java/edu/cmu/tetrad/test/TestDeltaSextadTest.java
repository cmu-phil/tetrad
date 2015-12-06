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

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestDeltaSextadTest extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDeltaSextadTest(String name) {
        super(name);
    }

//    Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    public void testBollenExample1() {
        SemIm sem = getSem1();
        DataSet data = sem.simulateData(3000, false);

        List<Node> variables = data.getVariables();

        Node m1 = variables.get(0);
        Node m2 = variables.get(1);
        Node m3 = variables.get(2);
        Node m4 = variables.get(3);
        Node m5 = variables.get(4);
        Node m6 = variables.get(5);

        Sextad t1 = new Sextad(m1, m2, m3, m4, m5, m6);
        Sextad t2 = new Sextad(m1, m2, m4, m3, m5, m6);
        Sextad t3 = new Sextad(m1, m2, m5, m3, m4, m6);
        Sextad t4 = new Sextad(m1, m2, m6, m3, m4, m5);
        Sextad t5 = new Sextad(m1, m3, m4, m2, m5, m6);
        Sextad t6 = new Sextad(m1, m3, m5, m2, m4, m6);
        Sextad t7 = new Sextad(m1, m3, m6, m2, m4, m5);
        Sextad t8 = new Sextad(m1, m4, m5, m2, m3, m6);
        Sextad t9 = new Sextad(m1, m4, m6, m2, m3, m5);
        Sextad t10 = new Sextad(m1, m5, m6, m2, m3, m4);

        List<Sextad> sextads = new ArrayList<Sextad>();

        sextads.add(t1);
        sextads.add(t2);
        sextads.add(t3);
        sextads.add(t4);
        sextads.add(t5);
        sextads.add(t6);
        sextads.add(t7);
        sextads.add(t8);
        sextads.add(t9);
        sextads.add(t10);


        IDeltaSextadTest test = new DeltaSextadTest(data);

        int numSextads = 8;
        double alpha = 0.001;

        ChoiceGenerator gen = new ChoiceGenerator(sextads.size(), numSextads);
        int choice[];

        while ((choice = gen.next()) != null) {
            Sextad[] _sextads = new Sextad[numSextads];

            for (int i = 0; i < numSextads; i++) {
                _sextads[i] = sextads.get(choice[i]);
            }

            double p = test.getPValue(_sextads);

//            if (p > alpha) {
//                for (int i = 0; i < numSextads; i++) {
//                    System.out.print((choice[i] + 1 + " "));
//                }
//                System.out.println(" " + p);
//            }
        }

    }

    public void testBollenExampleb() {
        SemIm sem = getSem2();
        DataSet data = null; // = sem.simulateData(3000, false);

        try {
            String name = "src/test/resources/dataLG.txt";

//            PrintWriter out = new PrintWriter(new File(dir, name));
//            DataWriter.writeRectangularData(data, out, '\t');

            DataReader reader = new DataReader();
            data = reader.parseTabular(new File(name));
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<Node> variables = data.getVariables();

        Node m1 = variables.get(0);
        Node m2 = variables.get(1);
        Node m3 = variables.get(2);
        Node m4 = variables.get(3);
        Node m5 = variables.get(4);
        Node m6 = variables.get(5);

        Sextad t1 = new Sextad(m1, m2, m3, m4, m5, m6);
        Sextad t2 = new Sextad(m1, m2, m4, m3, m5, m6);
        Sextad t3 = new Sextad(m1, m2, m5, m3, m4, m6);
        Sextad t4 = new Sextad(m1, m2, m6, m3, m4, m5);
        Sextad t5 = new Sextad(m1, m3, m4, m2, m5, m6);
        Sextad t6 = new Sextad(m1, m3, m5, m2, m4, m6);
        Sextad t7 = new Sextad(m1, m3, m6, m2, m4, m5);
        Sextad t8 = new Sextad(m1, m4, m5, m2, m3, m6);
        Sextad t9 = new Sextad(m1, m4, m6, m2, m3, m5);
        Sextad t10 = new Sextad(m1, m5, m6, m2, m3, m4);

//        List<Sextad> sextads = new ArrayList<Sextad>();
//
//        sextads.add(t1a);
//        sextads.add(t2);
//        sextads.add(t3);
//        sextads.add(t4);
//        sextads.add(t5);
//        sextads.add(t6);
//        sextads.add(t7);
//        sextads.add(t8);
//        sextads.add(t9);
//        sextads.add(t10);
//
        IDeltaSextadTest test = new DeltaSextadTest(data);
//
//        int numSextads = 10;
//
//        Sextad[] _sextads = new Sextad[numSextads];
//
//        for (int i = 0; i < numSextads; i++) {
//            _sextads[i] = sextads.get(i);
//        }

//        Sextad[] _sextads = {t2, t5, t10, t3, t6};
        Sextad[] _sextads = {t1, t2, t3, t4, t5, t6, t7, t8, t9, t10};
//        Sextad[] _sextads = {t10};

        double p = test.getPValue(_sextads);

//        System.out.println(" " + p);
    }

    public void test2() {

        int c = 2;
        int m = 2;
        int p = 6;

        Graph g = new EdgeListGraph();
        List<List<Node>> varClusters = new ArrayList<List<Node>>();
        List<List<Node>> latents = new ArrayList<List<Node>>();

        List<Node> vars = new ArrayList<Node>();

        for (int y = 0; y < c; y++) {
            varClusters.add(new ArrayList<Node>());
            latents.add(new ArrayList<Node>());
        }

        int e = 0;

        for (int y = 0; y < c; y++) {
            for (int i = 0; i < p; i++) {
                GraphNode n = new GraphNode("V" + ++e);
                vars.add(n);
                varClusters.get(y).add(n);
                g.addNode(n);
            }
        }

        List<Node> l = new ArrayList<Node>();

        int f = 0;

        for (int y = 0; y < c; y++) {
            for (int j = 0; j < m; j++) {
                Node _l = new GraphNode("L" + ++f);
                _l.setNodeType(NodeType.LATENT);
                l.add(_l);
                latents.get(y).add(_l);
                g.addNode(_l);
            }
        }

        for (int y = 0; y < c; y++) {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < p; j++) {
                    g.addDirectedEdge(latents.get(y).get(i), varClusters.get(y).get(j));
                }
            }
        }

        for (int y = 1; y < c; y++) {
            for (int j = 0; j < m; j++) {
                g.addDirectedEdge(latents.get(y - 1).get(j), latents.get(y).get(j));
            }
        }

//        System.out.println(g);

        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
//        RandomUtil.getInstance().setSeed(4737572747L);
        DataSet data = im.simulateData(1000, false);

        List<Integer> indices = new ArrayList<Integer>();
        indices.add(0);
        indices.add(1);
        indices.add(2);
        indices.add(4);
        indices.add(5);
        indices.add(7);

        Collections.shuffle(indices);

        Node x1 = data.getVariable(indices.get(0));
        Node x2 = data.getVariable(indices.get(1));
        Node x3 = data.getVariable(indices.get(2));
        Node x4 = data.getVariable(indices.get(3));
        Node x5 = data.getVariable(indices.get(4));
        Node x6 = data.getVariable(indices.get(5));

        IDeltaSextadTest test = new DeltaSextadTest(data);

        // Should be invariant to changes or order of the first three or of the last three variables.
        double a = test.getPValue(new Sextad(x1, x2, x3, x4, x5, x6));
        double b = test.getPValue(new Sextad(x2, x3, x1, x5, x4, x6));

//        System.out.println(a);
//        System.out.println(b);

        assertEquals(a, b, 1e-7);

        FindTwoFactorClusters ftfc = new FindTwoFactorClusters(data, TestType.TETRAD_DELTA, 0.01);
        Graph graph = ftfc.search();
//        System.out.println(graph);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDeltaSextadTest.class);
    }

    private SemIm getSem1() {
        Graph graph = new EdgeListGraph();

        Node l1 = new GraphNode("l1");
        Node l2 = new GraphNode("l2");

        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);

        List<Node> measures = new ArrayList<Node>();
        int numMeasures = 8;

        for (int i = 0; i < numMeasures; i++) {
            measures.add(new GraphNode("X" + (i + 1)));
        }

        graph.addNode(l1);
        graph.addNode(l2);

        for (int i = 0; i < numMeasures; i++) {
            graph.addNode(measures.get(i));
            graph.addDirectedEdge(l1, measures.get(i));
            graph.addDirectedEdge(l2, measures.get(i));
        }

        SemPm pm = new SemPm(graph);

        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.3, 0.8);

        SemIm im = new SemIm(pm, params);
        return im;
    }

    private SemIm getSem2() {
        Graph graph = new EdgeListGraph();

        Node l1 = new GraphNode("l1");
//        Node l2 = new GraphNode("l2");

        l1.setNodeType(NodeType.LATENT);
//        l2.setNodeType(NodeType.LATENT);

        List<Node> measures = new ArrayList<Node>();
        int numMeasures = 6;

        for (int i = 0; i < numMeasures; i++) {
            measures.add(new GraphNode("X" + (i + 1)));
        }

        graph.addNode(l1);
//        graph.addNode(l2);

        for (int i = 0; i < numMeasures; i++) {
            graph.addNode(measures.get(i));
            graph.addDirectedEdge(l1, measures.get(i));
//            graph.addDirectedEdge(l2, measures.get(i));
        }

        SemPm pm = new SemPm(graph);

        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.3, 0.8);

        return new SemIm(pm, params);
    }
}



