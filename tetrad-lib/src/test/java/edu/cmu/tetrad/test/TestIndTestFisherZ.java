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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * @author Joseph Ramsey
 */
public class TestIndTestFisherZ {

    @Test
    public void testDirections() {
        RandomUtil.getInstance().setSeed(48285934L);

        Graph graph1 = new EdgeListGraph();
        Graph graph2 = new EdgeListGraph();

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");

        graph1.addNode(x);
        graph1.addNode(y);
        graph1.addNode(z);

        graph2.addNode(x);
        graph2.addNode(y);
        graph2.addNode(z);

        graph1.addEdge(Edges.directedEdge(x, y));
        graph1.addEdge(Edges.directedEdge(y, z));

        graph2.addEdge(Edges.directedEdge(x, y));
        graph2.addEdge(Edges.directedEdge(z, y));

        SemPm pm1 = new SemPm(graph1);
        SemPm pm2 = new SemPm(graph2);

        SemIm im1 = new SemIm(pm1);
        SemIm im2 = new SemIm(pm2);

        im2.setEdgeCoef(x, y, im1.getEdgeCoef(x, y));
        im2.setEdgeCoef(z, y, im1.getEdgeCoef(y, z));

        DataSet data1 = im1.simulateData(500, false);
        DataSet data2 = im2.simulateData(500, false);

        IndependenceTest test1 = new IndTestFisherZ(data1, 0.05);
        IndependenceTest test2 = new IndTestFisherZ(data2, 0.05);

        test1.isIndependent(data1.getVariable(x.getName()), data1.getVariable(y.getName()));
        double p1 = test1.getPValue();

        test2.isIndependent(data2.getVariable(x.getName()), data2.getVariable(z.getName()),
                data2.getVariable(y.getName()));
        double p2 = test2.getPValue();

        test2.isIndependent(data2.getVariable(x.getName()), data2.getVariable(z.getName()));
        double p3 = test2.getPValue();

        assertEquals(0, p1, 0.01);
        assertEquals(0, p2, 0.01);
        assertEquals(0.38, p3, 0.01);
    }
}


