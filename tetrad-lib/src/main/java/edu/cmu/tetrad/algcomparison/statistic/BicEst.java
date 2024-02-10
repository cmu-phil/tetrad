package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import edu.cmu.tetrad.search.score.SemBicScorer;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Estimated BIC score. The BIC is calculated as 2L - k ln N, so "higher is better."
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BicEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    private boolean precomputeCovariances = true;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public BicEst() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "BicEst";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "BIC of the estimated CPDAG (depends only on the estimated DAG and the data)";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        if (dataModel.isDiscrete()) {
            DiscreteBicScore score = new DiscreteBicScore((DataSet) dataModel);

            Graph dag = GraphTransforms.dagFromCpdag(estGraph, null);
            List<Node> nodes = dag.getNodes();

            double _score = 0.0;

            for (Node node : dag.getNodes()) {
                score.setPenaltyDiscount(1);
                int i = nodes.indexOf(node);
                List<Node> parents = dag.getParents(node);
                int[] parentIndices = new int[parents.size()];

                for (Node parent : parents) {
                    parentIndices[parents.indexOf(parent)] = nodes.indexOf(parent);
                }

                _score += score.localScore(i, parentIndices);
            }

            return _score;
        } else if (dataModel.isContinuous()) {
            return SemBicScorer.scoreDag(GraphTransforms.dagFromCpdag(estGraph, null), dataModel, precomputeCovariances);
        } else {
            throw new IllegalArgumentException("Data must be either discrete or continuous");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }

    /**
     * Returns the precompute covariances flag.
     *
     * @param precomputeCovariances The precompute covariances flag.
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}

