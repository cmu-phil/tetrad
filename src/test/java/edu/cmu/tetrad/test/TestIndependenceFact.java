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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;


/**
 * Implements some tests of the FDR (False Discovery Rate) test.
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings({"UnusedDeclaration"})
public class TestIndependenceFact extends TestCase {
    public TestIndependenceFact(String name) {
        super(name);
    }

    public void testSimpleCase() {

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");

        Node z = new GraphNode("Z");
        Node w = new GraphNode("W");
        Node r = new GraphNode("R");

        IndependenceFact fact1 = new IndependenceFact(x, y);
        IndependenceFact fact2 = new IndependenceFact(y, x);

        System.out.println(fact1);
        System.out.println(fact2);

        assertEquals(fact1, fact2);

        IndependenceFact fact3 = new IndependenceFact(x, w);

        try {
            assertEquals(fact1, fact3);
        }
        catch (AssertionFailedError e) {
            // fail.
        }

        List<IndependenceFact> facts = new ArrayList<IndependenceFact>();

        facts.add(fact1);

        assertTrue(facts.contains(fact2));
    }

    public static Test suite() {
        return new TestSuite(TestIndependenceFact.class);
    }
}


