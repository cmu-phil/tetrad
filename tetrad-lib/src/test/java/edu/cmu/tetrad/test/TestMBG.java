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

import java.util.*;

/**
 * @author Bryan Andrews
 */
public class TestMBG {

    @Test
    public void test1() {

//        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X3<->X4,X4<->X5,X3-->X5");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node node : variables) {
            order.add(0, node);
        }

//        int i = 3;
//        int[] js = new int[] {0, 1, 2, 4};

//        int i = 3;
//        int[] js = new int[] {0, 1, 2};

//        int i = 3;
//        int[] js = new int[] {0,1};

//        int i = 2;
//        int[] js = new int[] {0,1};

        int i = 4;
        int[] js = new int[]{0, 1, 2, 3};

        Node v1 = variables.get(i);

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

        long startTime = System.currentTimeMillis();

        List<List<Node>> heads = new ArrayList<>();
        List<List<Node>> tails = new ArrayList<>();
        constructHeadsTails(heads, tails, mbo, new ArrayList<>(), new ArrayList<>(), new HashSet<>(), v1, mag);

        for (int l = 0; l < heads.size(); l++) {
            List<Node> head = heads.get(l);
            List<Node> tail = tails.get(l);
            System.out.print("head: ");
            System.out.println(head);
            System.out.print("tail: ");
            System.out.println(tail);
            System.out.println();

            head.remove(v1);
            int h = head.size();
            int max = h + tail.size();
            for (int j = 0; j < 1 << h; j++) {
                List<Node> condSet = new ArrayList<>(tail);
                for (int k = 0; k < h; k++) {
                    if ((j & (1 << k)) > 0) {
                        condSet.add(head.get(k));
                    }
                }

                System.out.print((((max - condSet.size()) % 2) == 0) ? " + " : " - ");
                System.out.print(v1);
                System.out.print(" | ");
                System.out.println(condSet);
            }
            System.out.println();
        }

        long endTime = System.currentTimeMillis();

        System.out.println("That took " + (endTime - startTime) + " milliseconds");
    }

    private void constructHeadsTails(List<List<Node>> heads, List<List<Node>> tails, List<Node> mbo, List<Node> head, List<Node> in, Set<Node> an, Node v1, Graph mag) {
        head.add(v1);
        heads.add(head);

        List<Node> sib = new ArrayList<>();
        updateAncestors(an, v1, mag);
        updateIntrinsics(in, sib, an, v1, mbo, mag);

        List<Node> tail = new ArrayList<>(in);
        tail.removeAll(head);
        for (Node v2 : in) {
            tail.addAll(mag.getParents(v2));
        }
        tails.add(tail);

        for (Node v2 : sib) {
            constructHeadsTails(heads, tails, mbo.subList(mbo.indexOf(v2)+1,mbo.size()), new ArrayList<>(head), new ArrayList<>(in), new HashSet<>(an), v2, mag);
        }
    }

    private void updateAncestors(Set<Node> an, Node v1, Graph mag) {
        an.add(v1);

        for (Node v2 : mag.getParents(v1)) {
            updateAncestors(an, v2, mag);
        }
    }

    private void updateIntrinsics(List<Node> in, List<Node> sib, Set<Node> an, Node v1, List<Node> mbo, Graph mag) {
        in.add(v1);

        List<Node> mb = new ArrayList<>(mbo);
        mb.removeAll(in);

        for (Node v3 : in.subList(0,in.size())) {
            for (Node v2 : mb) {
                Edge e = mag.getEdge(v2,v3);
                if (e != null && e.getEndpoint1() == Endpoint.ARROW && e.getEndpoint2() == Endpoint.ARROW) {
                    if (an.contains(v2)) {
                        updateIntrinsics(in, sib, an, v2, mbo, mag);
                    } else {
                        sib.add(v2);
                    }
                }
            }
        }
    }

}





