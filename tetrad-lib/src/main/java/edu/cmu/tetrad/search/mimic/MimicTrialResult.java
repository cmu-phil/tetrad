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
     * Constructs a new trial result.
     *
     * @param trueModel the true model
     * @param measuredData the measured data
     * @param knowledge the tier knowledge
     * @param estimatedGraphs the estimated graphs
     * @param evaluations the evaluations
     */
    public MimicTrialResult(MimicModel trueModel,
                            DataSet measuredData,
                            Knowledge knowledge,
                            Map<String, Graph> estimatedGraphs,
                            Map<String, MimicEvaluation> evaluations) {
        this.trueModel = trueModel;
        this.measuredData = measuredData;
        this.knowledge = new Knowledge(knowledge);
        this.estimatedGraphs = new LinkedHashMap<>();
        this.evaluations = new LinkedHashMap<>(evaluations);

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
}