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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.IndependenceFactsModel;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the Knowledge class.
 *
 * @author Joseph Ramsey
 */
public final class TestIndependenceFacts extends TestCase {
    private IndependenceFactsModel facts;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestIndependenceFacts(String name) {
        super(name);
    }

    public void test1() {
        IndependenceFactsModel facts = new IndependenceFactsModel();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        facts.add(new IndependenceFact(x1, x2, x3));
        facts.add(new IndependenceFact(x2, x3));
        facts.add(new IndependenceFact(x2, x4, x1, x2));
        facts.add(new IndependenceFact(x2, x4, x1, x3, x5));
        facts.add(new IndependenceFact(x2, x4, x3));
        facts.add(new IndependenceFact(x2, x4, x3, x6));

        System.out.println(facts);

        facts.remove(new IndependenceFact(x1, x2, x3));

//        System.out.println(facts);

        IndependenceFacts _facts = new IndependenceFacts(facts.getFacts());

        System.out.println(_facts.toString());

        assertTrue(_facts.isIndependent(x4, x2, x1, x2));
        assertTrue(_facts.isIndependent(x4, x2, x5, x3, x1));

        List<Node> l = new ArrayList<Node>();
        l.add(x1);
        l.add(x2);

        assertTrue(_facts.isIndependent(x4, x2, l));

    }

    public void test2() {
        File file = new File("resources/sample.independencies.txt");

        try {
            IndependenceFactsModel facts = IndependenceFactsModel.loadFacts(new FileReader(file));

            System.out.println(facts);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestIndependenceFacts.class);
    }
}





