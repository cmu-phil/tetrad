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
import edu.cmu.tetrad.sem.Ricf;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradMatrix;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestRicf {

    /**
     * <pre>
     * > ## A covariance matrix
     *
     * > "S" <- structure(c(2.93, -1.7, 0.76, -0.06,
     * +                   -1.7, 1.64, -0.78, 0.1,
     * +                    0.76, -0.78, 1.66, -0.78,
     * +                   -0.06, 0.1, -0.78, 0.81), .Dim = c(4,4),
     * +                  .Dimnames = list(c("y", "x", "z", "u"), c("y", "x",
     * "z", "u")))
     *
     * > ## The following should give the same fit.
     *
     * > ## Fit an ancestral graph y -> x <-> z <- u
     *
     * > fitAncestralGraph(ag1 <- makeAG(dag=DAG(x~y,z~u), bg = UG(~x*z)), S,
     * n=100)
     * $Shat
     * y          x          z          u
     * y  2.930000 -1.4344254  0.0000000  0.0000000
     * x -1.434425  1.3799680 -0.3430373  0.0000000
     * z  0.000000 -0.3430373  1.5943070 -0.7442518
     * u  0.000000  0.0000000 -0.7442518  0.8100000
     *
     * $Lhat
     * y x z    u
     * y 2.93 0 0 0.00
     * x 0.00 0 0 0.00
     * z 0.00 0 0 0.00
     * u 0.00 0 0 0.81
     *
     * $Bhat
     * y x z         u
     * y 1.000000 0 0 0.0000000
     * x 0.489565 1 0 0.0000000
     * z 0.000000 0 1 0.9188294
     * u 0.000000 0 0 1.0000000
     *
     * $Ohat
     * y          x          z u
     * y 0  0.0000000  0.0000000 0
     * x 0  0.6777235 -0.3430373 0
     * z 0 -0.3430373  0.9104666 0
     * u 0  0.0000000  0.0000000 0
     *
     * $dev
     * [1] 21.57711
     *
     * $df
     * [1] 3
     *
     * $it
     * [1] 4
     *
     *
     *
     * </pre>
     */
    @Test
    public void testRicf1() {
        String[] varNames = new String[]{"y", "x", "z", "u"};
        int numVars = varNames.length;

        double[] values = {2.93, -1.7, 0.76, -0.06, -1.7, 1.64, -0.78, 0.1,
                0.76, -0.78, 1.66, -0.78, -0.06, 0.1, -0.78, 0.81};
        TetradMatrix m = matrix(values, numVars, numVars);

        ICovarianceMatrix s = new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), m, 30);

        Graph mag = new EdgeListGraph();
        Node x = new ContinuousVariable("x");
        Node y = new ContinuousVariable("y");
        Node z = new ContinuousVariable("z");
        Node u = new ContinuousVariable("u");
        mag.addNode(x);
        mag.addNode(y);
        mag.addNode(z);
        mag.addNode(u);

        mag.addDirectedEdge(y, x);
//        mag.addDirectedEdge(u, x);
        mag.addBidirectedEdge(x, z);
        mag.addDirectedEdge(u, z);

//        int n = 100;
        double tol = 1e-06;

        Ricf ricf = new Ricf();
        Ricf.RicfResult ricfResult = ricf.ricf(new SemGraph(mag), s, tol);

        // Test shat at least.
        double[] shatValues = {2.93, -1.434425, 0, 0,
                -1.434425, 1.379968, -0.343037, 0,
                0, -0.343037, 1.594307, -0.744252,
                0, 0, -0.744252, 0.81};

        double norm = normdiff(ricfResult, shatValues, numVars, numVars);
        assertTrue(norm < 0.0001);

        // sHat should be the same for the bidirected model.

        mag.removeEdges(mag.getEdges());
        mag.addBidirectedEdge(y, x);
