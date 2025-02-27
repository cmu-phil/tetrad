package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Number of parameters for a discrete Bayes model of the data. Must be for a discrete dataset.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumParametersEst implements Statistic {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for NumParametersEst.</p>
     */
    public NumParametersEst() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NumParams";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of parameters for the estimated graph for a Bayes or SEM model";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        if (dataModel.isDiscrete()) {
            DiscreteBicScore score = new DiscreteBicScore((DataSet) dataModel);

            Graph dag = GraphTransforms.dagFromCpdag(estGraph, null);
            List<Node> nodes = dag.getNodes();

            double params = 0.0;

            for (Node node : dag.getNodes()) {
                score.setPenaltyDiscount(1);
                int i = nodes.indexOf(node);
                List<Node> parents = dag.getParents(node);
                int[] parentIndices = new int[parents.size()];

                for (Node parent : parents) {
                    parentIndices[parents.indexOf(parent)] = nodes.indexOf(parent);
                }

                params += score.numParameters(i, parentIndices);
            }

            return params;
        } else if (dataModel.isContinuous()) {
            return estGraph.getNumEdges();
        } else {
            throw new IllegalArgumentException("Data must be discrete");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

