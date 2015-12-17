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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author Joseph Ramsey
 */
public final class TestKnowledge {

    @Test
    public final void test1() {
        List<Node> nodes1 = new ArrayList<Node>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph g = GraphUtils.randomGraph(nodes1, 0, 10,
                3, 3, 3, false);
        g.getNode("X1").setName("X1.1");
        g.getNode("X2").setName("X2-1");

        List<Node> nodes = g.getNodes();

        List<String> varNames = new ArrayList<String>();

        for (Node node : nodes) {
            varNames.add(node.getName());
        }

        IKnowledge knowledge = new Knowledge2(varNames);

        knowledge.addToTier(0, "X1.*1");
        knowledge.addToTier(0, "X2-1");
        knowledge.addToTier(1, "X3");

        knowledge.setForbidden("X4", "X5");

        knowledge.setRequired("X6", "X7");
        knowledge.setRequired("X7", "X8");

        assertTrue(knowledge.isForbidden("X4", "X5"));
        assertFalse(knowledge.isForbidden("X1.1", "X2-1"));
        assertTrue(knowledge.isForbidden("X3", "X2-1"));

        assertTrue(knowledge.isRequired("X6", "X7"));

        IKnowledge copy = knowledge.copy();

        assertTrue(copy.isForbidden("X4", "X5"));
        assertFalse(copy.isForbidden("X1", "X2-1"));
        assertTrue(copy.isForbidden("X3", "X2-1"));

        knowledge.setTierForbiddenWithin(0, true);

        assertTrue(knowledge.isForbidden("X1.1", "X2-1"));
        assertTrue(knowledge.isForbidden("X2-1", "X1.1"));
        assertFalse(knowledge.isForbidden("X1.1", "X1.1"));

        boolean found = false;

        for (Iterator i = knowledge.forbiddenEdgesIterator(); i.hasNext();) {
            KnowledgeEdge edge = (KnowledgeEdge) i.next();
            if (edge.getFrom().equals("X1.1") && edge.getTo().equals("X2-1")) {
                found = true;
            }
        }

        assertTrue(found);

        knowledge.setTierForbiddenWithin(0, false);

        assertFalse(knowledge.isForbidden("X1.1", "X2-1"));
        assertFalse(knowledge.isForbidden("X2-1", "X1.1"));
        assertFalse(knowledge.isForbidden("X1.1", "X1.1"));
    }

    @Test
    public final void test2() {
        List<Node> nodes1 = new ArrayList<Node>();

        for (int i = 0; i < 100; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = GraphUtils.randomGraph(nodes1, 0, 100,
                3, 3, 3, false);

        List<Node> nodes = g.getNodes();

        List<String> names = new ArrayList<String>();
        for (Node node : nodes) names.add(node.getName());

        Knowledge2 knowledge = new Knowledge2(names);

        knowledge.addToTier(0, "X1*");
        knowledge.addToTier(1, "X2*");

        knowledge.setRequired("X4*,X6*", "X5*");
        knowledge.setRequired("X6*", "X5*");

        assertTrue(knowledge.isForbidden("X20", "X10"));
        assertTrue(knowledge.isRequired("X6","X5"));
    }

    @Test
    public final void test3() {
        List<String> vars = new ArrayList<String>();

        final int numVars = 1000;

        for (int i = 0; i < numVars; i++) {
            vars.add("X" + i);
        }

        IKnowledge knowledge = new Knowledge2(vars);

        knowledge.setForbidden("X1*", "X2*");
        knowledge.setForbidden("X3*", "X4*");
        knowledge.setForbidden("X5*", "X6*");

        knowledge.addToTier(0, "X7*");
        knowledge.addToTier(1, "X8*");
        knowledge.addToTier(2, "X9*");

        long start = System.currentTimeMillis();

        for (int i = 0; i < numVars; i++) {
            knowledge.isForbidden("X11", "X22");
        }

        long stop = System.currentTimeMillis();
    }
}





