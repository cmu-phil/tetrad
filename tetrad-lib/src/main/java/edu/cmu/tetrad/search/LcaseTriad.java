package edu.cmu.tetrad.search;

/**
 * Implements a test for Triad constraints in Cai R, Xie F, et al. (2019). "Triad constraints for learning causal
 * structure of latent variables." Advances in Neural Information Processing Systems, pp. 12863-12872. 2019.
 *
 * @author Zhiyi Huang@DMIRLab, Ruichu Cai@DMIRLab
 * From DMIRLab: https://dmir.gdut.edu.cn/
 */

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.List;

public final class LcaseTriad {
    private double alpha;

    /**
     * Constructs a new Triad test. The given significance level is used.
     *
     * @param alpha   The alpha level of the HSIC Independence test.
     */
    public LcaseTriad(double alpha) {
        this.alpha = alpha;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    private double[] cal_eijk(double[] x, double[] y, double[] z) {
        assert (x.length == y.length);
        assert (x.length == z.length);

        double covxz = 0.0;
        double covyz = 0.0;
        double[] mean = new double[5];

        for (int i = 0; i < 5; i++) {
            mean[i] = 0.0;
        }

        for (int i = 0; i < x.length; i++) {
            mean[0] += x[i];
            mean[1] += y[i];
            mean[2] += z[i];
            mean[3] += x[i] * z[i];
            mean[4] += y[i] * z[i];
        }

        for (int i = 0; i < 5; i++) {
            mean[i] /= x.length;
        }
        covxz = (mean[3] - mean[0] * mean[2]);
        covyz = (mean[4] - mean[1] * mean[2]);

        double[] eijk = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            eijk[i] = x[i] - covxz / covyz * y[i];
        }
        return eijk;
    }

    public boolean isSatisfy(double[] x, double[] y, double[] z) {
        double[] eijk = cal_eijk(x, y, z);
        double[][] data = new double[x.length][2];

        for (int i = 0; i < x.length; i++) {
            data[i][0] = z[i];
            data[i][1] = eijk[i];
        }

        Matrix m = new Matrix(data);

        List<Node> list = new ArrayList<>();
        Node ZNode = new GraphNode("Z");
        Node ENode = new GraphNode("E");
        list.add(ZNode);
        list.add(ENode);

        IndTestHsic testHsic = new IndTestHsic(m, list, alpha);

        return testHsic.isIndependent(ZNode, ENode);
    }

}
