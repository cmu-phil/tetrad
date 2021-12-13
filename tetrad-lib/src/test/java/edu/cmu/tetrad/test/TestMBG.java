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

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Bryan Andrews
 */
public class TestMBG {

    @Test
    public void test1() {

        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node node : variables) {
            order.add(0, node);
        }

        int i = 3;
        int[] js = new int[]{0, 1, 2, 4};

//        int i = 3;
//        int[] js = new int[]{0, 1, 2};

//        int i = 3;
//        int[] js = new int[] {0,1};

//        int i = 2;
//        int[] js = new int[] {0,1};

        Node node1 = variables.get(i);

        List<Node> mb = new ArrayList<>();
        for (int j : js) {
            mb.add(variables.get(j));
        }

        List<Node> mbo = new ArrayList<>();
        for (Node node2 : order) {
            if (mb.contains(node2)) {
                mbo.add(node2);
            }
        }

        List<List<Node>> heads = new ArrayList<>();
        constructHeads(node1, copy(mbo), new ArrayList<>(), heads, mag);

        for (List<Node> head : heads) {
            List<Node> anc = mag.getAncestors(head);

            Set<Node> dis = new TreeSet<>();
            for (Node node2 : head) {
                collectDistrictVisit(node2, dis, anc, mag);
            }

            List<Node> tail = new ArrayList<>(dis);
            tail.removeAll(head);
            for (Node node2 : dis) {
                for (Node node3 : mag.getParents(node2)) {
                    if (tail.contains(node3)) continue;
                    tail.add(node3);
                }
            }


            System.out.println(head);
            System.out.println(tail);
            System.out.println();


            for (int j = 0; j < 1 << tail.size(); j++) {
                List<Node> paraSet = copy(head);
                for (int k = 0; k < tail.size(); k++) {
                    if ((j & (1 << k)) > 0) {
                        paraSet.add(tail.get(k));
                    }
                }

                System.out.println(paraSet);

            }

            System.out.println();

        }
    }

    private void constructHeads (Node node1, List<Node> mbo, List<Node> head, List <List<Node>> heads, Graph mag) {
        head.add(node1);
        heads.add(head);

        while (!mbo.isEmpty()) {
            Node node2 = mbo.remove(0);

            if (mag.getEdge(node1, node2) == null) continue;
            if (mag.getAncestors(head).contains(node2)) continue;
            constructHeads(node2, copy(mbo), copy(head), heads, mag);
        }
    }

    public List<Node> getSiblings(Node node1, List<Node> anc, Graph mag){
        List<Node> siblings = new ArrayList<>();

        for (Node node2 : anc){
            Edge edge = mag.getEdge(node1, node2);

            if (edge == null) continue;
            if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.ARROW) {
                siblings.add(node2);
            }
        }

        return siblings;
    }

    private void collectDistrictVisit(Node node, Set<Node> dis, List<Node> anc, Graph mag) {
        if (dis.contains(node)) {
            return;
        }

        dis.add(node);
        List<Node> siblings = getSiblings(node, anc, mag);

        if (!siblings.isEmpty()) {
            for (Node sibling : siblings) {
                collectDistrictVisit(sibling, dis, anc, mag);
            }
        }
    }


    private List<Node> copy(List<Node> list1) {
        List<Node> list2 = new ArrayList<>();
        for (Node node : list1) {
            list2.add(node);
        }
        return list2;
    }

}





