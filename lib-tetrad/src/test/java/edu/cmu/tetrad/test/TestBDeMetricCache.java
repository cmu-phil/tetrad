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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.BdeMetricCache;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Test of the iterate method of the FactoredBayesStructuralEM class.
 *
 * @author Frank Wimberly </p> NOTE:  The string "SEM" here does not mean
 *         "Structural Equation Model" but "Structural Expectation
 *         Maximization".
 */
public final class TestBDeMetricCache extends TestCase {

    public TestBDeMetricCache(String name) {
        super(name);
    }

    public static void rtestFB_SEM() {

        try {

            // No longer have this file.
            String fileName = "src/test/resources/testbdemetricX1X2X310k.dat";

            File file = new File(fileName);

            DataReader reader = new DataReader();
            DataSet ds = reader.parseTabular(file);

            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");
            Node x5 = new GraphNode("X5");
            //        graph = new EdgeListGraph();
            Dag graph = new Dag();


            graph.clear();

            // Add and remove some nodes.
            graph.addNode(x1);
            graph.addNode(x2);
            graph.addNode(x3);
            graph.addNode(x4);
            graph.addNode(x5);

            //graph.addDirectedEdge(X1, X2);
            //graph.addDirectedEdge(X2, X3);
            //graph.addDirectedEdge(X3, X4);
            graph.addDirectedEdge(x1, x2);
            graph.addDirectedEdge(x2, x3);
            graph.addDirectedEdge(x3, x4);

            BayesPm bayesPm = new BayesPm(graph);
            bayesPm.setNumCategories(x1, 2);
            bayesPm.setNumCategories(x2, 2);
            bayesPm.setNumCategories(x3, 2);
            bayesPm.setNumCategories(x4, 2);
            bayesPm.setNumCategories(x5, 2);

            //FactoredBayesStructuralEM fbsem = new FactoredBayesStructuralEM(dds, bayesPm);
            BdeMetricCache bdemc = new BdeMetricCache(ds, bayesPm);

            Set<Node> s1 = new HashSet<Node>();
            s1.add(x2);
            s1.add(x3);
            int c1 = bdemc.getScoreCount(x1, s1);
            assertEquals(c1, 3);

            Set<Node> s2 = new HashSet<Node>();
            s2.add(x3);
            int c2 = bdemc.getScoreCount(x4, s2);
            assertEquals(c2, 2);

            Set<Node> s3 = new HashSet<Node>();
            s3.add(x2);
            s3.add(x3);
            int c3 = bdemc.getScoreCount(x1, s3);
            assertEquals(c3, 3);

            System.out.println("c1, c2, c3 = " + c1 + " " + c2 + " " + c3);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void test1() {
        // blank
    }


    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBDeMetricCache.class);
    }
}