//        mag.addDirectedEdge(u, x);
        mag.addBidirectedEdge(x, z);
        mag.addBidirectedEdge(u, z);

        ricf.ricf(new SemGraph(mag), s, tol);

        norm = normdiff(ricfResult, shatValues, numVars, numVars);
        assertTrue(norm < 0.0001);
    }

    private double normdiff(Ricf.RicfResult ricfResult, double[] shatValues,
                            int rows, int cols) {
        TetradMatrix shat = matrix(shatValues, rows, cols);
        TetradMatrix diff = shat.copy();
//        diff.assign(ricfResult.getShat(), PlusMult.plusMult(-1));
        diff = diff.minus(new TetradMatrix(ricfResult.getShat().toArray()));
        return diff.norm1();
    }

    private TetradMatrix matrix(double[] values, int rows, int cols) {
        TetradMatrix m = new TetradMatrix(rows, cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                m.set(i, j, values[i + cols * j]);
            }
        }

        return m;
    }

    @Test
    public void testCliques() {
        Graph graph = new EdgeListGraph();

        ContinuousVariable x0 = new ContinuousVariable("X0");
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");

        graph.addNode(x0);
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addUndirectedEdge(x0, x1);
        graph.addUndirectedEdge(x0, x2);
        graph.addUndirectedEdge(x0, x3);
        graph.addUndirectedEdge(x1, x2);
        graph.addUndirectedEdge(x1, x3);
        graph.addUndirectedEdge(x1, x4);
        graph.addUndirectedEdge(x2, x4);

        graph.addUndirectedEdge(x5, x0);
        graph.addUndirectedEdge(x5, x1);
        graph.addUndirectedEdge(x5, x2);

        new Ricf().cliques(graph);
    }

    @Test
    public void testCliques2() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 20,
                5, 5, 5, false));
        new Ricf().cliques(graph);
    }

    /**
     * Whittaker p. 59.
     */
    @Test
    public void testCliques3() {
        Graph graph = new EdgeListGraph();

        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");
        ContinuousVariable x6 = new ContinuousVariable("X6");
        ContinuousVariable x7 = new ContinuousVariable("X7");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);
        graph.addNode(x6);
        graph.addNode(x7);

        graph.addUndirectedEdge(x1, x2);
        graph.addUndirectedEdge(x1, x4);
        graph.addUndirectedEdge(x2, x3);
        graph.addUndirectedEdge(x2, x5);
        graph.addUndirectedEdge(x3, x5);
        graph.addUndirectedEdge(x4, x5);
        graph.addUndirectedEdge(x5, x6);

        new Ricf().cliques(graph);
    }

    @Test
    public void test2() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 10,
                30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        CovarianceMatrix cov = new CovarianceMatrix(data);

        Ricf.RicfResult result = new Ricf().ricf(new SemGraph(graph), cov, 0.001);

        result.getBhat();

    }

    @Ignore // File not found.
    public void test3() {
        try {
            DataReader reader = new DataReader();
            final File datapath = new File("/Users/josephramsey/Downloads/data6.txt");

            DataSet dataSet = reader.parseTabular(datapath);
            Graph mag = GraphUtils.loadGraphTxt(new File("/Users/josephramsey/Downloads/graph3.txt"));

            ICovarianceMatrix cov = new CovarianceMatrix(dataSet);

            new Ricf().ricf(new SemGraph(mag), cov, 0.001);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph g1 = GraphUtils.randomGraph(nodes1, 0, 5,
                0, 0, 0, false);
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g2 = GraphUtils.randomGraph(nodes, 0, 5,
                0, 0, 0, false);

        SemPm pm = new SemPm(g1);
        SemIm im = new SemIm(pm);

        DataSet dataset = im.simulateData(1000, false);

        ICovarianceMatrix cov = new CovarianceMatrix(dataset);

        new Ricf().ricf(new SemGraph(g1), cov, 0.001);
        new Ricf().ricf(new SemGraph(g2), cov, 0.001);
    }
}



