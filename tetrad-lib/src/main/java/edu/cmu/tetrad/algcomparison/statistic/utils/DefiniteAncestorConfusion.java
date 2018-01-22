package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.List;

/**
 * A confusion matrix for ancestor relationships.
 *
 * @author jdramsey, rubens (November, 2016)
 */
public class DefiniteAncestorConfusion {

    private int afp;
    private int afn;
    private int atp;
    private int atn;
    private int fpna;
    private int fnna;
    private int tpna;
    private int tnna;
    private int fpnd;
    private int fnnd;
    private int tpnd;
    private int tnnd;

    public DefiniteAncestorConfusion(Graph truth, Graph est) {
        est = GraphUtils.replaceNodes(est, truth.getNodes());
        truth = GraphUtils.replaceNodes(truth, est.getNodes());


        List<Node> nodes = truth.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++){
                if (i == j) continue;

                boolean t = truth.isAncestorOf(nodes.get(i), nodes.get(j));
                boolean tji = truth.isAncestorOf(nodes.get(j), nodes.get(i));
                boolean tnd = !t && !tji;
                boolean tna = truth.existsSemiDirectedPathFromTo(nodes.get(i), Collections.singleton(nodes.get(j)));
                boolean e = est.isAncestorOf(nodes.get(i), nodes.get(j));
                boolean eji = est.isAncestorOf(nodes.get(j), nodes.get(i));
                boolean end = !e && !eji;
                boolean ena = est.existsSemiDirectedPathFromTo(nodes.get(i), Collections.singleton(nodes.get(j)));

                if (t && !e) setAfp(getAfp() + 1);
                if (e && !t) setAfn(getAfn() + 1);
                if (t && e) setAtp(getAtp() + 1);
                if (!t && !e) setAtn(getAtn() + 1);

                if (tna && !ena) setFpna(getFpna() + 1);
                if (ena && !tna) setFnna(getFnna() + 1);
                if (tna && ena) setTpna(getTpnd() + 1);
                if (!tna && !ena) setTnna(getTnna() + 1);

                if (tnd && !end) fpnd = getFpnd() + 1;
                if (end && !tnd) fnnd = getFnnd() + 1;
                if (tnd && end) tpnd = getTpnd() + 1;
                if (!tnd && !end) tnnd = getTnnd() + 1;

            }
        }
    }

    public int getAfp() {
        return afp;
    }

    public int getAfn() {
        return afn;
    }

    public int getAtp() {
        return atp;
    }

    public int getAtn() {
        return atn;
    }

    public int getFpnd() {
        return fpnd;
    }

    public int getFnnd() {
        return fnnd;
    }

    public int getTpnd() {
        return tpnd;
    }

    public int getTnnd() {
        return tnnd;
    }

    public void setAfp(int afp) {
        this.afp = afp;
    }

    public void setAfn(int afn) {
        this.afn = afn;
    }

    public void setAtp(int atp) {
        this.atp = atp;
    }

    public void setAtn(int atn) {
        this.atn = atn;
    }

    public int getFpna() {
        return fpna;
    }

    public void setFpna(int fpna) {
        this.fpna = fpna;
    }

    public int getFnna() {
        return fnna;
    }

    public void setFnna(int fnna) {
        this.fnna = fnna;
    }

    public int getTpna() {
        return tpna;
    }

    public void setTpna(int tpna) {
        this.tpna = tpna;
    }

    public int getTnna() {
        return tnna;
    }

    public void setTnna(int tnna) {
        this.tnna = tnna;
    }
}