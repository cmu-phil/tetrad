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

import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.Parameters;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces a tab-delimited benchmark report for a MIMIC benchmark run.
 *
 * <p>The report contains:
 * <ol>
 *     <li>A short abbreviation dictionary.</li>
 *     <li>A tab-delimited table of hyperparameter settings.</li>
 *     <li>A tab-delimited summary table, one row per algorithm.</li>
 * </ol>
 *
 * <p>Integer-valued statistics are formatted as integers. Floating-point statistics are
 * formatted using {@code DecimalFormat("0.0000")}.</p>
 *
 * @author josephramsey
 */
public final class MimicBenchmarkReportTdf {

    /**
     * Default constructor for the MimicBenchmarkReportTdf class.
     * Initializes a new instance of the class with no specific configuration or parameters.
     */
    public MimicBenchmarkReportTdf() {}

    /**
     * Formatter for floating-point values.
     */
    private final DecimalFormat nf = new DecimalFormat("0.0000");

    /**
     * Creates a tab-delimited report for the supplied benchmark result and parameters.
     *
     * @param result the benchmark result
     * @param parameters the benchmark parameters
     * @return the report text
     */
    public String createReport(MimicBenchmarkResult result, Parameters parameters) {
        if (result == null) {
            throw new NullPointerException("Benchmark result must not be null.");
        }

        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        List<MimicTrialResult> trials = result.getTrials();
        StringBuilder sb = new StringBuilder();

        appendAbbreviationDictionary(sb);
        sb.append('\n');

        appendParameterTable(sb, parameters);
        sb.append('\n');

        appendSummaryTable(sb, trials);

        return sb.toString();
    }

    /**
     * Appends the abbreviation dictionary.
     *
     * @param sb the string builder
     */
    private void appendAbbreviationDictionary(StringBuilder sb) {
        sb.append("ABBREVIATIONS\n");
        sb.append("abbr\tdefinition\n");
        sb.append("alg\talgorithm name\n");
        sb.append("trials\tnumber of benchmark trials\n");
        sb.append("tlc_m\tmean true latent count\n");
        sb.append("tlc_sd\tstandard deviation of true latent count\n");
        sb.append("elc_m\tmean estimated latent count\n");
        sb.append("elc_sd\tstandard deviation of estimated latent count\n");
        sb.append("mlc_m\tmean matched latent count\n");
        sb.append("mlc_sd\tstandard deviation of matched latent count\n");
        sb.append("lce_m\tmean latent count error\n");
        sb.append("lce_sd\tstandard deviation of latent count error\n");
        sb.append("ls_m\tmean latent similarity\n");
        sb.append("ls_sd\tstandard deviation of latent similarity\n");
        sb.append("is_m\tmean input similarity\n");
        sb.append("is_sd\tstandard deviation of input similarity\n");
        sb.append("os_m\tmean output similarity\n");
        sb.append("os_sd\tstandard deviation of output similarity\n");
        sb.append("tp\tpooled latent-latent true positives\n");
        sb.append("fp\tpooled latent-latent false positives\n");
        sb.append("fn\tpooled latent-latent false negatives\n");
        sb.append("prec\tpooled latent-latent precision\n");
        sb.append("rec\tpooled latent-latent recall\n");
    }

    /**
     * Appends a tab-delimited parameter table.
     *
     * @param sb the string builder
     * @param parameters the parameters
     */
    private void appendParameterTable(StringBuilder sb, Parameters parameters) {
        sb.append("SETTINGS\n");
        sb.append("name\tvalue\n");

        List<String> names = new ArrayList<>(parameters.getParametersNames());
        names.sort(NaturalSort.naturalComparator());;

        for (String name : names) {
            Object value = parameters.get(name);
            sb.append(name).append('\t').append(value).append('\n');
        }
    }

    /**
     * Appends the summary table.
     *
     * @param sb the string builder
     * @param trials the trial results
     */
    private void appendSummaryTable(StringBuilder sb, List<MimicTrialResult> trials) {
        sb.append("SUMMARY\n");

        sb.append("alg\ttrials\t");
        sb.append("tlc_m\ttlc_sd\t");
        sb.append("elc_m\telc_sd\t");
        sb.append("mlc_m\tmlc_sd\t");
        sb.append("lce_m\tlce_sd\t");
        sb.append("ls_m\tls_sd\t");
        sb.append("is_m\tis_sd\t");
        sb.append("os_m\tos_sd\t");
        sb.append("tp\tfp\tfn\tprec\trec\n");

        if (trials.isEmpty()) {
            return;
        }

        Set<String> algorithmNames = collectAlgorithmNames(trials);

        for (String algorithm : algorithmNames) {
            appendAlgorithmRow(sb, algorithm, trials);
        }
    }

