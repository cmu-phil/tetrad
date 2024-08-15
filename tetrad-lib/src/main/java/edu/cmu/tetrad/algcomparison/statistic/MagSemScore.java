package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.work_in_progress.MagDgBicScore;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Takes a MAG in a PAG using Zhang's method and then reports the MAG SEM BIC score for it.
 */
public class MagSemScore implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public MagSemScore() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "MagSemScore";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "MAG SEM BIC score for the Zhang MAG in the given PAG.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        if (!(dataModel instanceof DataSet)) throw new IllegalArgumentException("Expecting a dataset for MAG DG Score.");

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        MagSemBicScore magDgScore = new MagSemBicScore(new CovarianceMatrix((DataSet) dataModel));
        magDgScore.setMag(mag);
        List<Node> nodes = mag.getNodes();
        double score = 0.0;

        for (Node node : nodes) {
            int i = nodes.indexOf(node);
            var parents = mag.getNodesInTo(node, Endpoint.ARROW);
            int[] _p = new int[parents.size()];
            for (int j = 0; j < parents.size(); j++) {
                _p[j] = nodes.indexOf(parents.get(j));
            }
            score += magDgScore.localScore(i, _p);
        }

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return (1 + tanh(value / 1.0e8)) / 2;
    }
}
