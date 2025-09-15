package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.PagCache;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Represents a statistic that calculates the number of correct visible ancestors in the true graph that are also
 * visible ancestors in the estimated graph.
 */
public class NumCorrectVisibleEdges implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumCorrectVisibleEdges() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#CorrectVis";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Returns the number of visible edges X->Y in the estimated graph where X and Y have no latent confounder in the true graph.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph dag = PagCache.getInstance().getDag(trueGraph);

        GraphUtils.addEdgeSpecializationMarkup(estGraph);

        if (dag == null) {
            return -99;
        }

        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                boolean existsLatentConfounder = false;

                // A latent confounder is a latent node z such that there is a trek x<~~(z)~~>y, so we can limit the
                // length of these treks to 3.
                List<List<Node>> treks = dag.paths().treks(x, y, 3);

                // If there is a trek, x<~~z~~>y, where z is latent, then the edge is not semantically visible.
                for (List<Node> trek : treks) {
                    if (GraphUtils.isConfoundingTrek(dag, trek, x, y)) {
                        existsLatentConfounder = true;
                        break;
                    }
                }

                if (!existsLatentConfounder) {
                    tp++;
                }
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
