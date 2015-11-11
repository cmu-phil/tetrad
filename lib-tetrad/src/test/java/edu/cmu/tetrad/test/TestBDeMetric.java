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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Frank Wimberly
 */
public final class TestBDeMetric extends TestCase {

    public TestBDeMetric(String name) {
        super(name);
    }

    public void testTemp() {

    }

//    public static void testMetric() {
//        String fileName = "test_data/testbdemetricshort.dat";
//        File file = new File(fileName);
//
//        try {
//
////            ds = DataLoaders.loadDiscreteData(file, DelimiterType.TAB, "#",
////                    null);
//
//            DataReader reader = new DataReader();
//            DataSet ds = reader.parseTabular(file);
//
//            Node x1 = new GraphNode("X1");
//            Node x2 = new GraphNode("X2");
//            Node x3 = new GraphNode("X3");
//            Node x4 = new GraphNode("X4");
//            Node x5 = new GraphNode("X5");
//            //        graph = new EdgeListGraph();
//            Dag graph = new Dag();
//
//
//            graph.clear();
//
//            // Add and remove some nodes.
//            graph.addNode(x1);
//            graph.addNode(x2);
//            graph.addNode(x3);
//            graph.addNode(x4);
//            graph.addNode(x5);
//
//            graph.addDirectedEdge(x1, x2);
//            graph.addDirectedEdge(x2, x3);
//            graph.addDirectedEdge(x3, x4);
//
//
//            BayesPm bayesPm = new BayesPm(graph);
//            bayesPm.setNumCategories(x1, 2);
//            bayesPm.setNumCategories(x2, 2);
//            bayesPm.setNumCategories(x3, 2);
//            bayesPm.setNumCategories(x4, 2);
//            bayesPm.setNumCategories(x5, 2);
//
//            BdeMetric bdem = new BdeMetric(ds, bayesPm);
//            double scoreOrig = bdem.score();
//            System.out.println("Score of generating PM = " + scoreOrig);
//
//            List<Graph> variants1 = ModelGenerator.generate(graph);
//
//            //System.out.println("Size of list = " + variants1.size());
//            //assertEquals(28, variants1.size());
//
//
//            for (Graph aVariants1 : variants1) {
//                Dag d = new Dag(aVariants1);
//                BayesPm bpm = new BayesPm(d);
//
//                BdeMetric bdemr = new BdeMetric(ds, bpm);
//                double scorer = bdemr.score();
//                //System.out.println(r);
//                System.out.println("Score for above graph = " + scorer);
//            }
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }


    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestBDeMetric.class);
    }
}





