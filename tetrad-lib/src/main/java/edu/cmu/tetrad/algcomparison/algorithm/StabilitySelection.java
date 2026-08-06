///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.sweep.ParameterSweep;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stability selection: runs the wrapped algorithm on resamples of the data and returns the graph containing exactly
 * those edges (with orientation) appearing in more than "percentStability" of the resample graphs.
 * <p>
 * Resampling and the resample searches are delegated to the shared machinery in {@link ParameterSweep}
 * ({@code drawResamples} and {@code searchOnResamples}); this class keeps its historical edge-counting and
 * thresholding semantics. Compared to the pre-2026 implementation, the resample draws are now seed-controllable via
 * Params.SEED, resample graphs have their nodes replaced by the original variables before counting (so edges are
 * comparable across resamples), and a concurrency hazard in the resample collection was removed. Note that a fixed
 * seed makes the resampled row sets exactly reproducible but makes the output graph reproducible only if the
 * wrapped algorithm is itself deterministic; algorithms with internal thread pools (e.g. FGES) can break score
 * near-ties differently between runs, which can flip edges whose resample counts sit at the percentStability
 * threshold.
 * <p>
 * Parameters read from the Parameters object: "numSubsamples", "percentSubsampleSize", "percentStability", and
 * optionally Params.SEED.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StabilitySelection implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The algorithm to use for the resample searches.
     */
    private final Algorithm algorithm;

    /**
     * <p>Constructor for StabilitySelection.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public StabilitySelection(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _dataSet = (DataSet) dataSet;

        double percentageB = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");
        long seed = parameters.getLong(Params.SEED, -1L);

        List<DataSet> resamples = ParameterSweep.drawResamples(_dataSet, numSubsamples, percentageB, true, seed);

        List<Graph> graphs;

        try {
            graphs = ParameterSweep.searchOnResamples(this.algorithm, parameters, resamples,
                    _dataSet.getVariables(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        Map<Edge, Integer> counts = new HashMap<>();

        for (Graph graph : graphs) {
            for (Edge edge : graph.getEdges()) {
                increment(edge, counts);
            }
        }

        Graph outputGraph = new EdgeListGraph(dataSet.getVariables());
        double percentStability = parameters.getDouble("percentStability");

        for (Edge edge : counts.keySet()) {
            if (counts.get(edge) > percentStability * numSubsamples) {
                outputGraph.addEdge(edge);
            }
        }

        return outputGraph;
    }

    /**
     * Increments the count for an edge.
     *
     * @param edge   the edge.
     * @param counts the count map.
     */
    private void increment(Edge edge, Map<Edge, Integer> counts) {
        counts.putIfAbsent(edge, 0);
        counts.put(edge, counts.get(edge) + 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return this.algorithm.getComparisonGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Stability selection for " + this.algorithm.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.algorithm.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = this.algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("numSubsamples");
        parameters.add("percentSubsampleSize");
        parameters.add("percentStability");

        return parameters;
    }
}