    /**
     * Appends one algorithm row.
     *
     * @param sb the string builder
     * @param algorithm the algorithm
     * @param trials the trials
     */
    private void appendAlgorithmRow(StringBuilder sb,
                                    String algorithm,
                                    List<MimicTrialResult> trials) {
        int evalCount = 0;

        double sumTrueLatentCount = 0.0;
        double sumTrueLatentCountSq = 0.0;

        double sumEstimatedLatentCount = 0.0;
        double sumEstimatedLatentCountSq = 0.0;

        double sumMatchedLatentCount = 0.0;
        double sumMatchedLatentCountSq = 0.0;

        double sumLatentCountError = 0.0;
        double sumLatentCountErrorSq = 0.0;

        double sumAverageLatentSimilarity = 0.0;
        double sumAverageLatentSimilaritySq = 0.0;

        double sumAverageInputSimilarity = 0.0;
        double sumAverageInputSimilaritySq = 0.0;

        double sumAverageOutputSimilarity = 0.0;
        double sumAverageOutputSimilaritySq = 0.0;

        int pooledLatentTp = 0;
        int pooledLatentFp = 0;
        int pooledLatentFn = 0;

        for (MimicTrialResult trial : trials) {
            MimicEvaluation evaluation = trial.getEvaluation(algorithm);

            if (evaluation != null) {
                evalCount++;

                double v;

                v = evaluation.getTrueLatentCount();
                sumTrueLatentCount += v;
                sumTrueLatentCountSq += v * v;

                v = evaluation.getEstimatedLatentCount();
                sumEstimatedLatentCount += v;
                sumEstimatedLatentCountSq += v * v;

                v = evaluation.getMatchedLatentCount();
                sumMatchedLatentCount += v;
                sumMatchedLatentCountSq += v * v;

                v = evaluation.getLatentCountError();
                sumLatentCountError += v;
                sumLatentCountErrorSq += v * v;

                v = evaluation.getAverageLatentSimilarity();
                sumAverageLatentSimilarity += v;
                sumAverageLatentSimilaritySq += v * v;

                v = evaluation.getAverageInputSimilarity();
                sumAverageInputSimilarity += v;
                sumAverageInputSimilaritySq += v * v;

                v = evaluation.getAverageOutputSimilarity();
                sumAverageOutputSimilarity += v;
                sumAverageOutputSimilaritySq += v * v;
            }

            LatentLatentEvaluator.Report latentReport = trial.getLatentLatentReport(algorithm);

            if (latentReport != null) {
                pooledLatentTp += latentReport.getTruePositives();
                pooledLatentFp += latentReport.getFalsePositives();
                pooledLatentFn += latentReport.getFalseNegatives();
            }
        }

        double pooledPrecision = pooledLatentTp + pooledLatentFp == 0
                ? Double.NaN
                : (double) pooledLatentTp / (pooledLatentTp + pooledLatentFp);

        double pooledRecall = pooledLatentTp + pooledLatentFn == 0
                ? Double.NaN
                : (double) pooledLatentTp / (pooledLatentTp + pooledLatentFn);

        sb.append(algorithm).append('\t');
        sb.append(trials.size()).append('\t');

        appendMeanSdPair(sb, sumTrueLatentCount, sumTrueLatentCountSq, evalCount);
        appendMeanSdPair(sb, sumEstimatedLatentCount, sumEstimatedLatentCountSq, evalCount);
        appendMeanSdPair(sb, sumMatchedLatentCount, sumMatchedLatentCountSq, evalCount);
        appendMeanSdPair(sb, sumLatentCountError, sumLatentCountErrorSq, evalCount);
        appendMeanSdPair(sb, sumAverageLatentSimilarity, sumAverageLatentSimilaritySq, evalCount);
        appendMeanSdPair(sb, sumAverageInputSimilarity, sumAverageInputSimilaritySq, evalCount);
        appendMeanSdPair(sb, sumAverageOutputSimilarity, sumAverageOutputSimilaritySq, evalCount);

        sb.append(pooledLatentTp).append('\t');
        sb.append(pooledLatentFp).append('\t');
        sb.append(pooledLatentFn).append('\t');
        sb.append(formatDouble(pooledPrecision)).append('\t');
        sb.append(formatDouble(pooledRecall)).append('\n');
    }

    /**
     * Appends a mean/standard-deviation pair as two tab-delimited fields.
     *
     * @param sb the string builder
     * @param sum the sum
     * @param sumSq the sum of squares
     * @param n the count
     */
    private void appendMeanSdPair(StringBuilder sb, double sum, double sumSq, int n) {
        if (n == 0) {
            sb.append("NaN\tNaN\t");
            return;
        }

        double mean = sum / n;
        double variance = sumSq / n - mean * mean;

        if (variance < 0.0) {
            variance = 0.0;
        }

        double sd = Math.sqrt(variance);

        sb.append(formatDouble(mean)).append('\t');
        sb.append(formatDouble(sd)).append('\t');
    }

    /**
     * Collects the algorithm names appearing in the trial results.
     *
     * @param trials the trials
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
     * Formats a double using four decimal places, with explicit handling of NaN.
     *
     * @param value the value
     * @return the formatted value
     */
    private String formatDouble(double value) {
        return Double.isNaN(value) ? "NaN" : nf.format(value);
    }
}