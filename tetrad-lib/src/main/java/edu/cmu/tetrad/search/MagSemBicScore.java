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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author Bryan Andrews
 */
public class MagSemBicScore implements Score{

    private final List<Node> variables;

    private Graph mag;

    private List<Node> order;

    private SemBicScore score;

    public MagSemBicScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        this.score = new SemBicScore(covariances);
        this.variables = covariances.getVariables();
        this.mag = null;
        this.order = null;
    }

    public MagSemBicScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.score = new SemBicScore(dataSet);
        this.variables = dataSet.getVariables();
        this.mag = null;
        this.order = null;
    }

    public Graph getMag() {
        return this.mag;
    }

    public void setMag(Graph mag) {
        this.mag = mag;
    }

    public void resetMag() {
        this.mag = null;
    }

    public List<Node> getOrder() {
        return this.order;
    }

    public void setOrder(List<Node> order) {
        this.order = order;
    }

    public void resetOrder() {
        this.order = null;
    }









    @Override
    public double localScore(int i, int... js) {
        if (this.mag == null || this.order == null) {
            return this.score.localScore(i, js);
        }

        double score = 0;

        Node node1 = this.variables.get(i);

        List<Node> mb = new ArrayList<>();
        for (int j : js) {
            mb.add(this.variables.get(j));
        }

        List<Node> mbo = new ArrayList<>();
        for (Node node2 : this.order) {
            if (mb.contains(node2)) {
                mbo.add(node2);
            }
        }

        List<List<Node>> heads = new ArrayList<>();
        constructHeads(node1, copy(mbo), new ArrayList<>(), heads);

        for (List<Node> head : heads) {
            List<Node> anc = this.mag.getAncestors(head);

            Set<Node> dis = new TreeSet<>();
            for (Node node2 : head) {
                collectDistrictVisit(node2, dis, anc);
            }

            List<Node> tail = new ArrayList<>(dis);
            tail.removeAll(head);
            for (Node node2 : dis) {
                for (Node node3 : this.mag.getParents(node2)) {
                    if (tail.contains(node3)) continue;
                    tail.add(node3);
                }
            }

//            System.out.println(head);
//            System.out.println(tail);
//            System.out.println();

            List<Node> temp = copy(head);
            temp.remove(node1);
            for (int j = 0; j < 1 << temp.size(); j++) {
                List<Node> condSet = copy(tail);
                for (int k = 0; k < temp.size(); k++) {
                    if ((j & (1 << k)) > 0) {
                        condSet.add(temp.get(k));
                    }
                }

                int[] parents = new int[j];
                for (int k = 0 ; k < j ; k++){
                    parents[k] = this.variables.indexOf(condSet.get(k));
                }

                if (((temp.size() - j) % 2) == 0) {
                    score += this.score.localScore(i, parents);
                } else {
                    score -= this.score.localScore(i, parents);
                }

//                System.out.print((((temp.size() - j) % 2) == 0) ? " + " : " - ");
//                System.out.print(node1);
//                System.out.print(" | ");
//                System.out.println(condSet);
            }
//            System.out.println();
        }
        return score;
    }

    private void constructHeads (Node node1, List<Node> mbo, List<Node> head, List <List<Node>> heads) {
        head.add(node1);
        heads.add(head);

        while (!mbo.isEmpty()) {
            Node node2 = mbo.remove(0);

            if (this.mag.getEdge(node1, node2) == null) continue;
            if (this.mag.getAncestors(head).contains(node2)) continue;
            constructHeads(node2, copy(mbo), copy(head), heads);
        }
    }

    public List<Node> getSiblings(Node node1, List<Node> anc){
        List<Node> siblings = new ArrayList<>();

        for (Node node2 : anc){
            Edge edge = this.mag.getEdge(node1, node2);

            if (edge == null) continue;
            if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.ARROW) {
                siblings.add(node2);
            }
        }

        return siblings;
    }

    private void collectDistrictVisit(Node node, Set<Node> dis, List<Node> anc) {
        if (dis.contains(node)) {
            return;
        }

        dis.add(node);
        List<Node> siblings = getSiblings(node, anc);

        if (!siblings.isEmpty()) {
            for (Node sibling : siblings) {
                collectDistrictVisit(sibling, dis, anc);
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

    public double getPenaltyDiscount() {
        return this.score.getPenaltyDiscount();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.score.setPenaltyDiscount(penaltyDiscount);
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    @Override
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    @Override
    public double localScore(int i) {
        return localScore(i, new int[0]);
    }

    @Override
    public int getSampleSize() {
        return this.score.getSampleSize();
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public int getMaxDegree() {
        return this.score.getMaxDegree();
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

}