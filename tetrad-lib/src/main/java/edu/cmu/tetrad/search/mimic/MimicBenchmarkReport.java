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

import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces a readable text report for a MIMIC benchmark run.
 * <p>
 * The report summarizes the performance of each algorithm across trials using the
 * stored {@link MimicEvaluation} objects and also summarizes latent-latent edge
 * adequacy using the stored {@link LatentLatentEvaluator.Report} objects.
 *
 * @author josephramsey
 */
public final class MimicBenchmarkReport {

    /**
     * Number formatter for floating-point values.
     */
    private final DecimalFormat nf = new DecimalFormat("0.000");

    /**
     * Whether to append the full latent-latent report for each trial and algorithm.
     */
    private final boolean includePerTrialLatentDetails;

    /**
     * Constructs a report generator without per-trial latent details.
     */
    public MimicBenchmarkReport() {
        this(false);
    }

    /**
     * Constructs a report generator.
     *
     * @param includePerTrialLatentDetails whether to include full per-trial latent details
     */
    public MimicBenchmarkReport(boolean includePerTrialLatentDetails) {
        this.includePerTrialLatentDetails = includePerTrialLatentDetails;
    }

    /**
     * Creates a readable report for the supplied benchmark result.
     *
     * @param result the benchmark result
     * @return the report text
     */
    public String createReport(MimicBenchmarkResult result) {
        if (result == null) {
            throw new NullPointerException("Benchmark result must not be null.");
        }

        List<MimicTrialResult> trials = result.getTrials();

        StringBuilder sb = new StringBuilder();

        sb.append("\nMIMIC Benchmark Report\n");
        sb.append("=====================\n");
        sb.append("Trials: ").append(trials.size()).append('\n');

        if (trials.isEmpty()) {
            sb.append("\n(no trials)\n");
            return sb.toString();
        }

        Set<String> algorithmNames = collectAlgorithmNames(trials);

        for (String algorithm : algorithmNames) {
            appendAlgorithmSection(sb, algorithm, trials);
        }

        if (includePerTrialLatentDetails) {
            appendPerTrialLatentSections(sb, trials, algorithmNames);
        }

        return sb.toString();
    }

    /**
     * Appends the summary for one algorithm.
     *
     * @param sb the string builder
     * @param algorithm the algorithm name
     * @param trials the trial results
     */
    private void appendAlgorithmSection(StringBuilder sb,
                                        String algorithm,
                                        List<MimicTrialResult> trials) {
        sb.append("\n\nAlgorithm: ").append(algorithm).append('\n');
        sb.append(repeat('-', 11 + algorithm.length())).append('\n');

        int evalCount = 0;

        double sumTrueLatentCount = 0.0;
        double sumEstimatedLatentCount = 0.0;
        double sumMatchedLatentCount = 0.0;
        double sumLatentCountError = 0.0;
        double sumAverageLatentSimilarity = 0.0;
        double sumAverageInputSimilarity = 0.0;
        double sumAverageOutputSimilarity = 0.0;

        int pooledLatentTp = 0;
        int pooledLatentFp = 0;
        int pooledLatentFn = 0;

        int latentReportCount = 0;

        for (MimicTrialResult trial : trials) {
            MimicEvaluation evaluation = trial.getEvaluation(algorithm);

            if (evaluation != null) {
                evalCount++;
                sumTrueLatentCount += evaluation.getTrueLatentCount();
                sumEstimatedLatentCount += evaluation.getEstimatedLatentCount();
                sumMatchedLatentCount += evaluation.getMatchedLatentCount();
                sumLatentCountError += evaluation.getLatentCountError();
                sumAverageLatentSimilarity += evaluation.getAverageLatentSimilarity();
                sumAverageInputSimilarity += evaluation.getAverageInputSimilarity();
                sumAverageOutputSimilarity += evaluation.getAverageOutputSimilarity();
            }

            LatentLatentEvaluator.Report latentReport = trial.getLatentLatentReport(algorithm);

            if (latentReport != null) {
                latentReportCount++;
                pooledLatentTp += latentReport.getTruePositives();
                pooledLatentFp += latentReport.getFalsePositives();
                pooledLatentFn += latentReport.getFalseNegatives();
            }
        }

        sb.append("\nLatent matching summary\n");
        sb.append("-----------------------\n");

        if (evalCount == 0) {
            sb.append("(no evaluations)\n");
        } else {
            sb.append("Average true latent count:      ")
                    .append(nf.format(sumTrueLatentCount / evalCount)).append('\n');
            sb.append("Average estimated latent count: ")
                    .append(nf.format(sumEstimatedLatentCount / evalCount)).append('\n');
            sb.append("Average matched latent count:   ")
                    .append(nf.format(sumMatchedLatentCount / evalCount)).append('\n');
            sb.append("Average latent count error:     ")
                    .append(nf.format(sumLatentCountError / evalCount)).append('\n');
            sb.append("Average latent similarity:      ")
                    .append(nf.format(sumAverageLatentSimilarity / evalCount)).append('\n');
            sb.append("Average input similarity:       ")
                    .append(nf.format(sumAverageInputSimilarity / evalCount)).append('\n');
            sb.append("Average output similarity:      ")
                    .append(nf.format(sumAverageOutputSimilarity / evalCount)).append('\n');
        }

        sb.append("\nLatent-latent edge adequacy\n");
        sb.append("---------------------------\n");

        if (latentReportCount == 0) {
            sb.append("(no latent-latent reports)\n");
        } else {
            double pooledPrecision = pooledLatentTp + pooledLatentFp == 0
                    ? Double.NaN
                    : (double) pooledLatentTp / (pooledLatentTp + pooledLatentFp);

            double pooledRecall = pooledLatentTp + pooledLatentFn == 0
                    ? Double.NaN
                    : (double) pooledLatentTp / (pooledLatentTp + pooledLatentFn);

            sb.append("Pooled TP:                      ").append(pooledLatentTp).append('\n');
            sb.append("Pooled FP:                      ").append(pooledLatentFp).append('\n');
            sb.append("Pooled FN:                      ").append(pooledLatentFn).append('\n');
            sb.append("Pooled precision:               ").append(formatDouble(pooledPrecision)).append('\n');
            sb.append("Pooled recall:                  ").append(formatDouble(pooledRecall)).append('\n');
        }
    }

