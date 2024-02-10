package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by the Erdos-Renyi method (probabiliy of edge fixed, # edges not).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ErdosRenyi implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        double p = parameters.getDouble(Params.PROBABILITY_OF_EDGE);
        int m = parameters.getInt(Params.NUM_MEASURES);
        int l = parameters.getInt(Params.NUM_LATENTS);
        int t = (m + l) * (m + l - 1) / 2;
        final int max = Integer.MAX_VALUE;
        int e = (int) (p * t);

        return edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(
                m + l, l, e, max, max, max, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Graph constructed the Erdos-Renyi method (p fixed, # edges not)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.NUM_MEASURES);
        parameters.add(Params.NUM_LATENTS);
        parameters.add(Params.PROBABILITY_OF_EDGE);
        return parameters;
    }
}
