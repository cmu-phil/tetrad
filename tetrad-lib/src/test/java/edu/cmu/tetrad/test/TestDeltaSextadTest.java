///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.DeltaSextadTest;
import edu.cmu.tetrad.search.utils.Sextad;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the BooleanFunction class.
 *
 * @author josephramsey
 */
public class TestDeltaSextadTest {


//    Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    @Test
    public void testBollenExample1() {
        SemIm sem = getSem1();
        DataSet data = sem.simulateData(3000, false);

        List<Node> variables = data.getVariables();


        final int m1 = 0;
        final int m2 = 1;
        final int m3 = 2;
        final int m4 = 3;
        final int m5 = 4;
        final int m6 = 5;

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

        List<Sextad> sextads = new ArrayList<>();

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

        final int numSextads = 3;
        final double alpha = 0.001;

        ChoiceGenerator gen = new ChoiceGenerator(sextads.size(), numSextads);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Sextad[] _sextads = new Sextad[numSextads];

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
            final String name = "src/test/resources/dataLG.txt";
            data = SimpleDataLoader.loadContinuousData(new File(name), "//", '\"',
                    "*", true, Delimiter.TAB, false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        final int m1 = 0;
        final int m2 = 1;
        final int m3 = 2;
        final int m4 = 3;
        final int m5 = 4;
        final int m6 = 5;

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

        DeltaSextadTest test = new DeltaSextadTest(data);

        Sextad[] _sextads = {t2, t5, t10, t3, t6};
        double p = test.getPValue(_sextads);
        assertEquals(0.21, p, 0.01);

        _sextads = new Sextad[]{t10};
        p = test.getPValue(_sextads);
        assertEquals(0.30, p, 0.01);

        // This should throw an exception but doesn't.
//        MySextad[] _sextads = {t1, t2, t3, t4, t5, t6, t7, t8, t9, t10};
    }

    @Test
    public void test2() {

        final int c = 2;
        final int m = 2;
        final int p = 6;

        Graph g = new EdgeListGraph();
        List<List<Node>> varClusters = new ArrayList<>();
        List<List<Node>> latents = new ArrayList<>();

        List<Node> vars = new ArrayList<>();

        for (int y = 0; y < c; y++) {
            varClusters.add(new ArrayList<>());
            latents.add(new ArrayList<>());
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

        List<Node> l = new ArrayList<>();

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

        List<Integer> indices = new ArrayList<>();
        indices.add(0);
        indices.add(1);
        indices.add(2);
        indices.add(4);
        indices.add(5);
        indices.add(7);

        RandomUtil.shuffle(indices);

        int x1 = indices.get(0);
        int x2 = indices.get(1);
        int x3 = indices.get(2);
        int x4 = indices.get(3);
        int x5 = indices.get(4);
        int x6 = indices.get(5);

        DeltaSextadTest test = new DeltaSextadTest(data);

        // Should be invariant to changes or order of the first three or of the last three variables.
        double a = test.getPValue(new Sextad(x1, x2, x3, x4, x5, x6));
        double b = test.getPValue(new Sextad(x2, x3, x1, x5, x4, x6));

        assertEquals(a, b, 1e-7);
    }

    private SemIm getSem1() {
        Graph graph = new EdgeListGraph();

        Node l1 = new GraphNode("l1");
        Node l2 = new GraphNode("l2");

        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);

        List<Node> measures = new ArrayList<>();
        final int numMeasures = 8;

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

        Parameters params = new Parameters();
//        params.setCoefRange(0.3, 0.8);

        return new SemIm(pm, params);
    }
}




