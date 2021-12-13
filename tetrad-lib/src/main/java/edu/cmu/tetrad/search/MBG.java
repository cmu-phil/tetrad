package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// NOTES FOR THE CLASS:

// for every contiguous set of bi-directed edges,
// split into barren and ancestor halves adding the parents to the latter
// ---these become the head and tail respectively

// the m-connecting sets are constructed from each head union with the subsets of its tail

// the score should remember interaction information for quick computation



public class MBG implements Score{

    private final DataSet dataSet;
    private final List<Node> variables;
    private double penaltyDiscount;

    private Graph mag;
    private List<Node> order;
//    private ? ii;

    public MBG(DataSet dataSet, double penaltyDiscount) {
        if (dataSet == null) {
            throw new NullPointerException();
        }
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;
        this.mag = null;
        this.order = null;
    }

    public Graph getMAG() {
        return this.mag;
    }

    public void setMAG(Graph mag) {
        this.mag = mag;
    }

    public void resetMAG() {
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

    private void getLocalSets(int i, int... js) {
        // get the heads and tails that are subsets of the parents

        Node node1 = this.variables.get(i);
        List<Node> mb = new ArrayList<>();
        List<Node> mbo = new ArrayList<>();
        for (int j : js) {
            mb.add(this.variables.get(j));
        }
        for (Node node2 : this.order) {
            if (mb.contains(node2)) {
                mbo.add(node2);
            }
        }

        List<Node> head = new ArrayList<>();
//        List<Node> tail = new ArrayList<>();
        head.add(node1);

        for (Node node2 : mbo) {
            Edge edge = this.mag.getEdge(node1, node2);
            if (edge == null || this.mag.isAncestorOf(node2, node1)) {
                continue;
            } else {
                head.add(node2);
                // recursive call
            }
        }






    }


    @Override
    public double localScore(int node, int... parents) {

//        get the heads and tails containing node such that their union is a subset of parents
        return 0;
    }











    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
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
        return dataSet.getNumRows();
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(dataSet.getNumRows()));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "MBG Penalty " + nf.format(penaltyDiscount);
    }

}