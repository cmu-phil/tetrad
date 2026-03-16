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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the results of multiple benchmark trials and provides simple averages by algorithm.
 *
 * @author josephramsey
 */
public final class MimicBenchmarkResult {

    /**
     * The individual trial results.
     */
    private final List<MimicTrialResult> trials;

    /**
     * Constructs a new benchmark result.
     *
     * @param trials the trial results
     */
    public MimicBenchmarkResult(List<MimicTrialResult> trials) {
        this.trials = new ArrayList<>(trials);
    }

    /**
     * Returns the individual trial results.
     *
     * @return the trial results
     */
    public List<MimicTrialResult> getTrials() {
        return new ArrayList<>(this.trials);
    }

    /**
     * Returns average evaluations by algorithm name across all trials.
     *
     * @return average evaluations by algorithm
     */
    public Map<String, MimicEvaluation> getAverageEvaluations() {
        Map<String, Integer> trueCounts = new LinkedHashMap<>();
        Map<String, Integer> estCounts = new LinkedHashMap<>();
        Map<String, Integer> matchedCounts = new LinkedHashMap<>();
        Map<String, Double> latentSums = new LinkedHashMap<>();
        Map<String, Double> inputSums = new LinkedHashMap<>();
        Map<String, Double> outputSums = new LinkedHashMap<>();
        Map<String, Integer> trialCounts = new LinkedHashMap<>();

        for (MimicTrialResult trial : this.trials) {
            for (Map.Entry<String, MimicEvaluation> entry : trial.getEvaluations().entrySet()) {
                String name = entry.getKey();
                MimicEvaluation eval = entry.getValue();

                trueCounts.put(name, trueCounts.getOrDefault(name, 0) + eval.getTrueLatentCount());
                estCounts.put(name, estCounts.getOrDefault(name, 0) + eval.getEstimatedLatentCount());
                matchedCounts.put(name, matchedCounts.getOrDefault(name, 0) + eval.getMatchedLatentCount());
                latentSums.put(name, latentSums.getOrDefault(name, 0.0) + eval.getAverageLatentSimilarity());
                inputSums.put(name, inputSums.getOrDefault(name, 0.0) + eval.getAverageInputSimilarity());
                outputSums.put(name, outputSums.getOrDefault(name, 0.0) + eval.getAverageOutputSimilarity());
                trialCounts.put(name, trialCounts.getOrDefault(name, 0) + 1);
            }
        }

        Map<String, MimicEvaluation> averages = new LinkedHashMap<>();

        for (String name : trialCounts.keySet()) {
            int n = trialCounts.get(name);

            averages.put(name, new MimicEvaluation(
                    trueCounts.get(name) / n,
                    estCounts.get(name) / n,
                    matchedCounts.get(name) / n,
                    latentSums.get(name) / n,
                    inputSums.get(name) / n,
                    outputSums.get(name) / n
            ));
        }

        return averages;
    }
}