    /**
     * Appends per-trial latent-latent adequacy details for each algorithm.
     *
     * @param sb the string builder
     * @param trials the trial results
     * @param algorithmNames the algorithm names
     */
    private void appendPerTrialLatentSections(StringBuilder sb,
                                              List<MimicTrialResult> trials,
                                              Set<String> algorithmNames) {
        sb.append("\n\nPer-trial latent-latent details\n");
        sb.append("-------------------------------\n");

        for (int i = 0; i < trials.size(); i++) {
            MimicTrialResult trial = trials.get(i);

            sb.append("\nTrial ").append(i + 1).append('\n');
            sb.append("~~~~~~~\n");

            for (String algorithm : algorithmNames) {
                LatentLatentEvaluator.Report latentReport = trial.getLatentLatentReport(algorithm);

                sb.append("\n").append(algorithm).append('\n');
                sb.append(repeat('~', algorithm.length())).append('\n');

                if (latentReport == null) {
                    sb.append("(no latent-latent report)\n");
                } else {
                    sb.append(latentReport.toDisplayString());
                    if (!latentReport.toDisplayString().endsWith("\n")) {
                        sb.append('\n');
                    }
                }
            }
        }
    }

    /**
     * Collects the algorithm names appearing in the trial results.
     *
     * @param trials the trial results
     * @return the algorithm names
     */
    private Set<String> collectAlgorithmNames(List<MimicTrialResult> trials) {
        Set<String> names = new LinkedHashSet<>();

        for (MimicTrialResult trial : trials) {
            Map<String, MimicEvaluation> evaluations = trial.getEvaluations();
            Map<String, LatentLatentEvaluator.Report> latentReports = trial.getLatentLatentReports();

            names.addAll(evaluations.keySet());
            names.addAll(latentReports.keySet());
        }

        return names;
    }

    /**
     * Formats a floating-point value, handling NaN explicitly.
     *
     * @param value the value
     * @return the formatted value
     */
    private String formatDouble(double value) {
        return Double.isNaN(value) ? "NaN" : nf.format(value);
    }

    /**
     * Returns a string consisting of the given character repeated the given number of times.
     *
     * @param c the character
     * @param n the number of repetitions
     * @return the repeated string
     */
    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < n; i++) {
            sb.append(c);
        }

        return sb.toString();
    }
}