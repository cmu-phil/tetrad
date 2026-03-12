///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Cdnod;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests for CD-NOD.
 *
 * @author josephramsey
 */
public class TestCdnod {

    @Test
    public void testCdnodBasic() throws InterruptedException {
        // Create a simple DAG: X1 -> X2, X1 -> X3, X2 -> X3
        Graph dag = new EdgeListGraph();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x1, x3);
        dag.addDirectedEdge(x2, x3);

        // Generate data
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(1000, false);

        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        Cdnod cdnod = new Cdnod.Builder()
                .test(test)
                .data(data)
                .build();

        Graph result = cdnod.search();

        assertNotNull(result);
        System.out.println("Result graph: " + result);
    }

    @Test
    public void testCdnodWithContext() throws InterruptedException {
        // Create a simple DAG with context: C -> X1 -> X2
        Graph dag = new EdgeListGraph();
        Node c = new ContinuousVariable("C");
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        dag.addNode(c);
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addDirectedEdge(c, x1);
        dag.addDirectedEdge(x1, x2);

        // Generate data
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = semIm.simulateData(1000, false);
        data.getVariable("C").setName("C");
        data.getVariable("X1").setName("X1");
        data.getVariable("X2").setName("X2");

        // Set C as context via Knowledge tier 0
        Knowledge knowledge = new Knowledge();
        knowledge.setTier(0, Collections.singletonList("C"));
        knowledge.setTier(1, java.util.Arrays.asList("X1", "X2"));

        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        Cdnod cdnod = new Cdnod.Builder()
                .test(test)
                .data(data)
                .knowledge(knowledge)
                .build();

        Graph result = cdnod.search();

        assertNotNull(result);
        System.out.println("Result graph with context: " + result);

        // Use nodes from the result graph for assertions
        Node rc = result.getNode("C");
        Node rx1 = result.getNode("X1");

        // C should be a parent of X1 and have no parents
        assertTrue(result.isParentOf(rc, rx1));
        assertTrue(result.getParents(rc).isEmpty());
    }
}
