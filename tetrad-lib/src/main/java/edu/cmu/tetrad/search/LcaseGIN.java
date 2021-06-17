package edu.cmu.tetrad.search;

/**
 * Implements a test for GIN constraints in Xie F, Cai R, et al. (2020). "TGeneralized Independent Noise Condition for
 * Estimating Latent Variable Causal Graphs." NeurIPS 2020.
 *
 * @author Zhiyi Huang@DMIRLab, Ruichu Cai@DMIRLab
 * From DMIRLab: https://dmir.gdut.edu.cn/
 */

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public final class LcaseGIN {
    private Node i;
    private Node j;
    private Node k;
    private Node l;
    private double pValue;

    public LcaseGIN(Node i, Node j, Node k, Node l) {
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.pValue = Double.NaN;
    }

    public LcaseGIN(Node i, Node j, Node k, Node l, double pValue) {
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
        this.pValue = pValue;
    }

    public Node getI() {
        return i;
    }

    public Node getJ() {
        return j;
    }

    public Node getK() {
        return k;
    }

    public Node getL() {
        return l;
    }

    public int hashCode() {

        int hash = 17 * i.hashCode() * j.hashCode();
        hash += 29 * k.hashCode() * l.hashCode();

        return hash;
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        LcaseGIN gin = (LcaseGIN) o;
        return (i == gin.i && j == gin.j && k == gin.k && l == gin.l)
                || (i == gin.j && j == gin.i && k == gin.k && l == gin.l)
                || (i == gin.i && j == gin.j && k == gin.l && l == gin.k)
                || (i == gin.j && j == gin.i && k == gin.l && l == gin.k)
                || (i == gin.k && j == gin.l && k == gin.i && l == gin.j)
                || (i == gin.k && j == gin.l && k == gin.j && l == gin.i)
                || (i == gin.l && j == gin.k && k == gin.i && l == gin.j)
                || (i == gin.l && j == gin.k && k == gin.j && l == gin.i);
    }

    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");

        if (Double.isNaN(pValue)) {
            return "s(" + i + "," + j + ")*s(" + k + "," + l + ")-s(" + i + "," + k + ")*s(" + j + "," + l + ")";
        } else {
            return "<" + i + ", " + j + ", " + k + ", " + l + ", " + nf.format(pValue) + ">";
        }
    }

    public double getPValue() {
        return pValue;
    }

    public Set<Node> getNodes() {
        Set<Node> nodes = new HashSet<>();
        nodes.add(i);
        nodes.add(j);
        nodes.add(k);
        nodes.add(l);
        return nodes;
    }

}
