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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.FruchtermanReingoldLayout;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to make sure the Fruchterman Reingold layout will run.
 *
 * @author Joseph Ramsey
 */
public final class TestFruchtermanReingoldLayout {

    @Test
    public void testLayout() {
        //        Dag dag = DataGraphUtils.createRandomDag(40, 0, 80, 6, 6, 6, true);

        Dag dag = new Dag();

        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");
        GraphNode x3 = new GraphNode("X3");
        GraphNode x4 = new GraphNode("X4");
        GraphNode x5 = new GraphNode("X5");
        GraphNode x6 = new GraphNode("X6");
        GraphNode x7 = new GraphNode("X7");

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);
        dag.addNode(x5);
        dag.addNode(x6);
        dag.addNode(x7);

        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x2, x3);
        dag.addDirectedEdge(x4, x5);
        dag.addDirectedEdge(x5, x6);

        Dag dag2 = new Dag(dag);

        GraphUtils.circleLayout(dag, 200, 200, 150);

        FruchtermanReingoldLayout layout = new FruchtermanReingoldLayout(dag);
        layout.doLayout();

        assertEquals(dag, dag2);
    }

    @Test
    public void testLayout2() {
        Dag dag = new Dag();

        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");

        x1.setCenter(40, 5);
        x2.setCenter(50, 5);

        dag.addNode(x1);
        dag.addNode(x2);

        dag.addDirectedEdge(x1, x2);

        Dag dag2 = new Dag(dag);

        FruchtermanReingoldLayout layout = new FruchtermanReingoldLayout(dag);
        layout.doLayout();

        assertEquals(dag, dag2);
    }
}





