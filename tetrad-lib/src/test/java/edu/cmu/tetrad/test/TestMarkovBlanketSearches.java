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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.GrowShrink;
import edu.cmu.tetrad.search.IMbSearch;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcMb;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestMarkovBlanketSearches {

    /**
     * Simple test using n-separation.
     */
    @Test
    public void testSubgraph1() {
        Graph graph = GraphUtils.convert("T-->X,X-->Y,W-->X,W-->Y");

        MsepTest test = new MsepTest(graph);

        IMbSearch search = new GrowShrink(test);
        Set<Node> blanket = search.findMb(test.getVariable("T"));

        Set<Node> b = new HashSet<>();
        b.add(graph.getNode("X"));
        b.add(graph.getNode("W"));

        assertEquals(b, blanket);
    }

    /**
     * Slightly harder test using d-separation.
     */
//    @Test
    public void testSubgraph2() {
        Graph graph = GraphUtils.convert("P1-->T,P2-->T,T-->C1,T-->C2," +
                                         "T-->C3,PC1a-->C1,PC1b-->C1,PC2a-->C2,PC2b<--C2,PC3a-->C3," +
                                         "PC3b-->C3,PC1b-->PC2a,PC1a<--PC3b,U,V");

        MsepTest test = new MsepTest(graph);
        IMbSearch mbSearch = new GrowShrink(test);
        Set<Node> blanket = mbSearch.findMb(test.getVariable("T"));

        List<Node> mbd = GraphUtils.markovBlanketSubgraph(graph.getNode("T"), graph).getNodes();
        mbd.remove(graph.getNode("T"));

        assertEquals(new HashSet<>(mbd), new HashSet<>(blanket));

    }

    //    @Test
    public void testRandom() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = new Dag(RandomGraph.randomGraph(nodes1, 0, 10,
                5, 5, 5, false));
        IndependenceTest test = new MsepTest(dag);
        PcMb search = new PcMb(test, -1);

        dag = GraphUtils.replaceNodes(dag, nodes1);

        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            Set<Node> resultNodes = search.findMb(node);

            Graph trueMb = GraphUtils.markovBlanketSubgraph(node, dag);
            Set<Node> trueNodes = new HashSet<>(trueMb.getNodes());
            trueNodes.remove(node);

//            trueNodes.sort(Comparator.comparing(Node::getName));
//            resultNodes.sort(Comparator.comparing(Node::getName));

//            assertEquals(trueNodes, resultNodes);
        }
    }


}





