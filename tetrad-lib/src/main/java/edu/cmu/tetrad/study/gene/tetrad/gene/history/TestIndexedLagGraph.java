///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the IndexedLagGraph class by constructing graphs with randomly chosen parameters and seeing if they have the
 * required properties.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestIndexedLagGraph extends TestCase {

    /**
     * Change the name of this constructor to match the name of the test class.
     *
     * @param name a {@link java.lang.String} object
     */
    public TestIndexedLagGraph(String name) {
        super(name);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     *
     * @return a {@link junit.framework.Test} object
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestIndexedLagGraph.class);
    }

    /**
     * <p>testConstruction.</p>
     */
    public void testConstruction() {
        LagGraph lagGraph = new BasicLagGraph();
        lagGraph.addFactor("G1");
        lagGraph.addFactor("G2");
        lagGraph.addFactor("G3");
        lagGraph.addEdge("G1", new LaggedFactor("G3", 1));
        lagGraph.addEdge("G2", new LaggedFactor("G1", 2));
        lagGraph.addEdge("G3", new LaggedFactor("G2", 3));
        lagGraph.addEdge("G3", new LaggedFactor("G3", 4));

        System.out.println(lagGraph);

        IndexedLagGraph indexedLagGraph = new IndexedLagGraph(lagGraph);

        System.out.println(indexedLagGraph);

        assertEquals("G1", indexedLagGraph.getFactor(0));
        assertEquals("G2", indexedLagGraph.getFactor(1));
        assertEquals("G3", indexedLagGraph.getFactor(2));

        assertEquals(2, indexedLagGraph.getParent(0, 0).getIndex());
        assertEquals(0, indexedLagGraph.getParent(1, 0).getIndex());
        assertEquals(1, indexedLagGraph.getParent(2, 0).getIndex());
        assertEquals(2, indexedLagGraph.getParent(2, 1).getIndex());

        assertEquals(1, indexedLagGraph.getParent(0, 0).getLag());
        assertEquals(2, indexedLagGraph.getParent(1, 0).getLag());
        assertEquals(3, indexedLagGraph.getParent(2, 0).getLag());
        assertEquals(4, indexedLagGraph.getParent(2, 1).getLag());
    }
}





