///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores the complete result of one benchmark trial.
 * <p>
 * For each trial, this includes the true MIMIC model, the measured data simulated
 * from that model, the tier knowledge supplied to the search procedures, the
 * estimated graphs produced by each algorithm, the corresponding overall
 * evaluations, and latent-latent adequacy reports comparing the estimated
 * latent structure to the true latent structure.
 *
 * @author josephramsey
 */
public final class MimicTrialResult {

    /**
     * The true MIMIC model used to generate the trial.
     */
    private final MimicModel trueModel;

    /**
     * The measured data simulated from the true model.
     */
    private final DataSet measuredData;

    /**
     * The tier knowledge used by the searches.
     */
    private final Knowledge knowledge;

    /**
     * Estimated graphs by algorithm name.
     */
    private final Map<String, Graph> estimatedGraphs;

    /**
     * Evaluations by algorithm name.
     */
    private final Map<String, MimicEvaluation> evaluations;

    /**
     * Latent-latent adequacy reports by algorithm name.
     */
    private final Map<String, LatentLatentEvaluator.Report> latentLatentReports;

    /**
     * Constructs a new trial result.
     *
     * @param trueModel the true model
     * @param measuredData the measured data
     * @param knowledge the tier knowledge
     * @param estimatedGraphs the estimated graphs
     * @param evaluations the evaluations
     * @param latentLatentReports the latent-latent adequacy reports
     */
    public MimicTrialResult(MimicModel trueModel,
                            DataSet measuredData,
                            Knowledge knowledge,
                            Map<String, Graph> estimatedGraphs,
                            Map<String, MimicEvaluation> evaluations,
                            Map<String, LatentLatentEvaluator.Report> latentLatentReports) {
        if (trueModel == null) {
            throw new NullPointerException("True model must not be null.");
        }

        if (measuredData == null) {
            throw new NullPointerException("Measured data must not be null.");
        }

        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        if (estimatedGraphs == null) {
            throw new NullPointerException("Estimated graphs map must not be null.");
        }

        if (evaluations == null) {
            throw new NullPointerException("Evaluations map must not be null.");
        }

        if (latentLatentReports == null) {
            throw new NullPointerException("Latent-latent reports map must not be null.");
        }

        this.trueModel = trueModel;
        this.measuredData = measuredData;
        this.knowledge = new Knowledge(knowledge);
        this.estimatedGraphs = new LinkedHashMap<>();
        this.evaluations = new LinkedHashMap<>(evaluations);
        this.latentLatentReports = new LinkedHashMap<>(latentLatentReports);

        for (Map.Entry<String, Graph> entry : estimatedGraphs.entrySet()) {
            this.estimatedGraphs.put(entry.getKey(), new EdgeListGraph(entry.getValue()));
        }
    }

    /**
     * Returns the true model.
     *
     * @return the true model
     */
    public MimicModel getTrueModel() {
        return this.trueModel;
    }

    /**
     * Returns the measured data.
     *
     * @return the measured data
     */
    public DataSet getMeasuredData() {
        return this.measuredData;
    }

    /**
     * Returns the tier knowledge.
     *
     * @return the tier knowledge
     */
    public Knowledge getKnowledge() {
        return new Knowledge(this.knowledge);
    }

    /**
     * Returns the estimated graphs by algorithm name.
     *
     * @return the estimated graphs
     */
    public Map<String, Graph> getEstimatedGraphs() {
        Map<String, Graph> copy = new LinkedHashMap<>();

        for (Map.Entry<String, Graph> entry : this.estimatedGraphs.entrySet()) {
            copy.put(entry.getKey(), new EdgeListGraph(entry.getValue()));
        }

        return copy;
    }

    /**
     * Returns the evaluations by algorithm name.
     *
     * @return the evaluations
     */
    public Map<String, MimicEvaluation> getEvaluations() {
        return new LinkedHashMap<>(this.evaluations);
    }

    /**
     * Returns the latent-latent adequacy reports by algorithm name.
     *
     * @return the latent-latent adequacy reports
     */
    public Map<String, LatentLatentEvaluator.Report> getLatentLatentReports() {
        return new LinkedHashMap<>(this.latentLatentReports);
    }

    /**
     * Returns the estimated graph for the given algorithm name, or null if none exists.
     *
     * @param algorithm the algorithm name
     * @return the estimated graph, or null
     */
    public Graph getEstimatedGraph(String algorithm) {
        Graph graph = this.estimatedGraphs.get(algorithm);
        return graph == null ? null : new EdgeListGraph(graph);
    }

    /**
     * Returns the evaluation for the given algorithm name, or null if none exists.
     *
     * @param algorithm the algorithm name
     * @return the evaluation, or null
     */
    public MimicEvaluation getEvaluation(String algorithm) {
        return this.evaluations.get(algorithm);
    }

    /**
     * Returns the latent-latent adequacy report for the given algorithm name,
     * or null if none exists.
     *
     * @param algorithm the algorithm name
     * @return the latent-latent adequacy report, or null
     */
    public LatentLatentEvaluator.Report getLatentLatentReport(String algorithm) {
        return this.latentLatentReports.get(algorithm);
    }
}