package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.List;

/**
 * A confusion matrix for ancestor relationships.
 *
 * @author jdramsey, rubens (November, 2016)
 */
public class AncestorConfusion {

    private int fp;
    private int fn;
    private int tp;
    private int tn;

    public AncestorConfusion(Graph truth, Graph est) {
        est = GraphUtils.replaceNodes(est, truth.getNodes());
        truth = GraphUtils.replaceNodes(truth, est.getNodes());


        List<Node> nodes = truth.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++){
                if (i == j) continue;

                boolean t = truth.isAncestorOf(nodes.get(i), nodes.get(j));
                boolean e = est.isAncestorOf(nodes.get(i), nodes.get(j));

                if (t & !e) setFn(getFn() + 1);
                if (e & !t) setFp(getFp() + 1);
                if (t & e) setTp(getTp() + 1);
                if (!t & !e) setTn(getTn() + 1);
            }
        }
    }

    public int getFp() {
        return fp;
    }

    public void setFp(int fp) {
        this.fp = fp;
    }

    public int getFn() {
        return fn;
    }

    public void setFn(int fn) {
        this.fn = fn;
    }

    public int getTp() {
        return tp;
    }

    public void setTp(int tp) {
        this.tp = tp;
    }

    public int getTn() {
        return tn;
    }

    public void setTn(int tn) {
        this.tn = tn;
    }
}
