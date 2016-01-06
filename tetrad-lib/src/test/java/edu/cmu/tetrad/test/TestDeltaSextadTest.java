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
import edu.cmu.tetrad.search.DeltaSextadTest;
import edu.cmu.tetrad.search.IntSextad;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestDeltaSextadTest {


//    Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    @Test
    public void testBollenExample1() {
        SemIm sem = getSem1();
        DataSet data = sem.simulateData(3000, false);

        List<Node> variables = data.getVariables();


        int m1 = 0;
        int m2 = 1;
        int m3 = 2;
        int m4 = 3;
        int m5 = 4;
        int m6 = 5;

        IntSextad t1 = new IntSextad(m1, m2, m3, m4, m5, m6);
        IntSextad t2 = new IntSextad(m1, m2, m4, m3, m5, m6);
        IntSextad t3 = new IntSextad(m1, m2, m5, m3, m4, m6);
        IntSextad t4 = new IntSextad(m1, m2, m6, m3, m4, m5);
        IntSextad t5 = new IntSextad(m1, m3, m4, m2, m5, m6);
        IntSextad t6 = new IntSextad(m1, m3, m5, m2, m4, m6);
        IntSextad t7 = new IntSextad(m1, m3, m6, m2, m4, m5);
        IntSextad t8 = new IntSextad(m1, m4, m5, m2, m3, m6);
        IntSextad t9 = new IntSextad(m1, m4, m6, m2, m3, m5);
        IntSextad t10 = new IntSextad(m1, m5, m6, m2, m3, m4);

        List<IntSextad> sextads = new ArrayList<IntSextad>();

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


        DeltaSextadTest test = new DeltaSextadTest(data);

        int numSextads = 3;
        double alpha = 0.001;

        ChoiceGenerator gen = new ChoiceGenerator(sextads.size(), numSextads);
        int choice[];

        while ((choice = gen.next()) != null) {
            IntSextad[] _sextads = new IntSextad[numSextads];

            for (int i = 0; i < numSextads; i++) {
                _sextads[i] = sextads.get(choice[i]);
            }

            double p = test.getPValue(_sextads);
        }

    }

    @Test
    public void testBollenExampleb() {
        DataSet data = null;

        try {
            String name = "src/test/resources/dataLG.txt";
            DataReader reader = new DataReader();
            data = reader.parseTabular(new File(name));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int m1 = 0;
        int m2 = 1;
        int m3 = 2;
        int m4 = 3;
        int m5 = 4;
        int m6 = 5;

        IntSextad t1 = new IntSextad(m1, m2, m3, m4, m5, m6);
        IntSextad t2 = new IntSextad(m1, m2, m4, m3, m5, m6);
        IntSextad t3 = new IntSextad(m1, m2, m5, m3, m4, m6);
        IntSextad t4 = new IntSextad(m1, m2, m6, m3, m4, m5);
        IntSextad t5 = new IntSextad(m1, m3, m4, m2, m5, m6);
        IntSextad t6 = new IntSextad(m1, m3, m5, m2, m4, m6);
        IntSextad t7 = new IntSextad(m1, m3, m6, m2, m4, m5);
        IntSextad t8 = new IntSextad(m1, m4, m5, m2, m3, m6);
        IntSextad t9 = new IntSextad(m1, m4, m6, m2, m3, m5);
        IntSextad t10 = new IntSextad(m1, m5, m6, m2, m3, m4);

        DeltaSextadTest test = new DeltaSextadTest(data);

        IntSextad[] _sextads = {t2, t5, t10, t3, t6};
        double p = test.getPValue(_sextads);
        assertEquals(0.21, p, 0.01);

        _sextads = new IntSextad[] {t10};
        p = test.getPValue(_sextads);
        assertEquals(0.30, p, 0.01);

        // This should throw an exception but doesn't.
//        MySextad[] _sextads = {t1, t2, t3, t4, t5, t6, t7, t8, t9, t10};
    }

    @Test
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

        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

        List<Integer> indices = new ArrayList<Integer>();
        indices.add(0);
        indices.add(1);
        indices.add(2);
        indices.add(4);
        indices.add(5);
        indices.add(7);

        Collections.shuffle(indices);

//        Node x1 = data.getVariable(indices.get(0));
//        Node x2 = data.getVariable(indices.get(1));
//        Node x3 = data.getVariable(indices.get(2));
//        Node x4 = data.getVariable(indices.get(3));
//        Node x5 = data.getVariable(indices.get(4));
//        Node x6 = data.getVariable(indices.get(5));

        int x1 = indices.get(0);
        int x2 = indices.get(1);
        int x3 = indices.get(2);
        int x4 = indices.get(3);
        int x5 = indices.get(4);
        int x6 = indices.get(5);

        DeltaSextadTest test = new DeltaSextadTest(data);

        // Should be invariant to changes or order of the first three or of the last three variables.
        double a = test.getPValue(new IntSextad(x1, x2, x3, x4, x5, x6));
        double b = test.getPValue(new IntSextad(x2, x3, x1, x5, x4, x6));

        assertEquals(a, b, 1e-7);
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

        return new SemIm(pm, params);
    }
}



