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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestMarkovBlanketSearches {

    /**
     * Simple test using d-separation.
     */
    @Test
    public void testSubgraph1() {
        Graph graph = GraphConverter.convert("T-->X,X-->Y,W-->X,W-->Y");

        IndTestDSep test = new IndTestDSep(graph);

        MbSearch search = new GrowShrink(test);
        List<Node> blanket = search.findMb("T");

        List<Node> b = new ArrayList<>();
        b.add(graph.getNode("X"));
        b.add(graph.getNode("W"));

        assertEquals(b, blanket);
    }

    /**
     * Slightly harder test using d-separation.
     */
    @Test
    public void testSubgraph2() {
        Graph graph = GraphConverter.convert("P1-->T,P2-->T,T-->C1,T-->C2," +
                "T-->C3,PC1a-->C1,PC1b-->C1,PC2a-->C2,PC2b<--C2,PC3a-->C3," +
                "PC3b-->C3,PC1b-->PC2a,PC1a<--PC3b,U,V");

        IndTestDSep test = new IndTestDSep(graph);
        MbSearch mbSearch = new GrowShrink(test);
        List<Node> blanket = mbSearch.findMb("T");

        List<Node> mbd = GraphUtils.markovBlanketDag(graph.getNode("T"), graph).getNodes();
        mbd.remove(graph.getNode("T"));

        assertEquals(new HashSet<>(mbd), new HashSet<>(blanket));

    }

    @Test
    public void testRandom() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes1, 0, 10,
                5, 5, 5, false));
        IndependenceTest test = new IndTestDSep(dag);
        Mbfs search = new Mbfs(test, -1);

        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            List<Node> resultNodes = search.findMb(node.getName());
            Graph trueMb = GraphUtils.markovBlanketDag(node, dag);
            List<Node> trueNodes = trueMb.getNodes();
            trueNodes.remove(node);

            Collections.sort(trueNodes, new Comparator<Node>() {
                public int compare(Node n1, Node n2) {
                    return n1.getName().compareTo(n2.getName());
                }
            });

            Collections.sort(resultNodes, new Comparator<Node>() {
                public int compare(Node n1, Node n2) {
                    return n1.getName().compareTo(n2.getName());
                }
            });

            assertEquals(trueNodes, resultNodes);
        }
    }


}





