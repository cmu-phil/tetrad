package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.work_in_progress.MagCgBicScore;
import edu.cmu.tetrad.search.work_in_progress.MagDgBicScore;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Takes a MAG in a PAG using Zhang's method and then reports the MAG DG BIC score for it.
 */
public class MagCgScore implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public MagCgScore() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "MagCgScore";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "MAG CG BIC score for the Zhang MAG in the given PAG.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        if (!(dataModel instanceof DataSet)) throw new IllegalArgumentException("Expecting a dataset for MAG DG Score.");

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        MagCgBicScore magDgScore = new MagCgBicScore((DataSet) dataModel);
        magDgScore.setMag(mag);
        List<Node> nodes = mag.getNodes();
        double score = 0.0;

        for (Node node : nodes) {
            int i = nodes.indexOf(node);
            var parents = mag.getParents(node);
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
