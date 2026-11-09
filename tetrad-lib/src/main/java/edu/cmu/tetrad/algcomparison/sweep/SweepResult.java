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

package edu.cmu.tetrad.algcomparison.sweep;

import edu.cmu.tetrad.graph.Graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The immutable result of evaluating one parameter setting in a {@link ParameterSweep}: the setting itself, the
 * point-estimate graph searched on the full data, a probability-annotated graph aggregating the resample searches
 * (in the same display convention as bootstrap results elsewhere in Tetrad), the StARS-style adjacency instability
 * over the resamples, Markov-check statistics for the point graph (if a Markov-check test was configured), and
 * timing.
 * <p>
 * This object records evidence about a setting; it does not judge settings. Selection rules live on
 * {@link SweepReport} and are explicit, overridable decisions.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SweepResult {

    /**
     * The parameter values for this setting, in insertion order; unmodifiable.
     */
    private final Map<String, Object> setting;

    /**
     * The point-estimate graph from searching the full dataset at this setting.
     */
    private final Graph pointGraph;

    /**
     * The probability-annotated graph aggregating the resample searches, as produced by
     * {@link edu.cmu.tetrad.util.GraphSampling#createGraphWithHighProbabilityEdges(java.util.List)}, or null if no
     * resamples were run.
     */
    private final Graph edgeProbabilityGraph;

    /**
     * The StARS-style adjacency instability over the resamples: the mean over unordered variable pairs of
     * 2 * theta * (1 - theta), where theta is the fraction of resample graphs in which the pair is adjacent. In
     * [0, 0.5]; NaN if no resamples were run.
     */
    private final double adjacencyInstability;

    /**
     * The Markov-check statistics for the point graph, or null if no Markov-check test was configured.
     */
    private final MarkovStats markovStats;

    /**
     * The number of resample searches aggregated.
     */
    private final int numResamples;

    /**
     * Wall time in milliseconds for this setting (point search, resample searches, and diagnostics).
     */
    private final long elapsedMillis;

    /**
     * Constructs a result. Intended to be called by {@link ParameterSweep}.
     *
     * @param setting              the parameter values for this setting; copied.
     * @param pointGraph           the point-estimate graph.
     * @param edgeProbabilityGraph the probability-annotated resample aggregate, or null.
     * @param adjacencyInstability the adjacency instability, or NaN.
     * @param markovStats          the Markov-check statistics, or null.
     * @param numResamples         the number of resample searches aggregated.
     * @param elapsedMillis        wall time in milliseconds.
     */
    public SweepResult(Map<String, Object> setting, Graph pointGraph, Graph edgeProbabilityGraph,
                       double adjacencyInstability, MarkovStats markovStats, int numResamples,
                       long elapsedMillis) {
        if (setting == null) throw new NullPointerException("setting");
        if (pointGraph == null) throw new NullPointerException("pointGraph");

        this.setting = Collections.unmodifiableMap(new LinkedHashMap<>(setting));
        this.pointGraph = pointGraph;
        this.edgeProbabilityGraph = edgeProbabilityGraph;
        this.adjacencyInstability = adjacencyInstability;
        this.markovStats = markovStats;
        this.numResamples = numResamples;
        this.elapsedMillis = elapsedMillis;
    }

    /**
     * Returns the parameter values for this setting, unmodifiable, in insertion order.
     *
     * @return This map.
     */
    public Map<String, Object> getSetting() {
        return this.setting;
    }

    /**
     * Returns the point-estimate graph from searching the full dataset at this setting.
     *
     * @return This graph.
     */
    public Graph getPointGraph() {
        return this.pointGraph;
    }

    /**
     * Returns the probability-annotated graph aggregating the resample searches, or null if no resamples were run.
     * Edge probabilities display identically to bootstrap results elsewhere in Tetrad.
     *
     * @return This graph or null.
     */
    public Graph getEdgeProbabilityGraph() {
        return this.edgeProbabilityGraph;
    }

    /**
     * Returns the StARS-style adjacency instability over the resamples, in [0, 0.5], or NaN if no resamples were
     * run.
     *
     * @return This instability.
     */
    public double getAdjacencyInstability() {
        return this.adjacencyInstability;
    }

    /**
     * Returns the Markov-check statistics for the point graph, or null if no Markov-check test was configured.
     *
     * @return These statistics or null.
     */
    public MarkovStats getMarkovStats() {
        return this.markovStats;
    }

    /**
     * Returns the number of resample searches aggregated.
     *
     * @return This number.
     */
    public int getNumResamples() {
        return this.numResamples;
    }

    /**
     * Returns the wall time in milliseconds for this setting.
     *
     * @return This time.
     */
    public long getElapsedMillis() {
        return this.elapsedMillis;
    }

    /**
     * Returns a one-line rendering of this result.
     *
     * @return This rendering.
     */
    @Override
    public String toString() {
        return "SweepResult " + this.setting + ": edges = " + this.pointGraph.getNumEdges()
                + ", instability = " + this.adjacencyInstability
                + (this.markovStats == null ? "" : ", adInd = " + this.markovStats.adInd());
    }

    /**
     * Markov-check statistics for a point graph, mirroring the statistics returned by
     * {@link edu.cmu.tetrad.search.MarkovCheck}: for the implied independencies ("Ind") and implied dependencies
     * ("Dep") respectively, the Anderson-Darling p-value for uniformity of the local p-values, the
     * Kolmogorov-Smirnov p-value, the binomial p-value, the fraction of facts judged dependent, and the number of
     * facts tested. For a well-specified model, adInd should not be small (the independence p-values should look
     * uniform) and fracDepDep should be large (implied dependencies should be detected).
     *
     * @param adInd       Anderson-Darling p for the implied independencies.
     * @param adDep       Anderson-Darling p for the implied dependencies.
     * @param ksInd       Kolmogorov-Smirnov p for the implied independencies.
     * @param ksDep       Kolmogorov-Smirnov p for the implied dependencies.
     * @param binomialInd binomial p for the implied independencies.
     * @param binomialDep binomial p for the implied dependencies.
     * @param fracDepInd  fraction of implied independencies judged dependent.
     * @param fracDepDep  fraction of implied dependencies judged dependent.
     * @param numTestsInd number of implied-independence facts tested.
     * @param numTestsDep number of implied-dependence facts tested.
     */
    public record MarkovStats(double adInd, double adDep, double ksInd, double ksDep, double binomialInd,
                              double binomialDep, double fracDepInd, double fracDepDep, int numTestsInd,
                              int numTestsDep) {
    }
}
