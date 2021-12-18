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
public class TestHeadsTails {

    @Test
    public void test1() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 3;
        int[] js = new int[] {0, 1, 2, 4};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test2() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 3;
        int[] js = new int[] {0, 1, 2};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test3() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 3;
        int[] js = new int[] {0,1};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test4() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X5-->X1");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 2;
        int[] js = new int[] {0,1};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test5() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X3<->X4,X3-->X5,X4<->X5");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 4;
        int[] js = new int[]{0, 1, 2, 3};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test6() {
        Graph mag = GraphConverter.convert("X1-->X2,X2<->X3,X2-->X4,X3<->X4,X3-->X5,X4<->X5");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 4;
        int[] js = new int[]{0, 1, 2, 3};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test7() {
        Graph mag = GraphConverter.convert("X1<--X2,X1<--X3,X1<--X4,X1<--X5");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 0;
        int[] js = new int[]{1, 2, 3, 4};

        headsTails(order, mag, i, js);
    }

    @Test
    public void test8() {
        Graph mag = GraphConverter.convert("A<->B,B<->C,C<->D,D<--E");
        List<Node> variables = mag.getNodes();

        List<Node> order = new ArrayList<>();
        for (Node v : variables) {
            order.add(0, v);
        }

        int i = 2;
        int[] js = new int[]{0, 1, 3, 4};

        headsTails(order, mag, i, js);
    }

    private void headsTails(List<Node> order, Graph mag, int i, int... js) {
        List<Node> variables = mag.getNodes();
        Node v1 = variables.get(i);

        List<Node> mbo = new ArrayList<>();
        Arrays.sort(js);
        for (Node v2 : order) {
            if (Arrays.binarySearch(js, variables.indexOf(v2)) >= 0) {
                mbo.add(v2);
            }
        }

//        System.out.println(mag);
//        System.out.println();

//        System.out.println(v1);
//        System.out.println(mbo);
//        System.out.println();

        long t1 = System.currentTimeMillis();

        List<List<Node>> heads = new ArrayList<>();
        List<Set<Node>> tails = new ArrayList<>();
        constructHeadsTails(heads, tails, mbo, new ArrayList<>(), new ArrayList<>(), new HashSet<>(), v1, mag);

        long t2 = System.currentTimeMillis();
        System.out.println("heads and tails took " + (t2 - t1) + " milliseconds");
        System.out.println();

        for (int l = 0; l < heads.size(); l++) {
            List<Node> head = heads.get(l);
            Set<Node> tail = tails.get(l);

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

        long t3 = System.currentTimeMillis();
        System.out.println("That took " + (t3 - t1) + " milliseconds");
        System.out.println();
    }

    private void constructHeadsTails(List<List<Node>> heads, List<Set<Node>> tails, List<Node> mbo, List<Node> head, List<Node> in, Set<Node> an, Node v1, Graph mag) {
        head.add(v1);
        heads.add(head);

        List<Node> sib = new ArrayList<>();
        updateAncestors(an, v1, mag);
        updateIntrinsics(in, sib, an, v1, mbo, mag);

        Set<Node> tail = new HashSet<>(in);
        head.forEach(tail::remove);
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





